# Guia de implantação

Como colocar o Books Manager (a API e o front-end) em um servidor, atrás de HTTPS, e como atualizá-lo, voltar atrás e fazer o backup do banco. É a implantação mínima da v1.0 (RNF12, RNF18, RNF26): **um servidor Linux, o Docker e quatro contêineres**. O front-end é outro repositório (`booksmanager-web`), mas roda nesta pilha, **na mesma origem da API**: ele chama `/api/...` com endereços relativos, e o cookie do refresh token (`SameSite=Strict`) só vale entre origens do mesmo site.

## 1. O que sobe

```
                                      ┌──► front-end (porta 8080, só na rede interna)    "/" e as telas do app
internet ──► Nginx (portas 80 e 443) ─┤
                                      └──► API (porta 8080, só na rede interna) ──► PostgreSQL (só na rede interna)
                                           "/api/", "/actuator/", "/v3/" e "/swagger-ui"
```

| Serviço | O que faz |
|---|---|
| `nginx` | Termina o TLS (HTTPS), redireciona a porta 80 para a 443, manda os cabeçalhos de segurança (HSTS e outros) e `X-Forwarded-*`, e **divide as rotas**: o que a API responde (`/api/`, `/actuator/`, `/v3/` e `/swagger-ui`) vai para a API, e todo o resto para o front-end. É o **único** que publica portas. |
| `web` | O front-end: os arquivos estáticos do app, servidos por um Nginx sem privilégios (a imagem do repositório `booksmanager-web`, publicada no registro). Não é alcançável de fora. Não repete os cabeçalhos de segurança, que o `nginx` da frente já manda. |
| `app` | A API (a imagem publicada no registro, ou construída pelo `Dockerfile`; perfil `prod`). Não é alcançável de fora. |
| `postgres` | O banco (PostgreSQL 17), com os dados em um volume do Docker. Não é alcançável de fora. A busca sem acentos usa a extensão `unaccent`, criada pela migração V15; a imagem oficial `postgres:17` já a traz. Se usar um banco gerenciado, confira que ele permite `create extension unaccent` (é uma extensão "confiável" desde o PostgreSQL 13). |
| `certbot` | Só para emitir e renovar o certificado do Let's Encrypt. Não sobe com o `up` (veja a seção 4). |

Os arquivos: `compose.prod.yaml` (a pilha), `deploy/nginx/nginx.conf.template` (o Nginx), `deploy/nginx/test-routing.sh` (o teste das rotas e dos limites do Nginx, que roda no CI) e `.env.prod-sample` (o modelo das variáveis). Nenhum deles tem segredo.

> **Atenção.** A pilha de produção tem o próprio nome de projeto (`booksmanager-prod`), de propósito: o `compose.yaml` de desenvolvimento é o projeto `booksmanager`, e com o mesmo nome os dois dividiriam contêineres e o volume do banco. **Nunca rode `docker compose down -v` em produção**: o `-v` apaga o volume com os dados.

## 2. Antes de começar

- Um servidor Linux com **Docker** e o **plugin Docker Compose** (`docker compose version` deve funcionar). Memória: 1 GB já basta para uma biblioteca pequena; a JVM usa até 75% da memória do contêiner.
- Um **domínio** (por exemplo `biblioteca.escola.org.br`) apontando para o IP do servidor (registro `A`).
- As portas **80 e 443** liberadas no firewall do servidor. As outras (8080, 5432) **não** devem ser abertas: a API só pode ser alcançada pelo Nginx, porque a aplicação confia nos cabeçalhos `X-Forwarded-*` de um proxy em endereço interno (`application-prod.properties`).
- O repositório clonado no servidor (por exemplo em `/opt/booksmanager`).

## 3. Primeira implantação

1. **Crie o arquivo de variáveis** a partir do modelo e proteja-o. Ele guarda os segredos e **nunca** vai para o Git (o `.gitignore` já o ignora):

   ```
   cd /opt/booksmanager
   cp .env.prod-sample .env.prod
   chmod 600 .env.prod
   ```

2. **Preencha o `.env.prod`**. O que é obrigatório:

   | Variável | Valor |
   |---|---|
   | `DOMAIN` | O domínio, sem `http://` (o mesmo do certificado). |
   | `POSTGRES_PASSWORD` | Uma senha longa e aleatória: `openssl rand -base64 24`. |
   | `APP_JWT_SECRET` | Segredo dos tokens, com ao menos 32 bytes: `openssl rand -base64 48`. A aplicação não sobe sem ele. |
   | `WEB_TAG` | A **versão do front-end** que roda (por exemplo `1.0.0`). Não tem valor padrão: a imagem é publicada com a versão e o hash do commit, nunca como `latest`, e a pilha não sobe sem esta variável. |
| `APP_CORS_ALLOWED_ORIGINS` | A origem pública, com `https` e sem barra no fim (o domínio acima). Como o front-end é servido pela própria pilha, o padrão, se ficar vazia, já é `https://<DOMAIN>`. Mantenha-a igual ao endereço público: a API responde **403 "Invalid CORS request"** a uma requisição de navegador cuja origem ela não conhece. |
   | `APP_ADMIN_LOGIN` e `APP_ADMIN_PASSWORD` | O primeiro administrador. É criado na subida **só se não houver nenhum usuário**; depois que ele existe, a senha pode sair do arquivo. |
   | `LETSENCRYPT_DIR` | Onde está o certificado (veja a seção 4). |

   `POSTGRES_DB` e `POSTGRES_USER` têm valores que servem como estão.

   **As imagens:** `APP_IMAGE` e `IMAGE_TAG` dizem qual imagem da API roda, e `WEB_IMAGE` e `WEB_TAG` qual imagem do front-end.

   **A imagem da API:** O modelo já aponta para o registro (`ghcr.io/riverfount/booksmanager`); troque `IMAGE_TAG` pela **versão publicada** que você quer (por exemplo `1.0.0`; veja a seção 6.1 para ver o que existe). Para construir a imagem no próprio servidor, em vez de puxá-la, deixe `APP_IMAGE` vazio, escolha qualquer `IMAGE_TAG` e suba com `--build` (passo 4).

   **A imagem do front-end:** o modelo aponta para o registro (`ghcr.io/riverfount/booksmanager-web`); ponha em `WEB_TAG` a **versão publicada** que você quer, a que combina com a da API (seção 6.2). Para construí-la no servidor, clone o repositório `booksmanager-web`, rode `docker build -t booksmanager-web:<tag> .` nele e use `WEB_IMAGE=booksmanager-web` com essa `<tag>`.

   **Os links dos e-mails do leitor** (convite e redefinição de senha) abrem telas do front-end, que agora está no endereço público: por padrão são montados a partir de `DOMAIN` (`https://<DOMAIN>/convite?token={token}` e `https://<DOMAIN>/redefinir-senha?token={token}`), e não é preciso configurar nada. Só defina `APP_READER_ACCESS_INVITATION_URL` e `APP_READER_ACCESS_PASSWORD_RESET_URL` se quiser apontar para outro lugar, e mantenha o `{token}`.

   **E-mail (opcional, RF28):** por padrão nada é enviado. Para ligar os avisos, preencha `APP_MAIL_ENABLED=true`, `APP_MAIL_HOST` (o SMTP do seu provedor) e `APP_MAIL_FROM` (o remetente); `APP_MAIL_PORT` (587), `APP_MAIL_STARTTLS` (`true`), `APP_MAIL_USERNAME` e `APP_MAIL_PASSWORD` (só se o servidor pedir). A porta 465 (TLS direto) não é suportada: use 587 com STARTTLS. Um servidor de e-mail fora do ar não derruba nada: o envio é feito depois do commit, com tempos curtos, e a falha só vai para o log (RNF19). O health check não olha o e-mail.

3. **Providencie o certificado** (seção 4). O Nginx **não sobe** sem ele.

4. **Suba a pilha.** Puxe a imagem do registro (ela é pública, não precisa de login) e suba:

   ```
   docker compose --env-file .env.prod -f compose.prod.yaml pull app web
   docker compose --env-file .env.prod -f compose.prod.yaml up -d
   ```

   O `pull` vem antes de propósito: se a versão de `IMAGE_TAG` (ou de `WEB_TAG`) não existe no registro (um erro de digitação, por exemplo), ele **falha com uma mensagem clara**. Sem ele, o `up` não encontra a imagem e, em silêncio, **a constrói no servidor** com esse nome, e você acredita estar rodando uma versão publicada que não é.

   Para **construir a imagem no servidor** de propósito (com `APP_IMAGE` vazio, o que leva alguns minutos), pule o `pull` e use `up -d --build`.

   Para não repetir as opções, vale um atalho no shell do servidor: `alias dcp='docker compose --env-file .env.prod -f compose.prod.yaml'`. Os exemplos abaixo escrevem o comando inteiro.

5. **Confira** (seção 5).

## 4. O certificado TLS

O Nginx lê o certificado em `/etc/letsencrypt/live/<DOMAIN>/fullchain.pem` e `privkey.pem`. Há dois jeitos de colocá-lo lá, e a variável `LETSENCRYPT_DIR` diz qual você usa.

### 4.1 Certificado que você já tem (montado)

Serve para um certificado comprado ou emitido por outra ferramenta.

1. Crie a pasta e copie os dois arquivos, com este nome e neste lugar:

   ```
   mkdir -p certs/live/biblioteca.escola.org.br
   cp fullchain.pem privkey.pem certs/live/biblioteca.escola.org.br/
   chmod 600 certs/live/biblioteca.escola.org.br/privkey.pem
   ```

2. No `.env.prod`, `LETSENCRYPT_DIR=./certs`. A pasta `certs/` é ignorada pelo Git.
3. **Para renovar:** troque os dois arquivos e reinicie o Nginx, que só lê o certificado ao iniciar:

   ```
   docker compose --env-file .env.prod -f compose.prod.yaml restart nginx
   ```

Para só **experimentar** a pilha num computador de testes, com `DOMAIN=localhost`, dá para gerar um certificado autoassinado (o navegador vai reclamar, e `curl` precisa de `-k`):

```
mkdir -p certs/live/localhost
openssl req -x509 -newkey rsa:2048 -nodes -days 30 -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost" \
  -keyout certs/live/localhost/privkey.pem -out certs/live/localhost/fullchain.pem
```

### 4.2 Let's Encrypt com o certbot

Gratuito, e o certificado vale 90 dias. O certbot guarda tudo em um volume do Docker (`LETSENCRYPT_DIR=letsencrypt`, o padrão).

> **Esta parte não foi testada contra o Let's Encrypt de verdade**, porque isso exige um domínio público e a porta 80 alcançável da internet. Foram testados a ligação do serviço `certbot` ao volume que o Nginx lê e o funcionamento do próprio `certbot`. Faça a primeira emissão com `--staging` (os limites do Let's Encrypt são baixos) e só depois a real.

**Emissão (uma vez).** Com o DNS apontando para o servidor, a porta 80 livre e o Nginx **parado** (na primeira implantação ele ainda nem subiu):

```
docker compose --env-file .env.prod -f compose.prod.yaml --profile certbot run --rm -p 80:80 certbot \
  certonly --standalone -d biblioteca.escola.org.br --email voce@escola.org.br --agree-tos --no-eff-email --staging
```

Deu certo? Repita sem o `--staging` (apague antes o certificado de teste: `... run --rm certbot delete --cert-name biblioteca.escola.org.br`) e suba a pilha com `up -d --build`.

**Renovação.** O certbot só renova o que vence em menos de 30 dias, então pode rodar todo mês. Como a emissão usou a porta 80, o Nginx precisa sair do caminho e voltar depois (leva alguns segundos fora do ar):

```
cd /opt/booksmanager
docker compose --env-file .env.prod -f compose.prod.yaml stop nginx
docker compose --env-file .env.prod -f compose.prod.yaml --profile certbot run --rm -p 80:80 certbot renew
docker compose --env-file .env.prod -f compose.prod.yaml start nginx
```

Para agendar no `cron` do servidor (`crontab -e`), todo dia 1 às 04:00:

```
0 4 1 * * cd /opt/booksmanager && docker compose --env-file .env.prod -f compose.prod.yaml stop nginx && docker compose --env-file .env.prod -f compose.prod.yaml --profile certbot run --rm -p 80:80 certbot renew; docker compose --env-file .env.prod -f compose.prod.yaml start nginx >> /var/log/booksmanager-certificado.log 2>&1
```

Para ensaiar a renovação sem trocar nada, use `renew --dry-run` no lugar de `renew`.

## 5. Conferir que está tudo certo

```
# os quatro serviços de pé; app, web e postgres "healthy"
docker compose --env-file .env.prod -f compose.prod.yaml ps

# a saúde, por HTTPS (200 e {"status":"UP"...}); o banco entra nessa conta
curl -fsS https://biblioteca.escola.org.br/actuator/health

# a porta 80 só redireciona (301 para https)
curl -sI http://biblioteca.escola.org.br/ | head -3

# o HSTS está lá, uma vez só
curl -sI https://biblioteca.escola.org.br/actuator/health | grep -i strict-transport

# o Swagger (a documentação da API)
# abra no navegador: https://biblioteca.escola.org.br/swagger-ui.html

# o front-end, na mesma origem: a raiz e um link direto devolvem o app (200, text/html)
curl -sI https://biblioteca.escola.org.br/ | head -1
curl -sI https://biblioteca.escola.org.br/livros | head -1
```

E, **num navegador**, abra `https://biblioteca.escola.org.br/`, entre com o administrador e **recarregue a página** numa tela interna (por exemplo `/usuarios`): a sessão deve continuar (o cookie do refresh token é enviado) e a tabela carregar. Um `curl` sem o cabeçalho `Origin` não prova isso: o navegador o manda, e a API recusa (403) se o endereço que ela enxerga não bater com o dele.

E, com o token de um login, confira que o `Location` das criações vem em `https` com o domínio público (e não `http://app:8080/...`): crie qualquer coisa (por exemplo uma categoria) e olhe o cabeçalho `Location` da resposta `201`.

Se algo não subir, `docker compose --env-file .env.prod -f compose.prod.yaml logs app` (ou `nginx`, `postgres`) mostra o motivo. Veja também a seção 9.

## 6. Atualização e volta atrás

As etiquetas das imagens (`IMAGE_TAG`, a da API, e `WEB_TAG`, a do front-end) são as versões que estão rodando, e voltar atrás é voltar a etiquetas anteriores, **sem reconstruir nada**: cada versão publicada continua no registro. **Faça um backup antes de cada atualização** (seção 7): as migrações do banco só andam para a frente.

**Atualizar** (a versão já está publicada, seção 6.1):

```
cd /opt/booksmanager

# 1. backup (seção 7)

# 2. os arquivos de implantação (o Compose e o Nginx) e as versões novas (veja a seção 6.2 para saber quais andam juntas)
git pull
sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=<versao-nova-da-api>/" .env.prod
sed -i "s/^WEB_TAG=.*/WEB_TAG=<versao-nova-do-front>/" .env.prod

# 3. puxa as imagens e troca só o que mudou (a API e o front-end reiniciam; o banco e o Nginx não)
docker compose --env-file .env.prod -f compose.prod.yaml pull app web
docker compose --env-file .env.prod -f compose.prod.yaml up -d

# 4. conferência (seção 5)
```

A aplicação executa as migrações novas do Flyway sozinha na subida.

**O que esperar durante a troca** (medido numa pilha de teste): o **front-end continua respondendo** o tempo todo; a **API fica fora uns 7 a 10 segundos**, o tempo de a única instância dela reiniciar (um 502 nas chamadas nesse intervalo). O Nginx **não é reiniciado** e acompanha os contêineres novos sozinho: ele consulta o DNS do Docker a cada poucos segundos (`resolver` no template), então um contêiner recriado com outro endereço não deixa o Nginx apontando para o antigo.

**Voltar atrás** (a versão nova deu problema):

```
# as versões que já passaram por este servidor
docker images --format '{{.Tag}}  (criada {{.CreatedSince}})' "$(grep '^APP_IMAGE=' .env.prod | cut -d= -f2)"

# volta para as versões anteriores; o pull traz a imagem do registro se ela não está mais no servidor
sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=<versao-anterior-da-api>/" .env.prod
sed -i "s/^WEB_TAG=.*/WEB_TAG=<versao-anterior-do-front>/" .env.prod
docker compose --env-file .env.prod -f compose.prod.yaml pull app web
docker compose --env-file .env.prod -f compose.prod.yaml up -d
```

**Construindo no servidor** (com `APP_IMAGE` vazio): em vez do `pull`, use `git pull`, uma etiqueta nova (por exemplo o hash do commit, `git rev-parse --short HEAD`) em `IMAGE_TAG` e `up -d --build`. Voltar atrás é voltar a uma etiqueta que ainda existe no servidor (`docker images booksmanager`). A imagem do front-end é construída no repositório dele (seção 3), e a volta atrás é igual.

> **Se a versão nova já tinha aplicado uma migração**, a versão antiga pode não funcionar com o banco novo. Nesse caso, volte a etiqueta **e** restaure o backup feito antes da atualização (seção 7.4): o que foi gravado depois do backup se perde. Por isso o backup antes de atualizar não é opcional.

### 6.1 Publicar uma versão (quem mantém o projeto)

A imagem é publicada pelo workflow `.github/workflows/publish-image.yml` quando se envia uma **tag de versão** para o GitHub. Nada é publicado a partir de um branch ou de uma pull request.

```
git switch main && git pull
git tag v1.0.0          # ou v1.0.0-rc.1, para uma pré-versão
git push origin v1.0.0
```

O workflow:

1. roda o mesmo `./mvnw verify` do CI; se falhar, **nada é publicado**;
2. confere que a tag tem o formato `v1.2.3` (ou `v1.2.3-rc.1`) e que o commit dela **já está na `main`**;
3. constrói a imagem, faz uma varredura de vulnerabilidades (Trivy), que **só avisa** no log e não impede a publicação;
4. publica em `ghcr.io/riverfount/booksmanager` com duas etiquetas: a **versão** (`1.0.0`, a tag sem o `v`) e o **hash do commit** (`sha-1a2b3c4`). Não existe `latest`: quem implanta sempre nomeia uma versão.

Ele usa só o `GITHUB_TOKEN` do próprio workflow, com a permissão de gravar pacotes apenas no job que publica; nenhum segredo foi acrescentado ao repositório, e a imagem não leva nenhum (a configuração vem do ambiente, como na seção 3).

**O front-end** é publicado do mesmo jeito, pelo workflow do repositório dele (`booksmanager-web`), com a mesma regra (uma tag `v*` de um commit da `main`) e as mesmas duas etiquetas (a versão e o hash), em `ghcr.io/riverfount/booksmanager-web`, depois de passar nos mesmos checks e no teste de fumaça da imagem. As duas versões não precisam ser iguais (seção 6.2).

**Na primeira publicação** de cada imagem, o GitHub cria o pacote como **privado**. Para o servidor puxar sem login, torne-o público uma vez: no GitHub, *Packages* → `booksmanager` (e `booksmanager-web`) → *Package settings* → *Change visibility* → *Public*. Isso não tem como ser feito pelo workflow. Depois, confira de qualquer máquina, sem login:

```
docker pull ghcr.io/riverfount/booksmanager:1.0.0
```

> Se preferir manter o pacote privado, o servidor precisa de um `docker login ghcr.io` com um token de leitura de pacotes (`read:packages`), guardado só nele.

### 6.2 Quais versões do front-end e da API andam juntas

As duas imagens são publicadas **separadamente** (cada repositório tem o próprio workflow e as próprias etiquetas). A ponte entre elas é o **contrato OpenAPI** da API. A tabela de mudanças da API (em `docs/projeto.md`, seção "Interface e API") classifica cada mudança de contrato:

- **"Compatível"** (só acrescenta: um campo, uma rota, um valor novo): o front-end antigo continua funcionando com a API nova. Nesse caso, **atualize primeiro a API**, e o front-end depois, quando quiser as telas novas.
- **"Muda o comportamento"** ou **"Quebra a entrada"**: o front-end antigo pode deixar de funcionar com a API nova. Atualize **as duas na mesma operação** (como no `up -d` acima, que troca as duas imagens de uma vez), e para voltar atrás, volte as duas.

Antes de atualizar, leia as linhas da tabela entre a versão que roda e a nova. No desenvolvimento, quem avisa é o `npm run api:types` do front-end: o `tsc` aponta o que quebrou.

## 7. Backup e restauração (`pg_dump`)

O banco é o único estado que importa: a imagem e os arquivos de configuração se refazem a partir do repositório e do `.env.prod`. **Guarde o `.env.prod` em um lugar seguro** (um gerenciador de senhas, por exemplo): sem o `APP_JWT_SECRET` e a senha do banco, o resto não se reconstrói.

Os comandos usam o formato "custom" do `pg_dump` (`-Fc`), compactado e restaurável com o `pg_restore`, inclusive só em parte.

### 7.1 O backup diário (script)

O `deploy/backup/backup.sh` faz o `pg_dump -Fc`, dá ao arquivo o nome `booksmanager-AAAA-MM-DD-HHMM.dump`, **confere que o arquivo não está vazio e que pode ser lido** (um dump cortado pela metade não lista o conteúdo), apaga os mais antigos pela retenção (14 dias no servidor), copia para fora do servidor (seção 7.2) e avisa o serviço de monitoramento. Se qualquer passo falha, o script para, apaga o arquivo incompleto e chama o aviso de falha. Não há segredo dentro dele.

**Configurar** (uma vez):

```
cd /opt/booksmanager
cp .env.backup-sample .env.backup && chmod 600 .env.backup     # preencha conforme a seção 7.2
deploy/backup/backup.sh                                         # roda uma vez, à mão, e confira o log
```

**Agendar** no `cron` do usuário que roda o Docker (`crontab -e`), todo dia às 02:30. Os scripts acham a pasta do projeto sozinhos, então o caminho pode ser absoluto, e não há `%` para escapar:

```
30 2 * * * /opt/booksmanager/deploy/backup/backup.sh >> /opt/booksmanager/backups/backup.log 2>&1
```

**Proteção dos arquivos:** a pasta `backups/` é criada com permissão `700` e cada backup com `600`, então outros usuários do servidor não os leem. O `.env.backup` também é `600` e está no `.gitignore`. O backup traz nome, documento e e-mail dos leitores, inclusive de menores: **não o copie para lugar nenhum sem criptografia** (seção 7.2). O `backup.log` cresce pouco (algumas linhas por dia); gire-o com o `logrotate` se quiser.

Cada variável do `.env.backup` está explicada no próprio `.env.backup-sample`. As principais: `BACKUP_KEEP_LOCAL` (14), `BACKUP_RCLONE_REMOTE`, `BACKUP_REMOTE_KEEP_DAYS` (90), `BACKUP_PING_URL` e `BACKUP_RESTORE_PING_URL`. O que está no ambiente vale mais que o arquivo.

### 7.2 A cópia para fora do servidor (`rclone`) e o aviso de falha

**Um backup só no disco do próprio banco não protege de perder o servidor.** O script copia o arquivo com o `rclone`, **só para um remoto criptografado** (`crypt`): ele recusa qualquer outro, porque o arquivo leva dados pessoais. Sem `BACKUP_RCLONE_REMOTE`, o backup fica só no servidor e o log avisa isso em toda execução (nada falha: é o ponto de partida enquanto não há para onde enviar).

**Montar o destino** (quando houver um provedor; o script não conhece nenhum):

1. Instale o `rclone` no servidor (pacote da distribuição ou <https://rclone.org/install/>).
2. `rclone config`: crie primeiro o remoto do armazenamento (um bucket de objetos, como Cloudflare R2 ou Backblaze B2, ou outro servidor por SFTP), e depois um segundo remoto do tipo **`crypt`** por cima dele, por exemplo `biblioteca-cripto`, apontando para `<remoto-do-armazenamento>:booksmanager-backups`, com a criptografia de nomes de arquivo ligada. O `rclone` pede uma senha e um sal: deixe-o gerar.
3. **Guarde a senha e o sal do `crypt` fora do servidor** (um gerenciador de senhas): sem eles, os backups criptografados **não abrem**, nem num servidor novo. O `rclone` também os mantém, de forma reversível, em `~/.config/rclone/rclone.conf` no servidor; mantenha esse arquivo em `600`.
4. Em `.env.backup`: `BACKUP_RCLONE_REMOTE=biblioteca-cripto:` (ou `biblioteca-cripto:subpasta`).
5. Rode o `deploy/backup/backup.sh` e confira o log (`remote: ... is there`) e, com `rclone ls biblioteca-cripto:`, que o arquivo aparece decifrado; no armazenamento de verdade ele tem nome e conteúdo ilegíveis.

> O backup remoto guarda os arquivos dos últimos `BACKUP_REMOTE_KEEP_DAYS` dias (90). As condições dos planos gratuitos dos provedores mudam: confira na página do provedor antes de escolher.

**O aviso de falha** é um serviço de monitoramento por ping (o Healthchecks.io tem plano gratuito). Crie duas verificações, uma diária e uma mensal, e ponha as URLs em `BACKUP_PING_URL` e `BACKUP_RESTORE_PING_URL`. O script chama a URL ao terminar com sucesso e a URL com `/fail` quando falha; **a falta do ping no prazo também gera o alerta**, o que cobre o caso de o `cron` nem ter rodado, que um aviso só em caso de erro não cobriria. Sem as URLs, ninguém é avisado e o único rastro é o log.

### 7.3 O teste de restauração (automático)

Um backup que nunca foi restaurado não é uma garantia. O `deploy/backup/restore-test.sh` restaura o backup **mais recente** num banco temporário (`restore_test_<número>`), confere que as migrações do Flyway estão aplicadas e sem falha, que as tabelas principais existem e podem ser lidas e que há ao menos um usuário, **apaga o banco temporário** (também quando falha) e registra o resultado em `backups/restore-test.log` e no aviso `BACKUP_RESTORE_PING_URL`. **Ele não mexe no banco de verdade.** Se o backup mais novo tem mais de 48 horas (`BACKUP_MAX_AGE_HOURS`), o teste falha: é o sinal de que o backup diário parou.

Agende uma vez por mês (dia 1, às 04:00):

```
0 4 1 * * /opt/booksmanager/deploy/backup/restore-test.sh >> /opt/booksmanager/backups/backup.log 2>&1
```

Para testar um arquivo específico (por exemplo, um baixado do remoto): `deploy/backup/restore-test.sh backups/<arquivo>.dump`.

### 7.4 Restaurar de verdade (perdeu ou estragou o banco)

**Isto apaga o banco atual e o troca pelo backup.** Pare a aplicação antes, para ninguém gravar durante a troca:

```
ARQ=backups/booksmanager-2026-10-22-0230.dump        # se só existe no remoto: rclone copy biblioteca-cripto:booksmanager-2026-10-22-0230.dump backups/
C="docker compose --env-file .env.prod -f compose.prod.yaml"

$C stop app
$C exec -T postgres sh -c 'dropdb -U "$POSTGRES_USER" --force "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
$C exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error' < "$ARQ"
$C start app
```

Depois, confira a saúde (seção 5). Se o servidor inteiro foi perdido: refaça a seção 3 num servidor novo (com o `.env.prod` guardado) e restaure o backup antes de usar.

## 8. No dia a dia

| Para | Comando |
|---|---|
| Ver o estado | `docker compose --env-file .env.prod -f compose.prod.yaml ps` |
| Ver os logs da API | `docker compose --env-file .env.prod -f compose.prod.yaml logs -f --tail=100 app` |
| Reiniciar a API | `docker compose --env-file .env.prod -f compose.prod.yaml restart app` |
| Parar tudo (os dados ficam) | `docker compose --env-file .env.prod -f compose.prod.yaml down` |
| Subir de novo | `docker compose --env-file .env.prod -f compose.prod.yaml up -d` |

**Nunca** use `down -v` nem `docker volume rm` no volume `booksmanager-prod_postgres_data`: é o banco.

A rotina que expira as reservas vencidas já roda sozinha (às 03:00 de São Paulo e uma vez quando a aplicação sobe); o log da API registra quantas expirou.

### 8.1 A trilha de auditoria (retenção)

A API grava, na tabela `audit_log`, quem fez cada operação sensível (login, usuários, bloqueio e inativação de leitor, multas, suspensões e a configuração da biblioteca). Só o administrador a consulta (`GET /api/v1/audit-log`), e nenhuma rota da API apaga um registro: o banco recusa até a alteração de uma linha.

A política é **guardar por 2 anos**, e a aplicação não apaga nada sozinha. Para limpar os registros mais antigos, uma vez por ano, por exemplo (faça um backup antes, seção 7):

```bash
docker compose --env-file .env.prod -f compose.prod.yaml exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "delete from audit_log where occurred_at < now() - interval '"'"'2 years'"'"'"'
```

Toda tentativa de login recusada é registrada, e a API não limita a taxa de requisições por conta própria, então alguém sem login poderia encher a tabela com tentativas. Quem limita é o **Nginx** (seção 8.2): 5 tentativas por minuto por endereço, com rajada de 5. Confira o tamanho da tabela de vez em quando (`select count(*), pg_size_pretty(pg_total_relation_size('audit_log')) from audit_log`).

### 8.2 O limite de tentativas de login e do acesso do leitor (Nginx)

O `deploy/nginx/nginx.conf.template` (uma única `location` para tudo o que a API responde, com os limites escritos uma vez) limita o `POST /api/v1/auth/login` a **5 tentativas por minuto por endereço de origem, com uma rajada de 5** (RNF13, RF27). Quem erra a senha algumas vezes não é barrado; um programa que tenta senhas em série passa a gerar poucas linhas por minuto na trilha. Passou do limite, a resposta é **429** (`application/problem+json`, com `Retry-After: 12`), e o log do Nginx registra `limiting requests` (nível `warn`). Só o login é contado: as demais rotas, inclusive o `/auth/refresh`, não têm limite.

Isto complementa o bloqueio da conta (que protege cada usuário) e não o substitui. O front-end e as demais rotas nunca são limitados. Os limites, a divisão das rotas, o que a API recebe (`Host` e `X-Forwarded-*`) e os cabeçalhos de segurança são conferidos pelo `deploy/nginx/test-routing.sh` (roda no CI): ele sobe o template **de verdade** na frente de dois servidores de mentira e confere cada caminho, inclusive as formas de tentar escapar do limite (`//api/...`, `/%61pi/...`).

**As rotas públicas do acesso do leitor** (Fase 6, RNF31) têm o mesmo limite, em uma zona à parte (`reader_access`): `POST /api/v1/auth/accept-invitation`, `/forgot-password` e `/reset-password`, **5 por minuto por endereço, com rajada de 5**, também com 429 em `application/problem+json`. Elas ficam abertas a quem tem um link ou um endereço de e-mail, e o `forgot-password` manda um e-mail a cada pedido. Os outros limites desse acesso são da aplicação, não do Nginx: **120 requisições por minuto por leitor** em `/api/v1/me/**` e **3 pedidos de "esqueci a senha" por hora por e-mail** (`app.rate-limit.*` no `application.properties`; contados em memória, o que vale para uma instância da API, como na pilha de produção). Para conferir o Nginx: `for i in $(seq 1 10); do curl -sk -o /dev/null -w '%{http_code} ' -X POST https://<DOMAIN>/api/v1/auth/forgot-password -H 'Content-Type: application/json' -d '{"email":"x@example.com"}'; done` mostra uns 6 códigos 204 e depois 429.

- **Atrás de uma CDN ou de outro proxy**, o endereço que o Nginx vê é o dele, e todos os usuários passariam a dividir o mesmo limite. Nesse caso, configure `set_real_ip_from` (a faixa do proxy) e `real_ip_header` antes de ligar o limite.
- **Muitos usuários atrás do mesmo endereço** (a rede de uma escola, por exemplo) dividem o limite. Se isso incomodar, aumente `rate` e `burst` no template.
- **Ajustar:** os valores estão no `map`/`limit_req_zone` (`rate=5r/m`) e no `limit_req` (`burst=5`) do template; depois, `docker compose ... restart nginx`.
- **Conferir** (a pilha precisa estar no ar): `for i in $(seq 1 15); do curl -sk -o /dev/null -w '%{http_code} ' -X POST https://<DOMAIN>/api/v1/auth/login; done` deve mostrar uns 6 códigos 401 e depois 429; passados alguns segundos, o 401 volta.

## 9. Problemas comuns

| Sintoma | Causa provável |
|---|---|
| `required variable ... is missing a value` ao subir | Falta preencher uma variável obrigatória no `.env.prod` (a mensagem diz qual). |
| O Nginx fica reiniciando e o log diz `cannot load certificate` | O certificado não está em `live/<DOMAIN>/fullchain.pem` e `privkey.pem`, ou `DOMAIN` e `LETSENCRYPT_DIR` não batem com onde ele está. |
| A `app` fica `unhealthy` ou reiniciando | Veja `logs app`: quase sempre é a senha do banco diferente da usada na criação do volume (a senha só vale na primeira criação do banco), ou `APP_JWT_SECRET` ausente ou curto. |
| O `Location` das criações sai com `http://` | O Nginx não está na frente, ou a API foi publicada direto na rede e a requisição não vem de um proxy em endereço interno. Confira que só o Nginx publica portas. |
| O navegador não conecta pela 443 | Firewall do servidor ou do provedor sem a porta 443 liberada. |
| O login do navegador responde **403 "Invalid CORS request"** (mas o `curl` entra) | O navegador manda o cabeçalho `Origin`, e a API só aceita se ele for o mesmo endereço que ela enxerga (`Host` e `X-Forwarded-*`, vindos do Nginx) ou estiver em `APP_CORS_ALLOWED_ORIGINS`. Confira que `DOMAIN` é o endereço que se digita no navegador e que `APP_CORS_ALLOWED_ORIGINS` (ou o padrão) é `https://<DOMAIN>`, sem barra no fim. |
| `required variable WEB_TAG is missing a value` | Falta a versão do front-end em `WEB_TAG` no `.env.prod` (não há `latest`). |
| A tela fica em branco, ou a raiz dá 502 ou 504 | O contêiner `web` não está saudável (`docker compose ... ps` e `logs web`), ou a versão de `WEB_TAG` não existe no registro. A API (`/actuator/health`) pode estar bem. |
| Um link direto (por exemplo `/livros`) dá 404 | O pedido não passou pelo Nginx da pilha: o `web` só responde direito atrás dele. Confira que só o `nginx` publica portas. |

## 10. O que este guia ainda não cobre

- Uma **política de segurança de conteúdo (CSP)** para o front-end: ainda não está configurada, e o `index.html` carrega as **fontes do Google Fonts** (um serviço de terceiros). Para um servidor sem acesso à internet, ou com CSP, isso precisa de decisão própria.
- **Mais de uma instância** da API (ou do front-end): a pilha tem uma de cada, e durante a atualização a API fica fora por alguns segundos (seção 6).
