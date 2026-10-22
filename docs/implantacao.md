# Guia de implantação

Como colocar o Books Manager (a API) em um servidor, atrás de HTTPS, e como atualizá-lo, voltar atrás e fazer o backup do banco. É a implantação mínima da v1.0 (RNF12, RNF18, RNF26): **um servidor Linux, o Docker e três contêineres**. O front-end é outro repositório e fica fora deste guia.

## 1. O que sobe

```
internet ──► Nginx (portas 80 e 443) ──► API (porta 8080, só na rede interna) ──► PostgreSQL (só na rede interna)
```

| Serviço | O que faz |
|---|---|
| `nginx` | Termina o TLS (HTTPS), redireciona a porta 80 para a 443, manda os cabeçalhos HSTS e `X-Forwarded-*` e repassa tudo para a API. É o **único** que publica portas. |
| `app` | A API (imagem construída pelo `Dockerfile`, perfil `prod`). Não é alcançável de fora. |
| `postgres` | O banco (PostgreSQL 17), com os dados em um volume do Docker. Não é alcançável de fora. |
| `certbot` | Só para emitir e renovar o certificado do Let's Encrypt. Não sobe com o `up` (veja a seção 4). |

Os arquivos: `compose.prod.yaml` (a pilha), `deploy/nginx/nginx.conf.template` (o Nginx) e `.env.prod-sample` (o modelo das variáveis). Nenhum deles tem segredo.

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
   | `APP_CORS_ALLOWED_ORIGINS` | A origem do front-end, com `https` e sem barra no fim. |
   | `APP_ADMIN_LOGIN` e `APP_ADMIN_PASSWORD` | O primeiro administrador. É criado na subida **só se não houver nenhum usuário**; depois que ele existe, a senha pode sair do arquivo. |
   | `LETSENCRYPT_DIR` | Onde está o certificado (veja a seção 4). |

   `POSTGRES_DB`, `POSTGRES_USER` e `IMAGE_TAG` têm valores que servem como estão.

3. **Providencie o certificado** (seção 4). O Nginx **não sobe** sem ele.

4. **Suba a pilha** (a primeira vez constrói a imagem, o que leva alguns minutos):

   ```
   docker compose --env-file .env.prod -f compose.prod.yaml up -d --build
   ```

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
# os três serviços de pé; app e postgres "healthy"
docker compose --env-file .env.prod -f compose.prod.yaml ps

# a saúde, por HTTPS (200 e {"status":"UP"...}); o banco entra nessa conta
curl -fsS https://biblioteca.escola.org.br/actuator/health

# a porta 80 só redireciona (301 para https)
curl -sI http://biblioteca.escola.org.br/ | head -3

# o HSTS está lá, uma vez só
curl -sI https://biblioteca.escola.org.br/actuator/health | grep -i strict-transport

# o Swagger (a documentação da API)
# abra no navegador: https://biblioteca.escola.org.br/swagger-ui.html
```

E, com o token de um login, confira que o `Location` das criações vem em `https` com o domínio público (e não `http://app:8080/...`): crie qualquer coisa (por exemplo uma categoria) e olhe o cabeçalho `Location` da resposta `201`.

Se algo não subir, `docker compose --env-file .env.prod -f compose.prod.yaml logs app` (ou `nginx`, `postgres`) mostra o motivo. Veja também a seção 9.

## 6. Atualização e volta atrás

A etiqueta da imagem (`IMAGE_TAG`) é a versão que está rodando, e voltar atrás é voltar a uma etiqueta anterior. **Faça um backup antes de cada atualização** (seção 7): as migrações do banco só andam para a frente.

**Atualizar:**

```
cd /opt/booksmanager

# 1. backup (seção 7)

# 2. o código novo e uma etiqueta nova (o hash do commit)
git pull
export TAG=$(git rev-parse --short HEAD)
sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$TAG/" .env.prod

# 3. constrói a imagem nova e troca só o que mudou (a aplicação reinicia, o banco e o Nginx não)
docker compose --env-file .env.prod -f compose.prod.yaml up -d --build

# 4. conferência (seção 5)
```

A aplicação executa as migrações novas do Flyway sozinha na subida.

**Voltar atrás** (a versão nova deu problema):

```
# as imagens que existem no servidor
docker images booksmanager --format '{{.Tag}}  (criada {{.CreatedSince}})'

# volta para a etiqueta anterior, sem reconstruir
sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=<etiqueta-anterior>/" .env.prod
docker compose --env-file .env.prod -f compose.prod.yaml up -d
```

> **Se a versão nova já tinha aplicado uma migração**, a versão antiga pode não funcionar com o banco novo. Nesse caso, volte a etiqueta **e** restaure o backup feito antes da atualização (seção 7.4): o que foi gravado depois do backup se perde. Por isso o backup antes de atualizar não é opcional.

## 7. Backup e restauração (`pg_dump`)

O banco é o único estado que importa: a imagem e os arquivos de configuração se refazem a partir do repositório e do `.env.prod`. **Guarde o `.env.prod` em um lugar seguro** (um gerenciador de senhas, por exemplo): sem o `APP_JWT_SECRET` e a senha do banco, o resto não se reconstrói.

Os comandos usam o formato "custom" do `pg_dump` (`-Fc`), compactado e restaurável com o `pg_restore`, inclusive só em parte.

### 7.1 Fazer um backup

```
cd /opt/booksmanager
mkdir -p backups
docker compose --env-file .env.prod -f compose.prod.yaml exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > backups/biblioteca-$(date +%F-%H%M).dump
```

O arquivo sai no servidor, na pasta `backups/` (ignorada pelo Git). **Um backup só no mesmo disco do banco não protege de um disco perdido**: copie os arquivos para outra máquina ou serviço (`rsync`, `rclone`, um disco externo).

### 7.2 Agendar

No `cron` (`crontab -e`), todo dia às 02:30, guardando 14 dias. **Em um `crontab`, o `%` precisa de barra invertida** (`\%`), senão a linha quebra:

```
30 2 * * * cd /opt/booksmanager && docker compose --env-file .env.prod -f compose.prod.yaml exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > backups/biblioteca-$(date +\%F-\%H\%M).dump && find backups -name '*.dump' -mtime +14 -delete
```

(Esta automação é só documentada: um script próprio, com cópia para fora do servidor e alerta de falha, é trabalho de prioridade C.)

### 7.3 Testar a restauração (faça de vez em quando)

Um backup que nunca foi restaurado não é uma garantia. O teste usa um banco temporário e **não mexe no banco de verdade**:

```
ARQ=backups/biblioteca-2026-10-22-0230.dump        # o backup a testar
C="docker compose --env-file .env.prod -f compose.prod.yaml"

$C exec -T postgres sh -c 'createdb -U "$POSTGRES_USER" restauracao_teste'
$C exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d restauracao_teste --no-owner --exit-on-error' < "$ARQ"

# compare com o banco de verdade: os números devem bater (ou ser menores, se o backup for mais antigo)
$C exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d restauracao_teste -tAc "select count(*) from reader"'
$C exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "select count(*) from reader"'
$C exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d restauracao_teste -tAc "select count(*) from flyway_schema_history where success"'

# apaga o banco temporário
$C exec -T postgres sh -c 'dropdb -U "$POSTGRES_USER" restauracao_teste'
```

### 7.4 Restaurar de verdade (perdeu ou estragou o banco)

**Isto apaga o banco atual e o troca pelo backup.** Pare a aplicação antes, para ninguém gravar durante a troca:

```
ARQ=backups/biblioteca-2026-10-22-0230.dump
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

## 9. Problemas comuns

| Sintoma | Causa provável |
|---|---|
| `required variable ... is missing a value` ao subir | Falta preencher uma variável obrigatória no `.env.prod` (a mensagem diz qual). |
| O Nginx fica reiniciando e o log diz `cannot load certificate` | O certificado não está em `live/<DOMAIN>/fullchain.pem` e `privkey.pem`, ou `DOMAIN` e `LETSENCRYPT_DIR` não batem com onde ele está. |
| A `app` fica `unhealthy` ou reiniciando | Veja `logs app`: quase sempre é a senha do banco diferente da usada na criação do volume (a senha só vale na primeira criação do banco), ou `APP_JWT_SECRET` ausente ou curto. |
| O `Location` das criações sai com `http://` | O Nginx não está na frente, ou a API foi publicada direto na rede e a requisição não vem de um proxy em endereço interno. Confira que só o Nginx publica portas. |
| O navegador não conecta pela 443 | Firewall do servidor ou do provedor sem a porta 443 liberada. |
| O navegador do front-end bloqueia as chamadas (CORS) | `APP_CORS_ALLOWED_ORIGINS` não é exatamente a origem do front-end (com `https`, sem barra no fim). |

## 10. O que este guia ainda não cobre

- A **publicação da imagem** em um registro (o CI só a constrói): hoje a imagem é construída no próprio servidor.
- A **automação do backup** (script, cópia para fora, alerta): só documentada aqui (prioridade C).
- O **front-end**, que é outro repositório.
