# Books Manager

| | |
|---|---|
| **Aluno(a)** | Vicente Eduardo Ribeiro Marçal |
| **Turma** | TEC-N-001788/2026 |
| **Opção escolhida** | Proposta própria: controle de acervo e empréstimos de uma biblioteca |
| **Versão atual** | 1.0.0 |

---

## 1. Sobre o projeto

O Books Manager é uma **API REST** para organizar o acervo e os empréstimos de uma biblioteca escolar ou comunitária de pequeno porte. Este repositório contém o back-end (Java + Spring Boot); o front-end será um projeto separado em React + Tailwind CSS, que consome essa API.

### 1.1 Problema
Bibliotecas desse porte costumam controlar livros e empréstimos em cadernos, planilhas ou mensagens de WhatsApp. Com isso, é difícil saber quais livros estão disponíveis, quem está com cada exemplar e quais devoluções estão atrasadas. As multas são calculadas à mão e não há histórico confiável do que foi emprestado.

### 1.2 Objetivo
Oferecer ao bibliotecário uma forma simples de cadastrar o acervo, registrar empréstimos e devoluções, acompanhar atrasos e calcular multas automaticamente.

### 1.3 Para quem
Bibliotecários, que operam o sistema no dia a dia, e o administrador, que gerencia usuários e parâmetros. Nesta primeira versão, o leitor não acessa o sistema.

### 1.4 O que o sistema vai fazer
- Cadastrar livros, autores, categorias, exemplares e leitores
- Registrar empréstimos, devoluções e renovações
- Calcular e controlar multas por atraso
- Pesquisar o acervo e mostrar a disponibilidade
- Listar os empréstimos em atraso

### 1.5 Estado atual
A Fase 1 (Catálogo) está concluída na API: livros, autores, categorias e exemplares têm cadastro, consulta, listagem paginada, edição e inativação. A pesquisa de livros, com a quantidade de exemplares disponíveis, é a própria listagem com filtros. A Fase 2 (Leitores e acesso) está concluída na API: os leitores têm cadastro, consulta, listagem paginada, edição, inativação e bloqueio (`/api/v1/readers`), e há login com JWT, autorização por perfil e a gestão dos usuários do sistema (`/api/v1/users`); a Fase 3 (empréstimos, multas e suspensões) está concluída na API: configuração da biblioteca, empréstimo, devolução com a penalidade da política configurada, renovação, consultas e histórico, pagamento e abono de multas, suspensões e o relatório de atrasos (`/api/v1/loans`, `/fines`, `/suspensions`, `/reports` e `/library-configuration`). A Fase 4 (complementos) está concluída na API: as reservas (reservar, consultar, cancelar, a fila, a retirada do exemplar guardado e a expiração automática às 03:00), o registro de perda ou dano na devolução, os relatórios de livros mais emprestados e da situação do acervo, e a implantação mínima, com a imagem de contêiner, o perfil de produção, o `compose` de produção com Nginx e HTTPS e o [guia de implantação](docs/implantacao.md). Com isso, todos os requisitos de prioridade M e S estão na API; a Fase 5 (evolução) está em andamento: as notificações por e-mail, a importação por ISBN a exportação dos relatórios em CSV e em PDF e a trilha de auditoria já estão na API. O plano completo e a situação de cada fase estão em [`docs/projeto.md`](docs/projeto.md), seção "Plano de desenvolvimento".

---

## 2. Documentação

| Documento | Conteúdo |
|---|---|
| [`docs/projeto.md`](docs/projeto.md) | Visão geral, atores, arquitetura, stack tecnológica, modelo de domínio, banco de dados, estratégia de testes, plano de desenvolvimento, rastreamento com o GitHub e decisões tomadas. |
| [`docs/requisitos.md`](docs/requisitos.md) | Requisitos funcionais (RF), regras de negócio (RN) e requisitos não funcionais (RNF), com prioridade MoSCoW. |
| [`docs/diagramas.md`](docs/diagramas.md) | Diagrama de classes das entidades e seus relacionamentos, com as enumerações e o estado de implementação de cada uma. |
| [`docs/use-case.md`](docs/use-case.md) | Histórias de usuário com critérios de aceite, organizadas por módulo. |
| [`docs/implantacao.md`](docs/implantacao.md) | Guia de implantação: a pilha de produção (Nginx com HTTPS, a API e o PostgreSQL), o certificado, a atualização e a volta atrás, e o backup automático do banco (`pg_dump`, cópia criptografada para fora do servidor com `rclone`, aviso de falha) com o teste de restauração mensal. |

---

## 3. Arquitetura

O projeto segue a **arquitetura hexagonal** (portas e adaptadores), organizado como um monólito modular dividido nos módulos `catalog`, `loan`, `user`, `security` e `shared`. Os cinco estão implementados (o `loan` reúne os empréstimos, as multas, as suspensões e as reservas). Os módulos só conversam por portas públicas e a dependência é de uma via: o `loan` usa as portas do `catalog` e do `user`, e o `catalog` pergunta ao `loan` por uma porta de saída própria, implementada por um adaptador do `loan`. As regras de negócio ficam em um domínio escrito em Java puro, sem dependência de Spring ou JPA. Banco de dados, adaptador web (API REST) e serviços externos se conectam ao domínio por meio de portas, implementadas por adaptadores. O `ArchitectureTest` (ArchUnit) já verifica essa separação a cada build: o domínio não pode depender de Spring, JPA/Hibernate, da aplicação nem dos adaptadores, e a aplicação não pode depender dos adaptadores. Outras regras do mesmo teste: o `catalog` e o `user` não dependem do `loan`, e todo controller declara quem pode chamá-lo.

O front-end (React + Tailwind CSS) é um projeto separado que consome esta API apenas por HTTP/JSON; nenhuma regra de negócio vive nele.

---

## 4. Como executar

### Requisitos
- JDK 25
- Docker em execução (usado para subir o PostgreSQL e para os testes com Testcontainers)
- Uma IDE (IntelliJ IDEA ou VS Code com o *Extension Pack for Java*)

### No GitHub Codespaces (recomendado)
1. No repositório, clique em **Code → Codespaces → Create codespace on main** (ou abra o Codespace existente).
2. Aguarde a preparação do ambiente.
3. Crie o arquivo **.env** a partir do **.env-sample** e preencha ao menos o **APP_JWT_SECRET** (veja a seção "Configuração e segredos"). No terminal, execute o comando **./mvnw spring-boot:run**. O Spring Boot sobe o container do PostgreSQL automaticamente a partir do arquivo **compose.yaml**.
4. Quando aparecer o aviso da porta **8080**, clique em **Abrir no navegador**. Isso abre a raiz da API (sem nada para visualizar); para a documentação, acesse **/swagger-ui.html** (veja a seção "Documentação da API" abaixo).

### No computador (ferramentas instaladas)
Abra o projeto na IDE e execute a classe principal **BooksManagerApplication**, ou use o comando **./mvnw spring-boot:run** na raiz do projeto. Antes, crie o arquivo **.env** a partir do **.env-sample** e preencha ao menos o **APP_JWT_SECRET** (veja a seção "Configuração e segredos"); sem ele a aplicação não sobe. Com o Docker em execução, o banco sobe sozinho. A API fica disponível em http://localhost:8080.

### Atalhos com Makefile
Os comandos mais usados do dia a dia também estão disponíveis como alvos do `make` (`make` ou `make help` lista todos):

| Alvo | Equivalente |
|---|---|
| `make run` | `./mvnw spring-boot:run` |
| `make test` | `./mvnw test` |
| `make verify` | `./mvnw verify` |
| `make image` | `docker build --tag booksmanager .` |
| `make up` | `docker compose up -d` |
| `make down` | `docker compose down` |
| `make clean` | `./mvnw clean` |

### Executar em contêiner
A API também vira uma imagem de contêiner (`Dockerfile`, em duas etapas: a construção usa o Temurin 25 JDK e a imagem final só a JRE 25 e o jar). A imagem **não tem segredos**: toda a configuração entra por variáveis de ambiente, e o contêiner roda como o usuário `app` (uid 10001), sem privilégios de administrador. Ela precisa de um PostgreSQL à parte.

```
make image                # ou: docker build --tag booksmanager .

docker network create biblioteca
docker run -d --name biblioteca-db --network biblioteca \
  -e POSTGRES_DB=biblioteca -e POSTGRES_USER=biblioteca -e POSTGRES_PASSWORD=<senha-do-banco> postgres:17

docker run -d --name biblioteca-api --network biblioteca -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://biblioteca-db:5432/biblioteca \
  -e SPRING_DATASOURCE_USERNAME=biblioteca -e SPRING_DATASOURCE_PASSWORD=<senha-do-banco> \
  -e APP_JWT_SECRET=<segredo com ao menos 32 bytes> \
  -e APP_CORS_ALLOWED_ORIGINS=http://localhost:5173 \
  -e APP_ADMIN_LOGIN=admin -e APP_ADMIN_PASSWORD=<senha-do-administrador> \
  booksmanager
```

| Variável | Para quê |
|---|---|
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD` | **Obrigatórias.** Onde está o PostgreSQL. As migrações do Flyway rodam sozinhas na subida. |
| `APP_JWT_SECRET` | **Obrigatória.** Veja "Configuração e segredos". |
| `APP_CORS_ALLOWED_ORIGINS`, `APP_ADMIN_LOGIN` e `APP_ADMIN_PASSWORD` | Como na seção "Configuração e segredos". |
| `JAVA_OPTS` | Opcional. Opções da JVM; o padrão é `-XX:MaxRAMPercentage=75` (a JVM usa até 75% da memória do contêiner). |

A imagem já roda o **perfil de produção** (`SPRING_PROFILES_ACTIVE=prod`, arquivo `application-prod.properties`): confia nos cabeçalhos `X-Forwarded-*` de um proxy reverso com TLS (só de um endereço interno, como a rede do Docker Compose; por isso só o proxy deve alcançar a porta da aplicação), então o `Location` dos `201` sai `https` com o host público; expõe só o `/actuator/health`, sem detalhes; esconde rastros de pilha dos erros; e liga a rotina que expira as reservas. O Swagger continua público. O `.env` não é lido nesse perfil. O Spring Boot Docker Compose (que sobe o PostgreSQL no desenvolvimento) já vem desligado na imagem (`SPRING_DOCKER_COMPOSE_ENABLED=false`). A imagem tem um `HEALTHCHECK` no `/actuator/health`: o contêiner fica `healthy` quando a aplicação e o banco respondem (`docker ps` mostra o estado) e `unhealthy` se o banco cair. O CI constrói a imagem a cada push e pull request, sem publicá-la, e valida o `compose.prod.yaml` e a configuração do Nginx (`nginx -t`); ela é publicada em `ghcr.io/riverfount/booksmanager` só quando se envia uma tag de versão (`v*`, veja o guia). O backup diário do banco e o teste de restauração mensal são scripts em `deploy/backup/` (guia de implantação, seção 7); o CI os confere com o ShellCheck. O Nginx limita o login a 5 tentativas por minuto por endereço (429 acima disso), para que a trilha de auditoria não seja enchida por quem não está autenticado. Para colocar a API em produção, atrás de um Nginx com HTTPS e com o PostgreSQL, use o `compose.prod.yaml` e siga o [guia de implantação](docs/implantacao.md) (primeira implantação, certificado, atualização, backup e restauração).

### Configuração e segredos
As variáveis de ambiente da aplicação estão no **.env-sample**. Copie-o para **.env** (que o Git ignora) e preencha: ao rodar localmente, a aplicação lê o **.env** da raiz do projeto sozinha (perfil `local`, o padrão), e uma variável exportada no ambiente tem prioridade sobre o arquivo. Os testes não leem o **.env**. Em produção, defina `SPRING_PROFILES_ACTIVE` com outro perfil (por exemplo, `prod`) e passe as variáveis pelo ambiente.

| Variável | Para quê |
|---|---|
| `APP_JWT_SECRET` | **Obrigatória.** Segredo que assina e valida os tokens JWT (HS256), com ao menos 32 bytes; a aplicação não sobe sem ele. Gere um com `openssl rand -base64 48`. |
| `APP_CORS_ALLOWED_ORIGINS` | Origem do front-end liberada no CORS, que permite credenciais (o cookie do refresh token); por isso aceita só origens exatas, nunca `*`. |
| `APP_ADMIN_LOGIN` e `APP_ADMIN_PASSWORD` | Administrador inicial (veja abaixo). |
| `APP_MAIL_ENABLED`, `APP_MAIL_HOST`, `APP_MAIL_PORT`, `APP_MAIL_USERNAME`, `APP_MAIL_PASSWORD`, `APP_MAIL_FROM` e `APP_MAIL_STARTTLS` | Opcionais. O envio de e-mail (RF28) fica **desligado por padrão**: sem `APP_MAIL_ENABLED=true` nada é enviado, só se registra o assunto em log. Ligado, exige o servidor SMTP (`APP_MAIL_HOST`) e o remetente (`APP_MAIL_FROM`); a porta é 587 e o STARTTLS fica ligado, se não for dito o contrário; usuário e senha só entram se o servidor pedir. Os tempos de conexão e de envio são curtos (3 s e 5 s). O e-mail é enviado só depois do commit e uma falha dele é registrada em log, sem desfazer a operação (RNF19). Com o e-mail ligado, o leitor também é avisado quando um exemplar reservado fica guardado para ele, com o último dia para retirar. |
| `APP_ISBN_LOOKUP_ENABLED` e `APP_ISBN_LOOKUP_BASE_URL` | Opcionais. A consulta de dados de livro por ISBN (RF06) no Open Library, que não pede chave: fica **desligada por padrão** e nos testes (nenhuma chamada de rede); com `APP_ISBN_LOOKUP_ENABLED=true` a consulta é feita com tempos curtos (2 s e 3 s) e guardada por 10 minutos. Serviço lento, fora do ar ou com resposta estranha é tratado como "não encontrado" e nunca impede o cadastro manual (RNF19). A rota que usa a consulta vem na #253. |
| `APP_LOAN_NOTICES_ENABLED`, `APP_LOAN_NOTICES_CRON` e `APP_LOAN_NOTICES_ZONE` | Opcionais. A rotina que avisa os leitores por e-mail (RF28) de que o empréstimo está para vencer e de que está em atraso: roda todo dia às 08:00 de `America/Sao_Paulo` (cron `0 0 8 * * *`) e uma vez quando a aplicação sobe, **só se o e-mail estiver ligado** (`APP_MAIL_ENABLED=true`); com o e-mail desligado nada é enviado nem registrado como enviado. Com quantos dias de antecedência o primeiro aviso sai é regra da biblioteca (`dueSoonNoticeDays` na configuração, padrão 2). Desligada nos testes. |
| `APP_RESERVATIONS_EXPIRATION_ENABLED`, `APP_RESERVATIONS_EXPIRATION_CRON` e `APP_RESERVATIONS_EXPIRATION_ZONE` | Opcionais. A rotina que expira as reservas vencidas e passa o exemplar ao próximo da fila: roda todo dia às 03:00 de `America/Sao_Paulo` (cron `0 0 3 * * *`, lido no fuso declarado, não no do servidor) e uma vez quando a aplicação sobe. Desligada nos testes. |

### Autenticação
A API usa tokens JWT no cabeçalho `Authorization: Bearer <token>`; não há sessão no servidor. Todas as rotas exigem um token válido, exceto a documentação (Swagger e OpenAPI), o health check e as rotas `/api/v1/auth/**`. Sem token, com token expirado ou inválido, a resposta é 401 em ProblemDetail. Há dois perfis, `ADMIN` e `OPERATOR`: o catálogo, os leitores e as operações do dia a dia são dos dois; os usuários do sistema (`POST /api/v1/users` cria um usuário, `GET /api/v1/users` e `GET /api/v1/users/{id}` os consultam, mostrando se estão bloqueados por tentativas de login, `PATCH /api/v1/users/{id}` muda o perfil e o nome de exibição (opcional; sem ele as telas mostram o login) `POST /api/v1/users/{id}/unblock` desbloqueia um usuário e `DELETE /api/v1/users/{id}` o inativa, sem excluí-lo e revogando seus refresh tokens, e `PUT /api/v1/users/{id}/password` redefine a senha, revogando os refresh tokens do usuário) e a configuração da biblioteca (`GET` e `PATCH /api/v1/library-configuration`: política de penalidade, prazos e limites) são só do `ADMIN`. Quem não tem permissão recebe 403 em ProblemDetail, e quem não está autenticado recebe 401 (a matriz completa está em `docs/projeto.md`). Para obter o token, faça `POST /api/v1/auth/login` com `{"login": "...", "password": "..."}`: a resposta traz o `accessToken` (válido por 15 minutos) e define o refresh token em um cookie `httpOnly` (7 dias), que o JavaScript não lê. O front-end deve fazer essa requisição com credenciais (`credentials: 'include'`). Login inexistente, senha errada, usuário inativo e usuário bloqueado recebem a mesma resposta 401. Depois de **3 senhas erradas** seguidas o usuário fica bloqueado por **15 minutos** (configurável em `app.login-lockout.*`) e o bloqueio termina sozinho; o 401 não diz que a conta está bloqueada. No Swagger, use o botão **Authorize** para colar o `accessToken`. Para renovar, `POST /api/v1/auth/refresh` (sem corpo) troca o refresh token do cookie por um novo access token e por um novo refresh token (cada um vale uma vez só); `POST /api/v1/auth/logout` encerra a sessão. As duas exigem o cabeçalho `X-Requested-With: XMLHttpRequest` (proteção contra CSRF) e a requisição com credenciais; um refresh token inválido, expirado ou já usado devolve 401 e limpa o cookie. `GET /api/v1/users/me` devolve o id, o login, o nome de exibição e o perfil de quem está logado, para qualquer perfil (fica fora de `/auth` porque essas rotas ignoram o cabeçalho `Authorization`).

### Documentação da API
A API é um back-end puro, sem páginas HTML; não há nada para visualizar na raiz **http://localhost:8080**. Com a aplicação em execução, a documentação Swagger fica em http://localhost:8080/swagger-ui.html, e as requisições à API usam o prefixo **/api/v1**. O Swagger mostra a versão do projeto (`v1.0.0`, a mesma do `pom.xml`); ela não é a versão da API, que continua sendo o prefixo `/api/v1`.

### Endpoints do catálogo
Todos os recursos do catálogo seguem o mesmo contrato, sob o prefixo **/api/v1**:

| Recurso | Rota base | Observações |
|---|---|---|
| Reativação | `POST /{id}/reactivate` em `/books`, `/authors`, `/categories` e `/copies` | **Só `ADMIN`** (o operador recebe 403). Devolve 200 com o registro; é idempotente para o que já está ativo e dá 404 para id inexistente. O livro só volta se a categoria e todos os autores estão ativos, e o exemplar só se o livro está ativo (422 diz o que falta); reativar o livro não reativa os exemplares. |
| Livros | `/books` | O ISBN é imutável depois do cadastro, e o ISBN-10 é guardado como o ISBN-13 do mesmo livro (os dois são o mesmo ISBN). **A editora (até 255 caracteres) e o ano de publicação (de 1450 até o ano atual) são obrigatórios no cadastro**; o `PATCH` troca os dois, mas não os esvazia, e um livro de antes da exigência, sem eles, segue editável e pode ser completado. `GET /books/lookup?isbn=` sugere título, autores (com o id do autor que o acervo já tem), editora e ano a partir do ISBN, para pré-preencher o cadastro: responde sempre 200 (`FOUND`, `NOT_FOUND` ou `ALREADY_REGISTERED`), 400 para ISBN inválido, e só consulta a fonte externa com `APP_ISBN_LOOKUP_ENABLED=true`. A listagem pesquisa por `title`, `author`, `isbn` (por trecho) e `categoryId`, e cada livro traz `availableCopies`, `totalCopies`, a categoria e os autores com nome. |
| Autores | `/authors` | Filtro por `name`. Cadastrar ou renomear para o nome (sem diferenciar maiúsculas) de **outro autor ativo** devolve 422 com o `existingAuthorId`; para confirmar um homônimo, envie `allowDuplicate: true`. Autor inativo não conta. |
| Categorias | `/categories` | Filtro por `name`; o nome é único. |
| Exemplares | `/copies` | Filtros por `bookId`, `status` e `assetCode` (o código de patrimônio inteiro, sem diferenciar maiúsculas); a edição altera só o status. |

| Operação | Requisição | Resposta |
|---|---|---|
| Cadastrar | `POST /{recurso}` | 201 |
| Consultar | `GET /{recurso}/{id}` | 200 |
| Listar | `GET /{recurso}?page=&size=&sort=&active=` | 200, página de até 100 itens |
| Editar | `PATCH /{recurso}/{id}` | 200, altera só os campos enviados |
| Inativar | `DELETE /{recurso}/{id}` | 204, nunca exclui fisicamente |

### Endpoints de empréstimos, multas e relatórios
Todos aceitam os perfis `ADMIN` e `OPERATOR` (a configuração da biblioteca, a exportação dos relatórios em CSV e PDF e a trilha de auditoria são só do `ADMIN`; o `OPERATOR` recebe 403) e as listagens são paginadas, com até 100 itens por página (`size` acima disso é 400):

| Operação | Requisição | Resposta |
|---|---|---|
| Registrar empréstimo | `POST /api/v1/loans` com `copyId` e `readerId` | 201 com `Location`; 422 com todos os motivos da recusa. O exemplar `RESERVED` só sai para o dono da reserva, até o último dia do prazo de retirada (a reserva passa a `FULFILLED`); outro leitor recebe "exemplar reservado para outro leitor" (RN03) |
| Reservar livro | `POST /api/v1/reservations` com `bookId` e `readerId` | 201 com `Location` e `queuePosition` (lugar na fila); 404 se livro ou leitor não existe; 422 com todos os motivos da recusa (RN05, RN11, RN12) |
| Consultar reserva | `GET /api/v1/reservations/{id}` | 200 com o nome do leitor, o título do livro, a situação, o lugar na fila (se espera exemplar) e o exemplar e o prazo de retirada (se segura um), inclusive para reserva encerrada; 404 se não existe |
| Listar reservas | `GET /api/v1/reservations?readerId=&bookId=&status=` | 200 paginada (`size` até 100, senão 400); `status` é `ACTIVE`, `FULFILLED`, `CANCELLED` ou `EXPIRED`; com `bookId` e `status=ACTIVE` é a fila do livro |
| Cancelar reserva | `POST /api/v1/reservations/{id}/cancel` | 200 com a reserva cancelada e, se ela guardava um exemplar, a reserva `served` que o recebeu (ou o exemplar volta a disponível); sem corpo, por ADMIN e OPERATOR; 404 se não existe; 422 se já encerrada (RF17, RF18) |
| Devolver | `POST /api/v1/loans/{id}/return` | 200 com os dias de atraso, a multa e a suspensão geradas e, quando há fila para o livro, a `reservation` atendida (o exemplar fica guardado para o primeiro da fila, RF18); corpo opcional `{"condition": "GOOD\|DAMAGED\|LOST"}` (RF15) |
| Renovar | `POST /api/v1/loans/{id}/renew` | 200 com a nova data prevista; 422 se encerrado, em atraso, no limite ou se outro leitor espera o livro na fila (RN07) |
| Consultar empréstimo | `GET /api/v1/loans/{id}` | 200, inclusive para empréstimo encerrado |
| Listar empréstimos | `GET /api/v1/loans?readerId=&copyId=&status=&overdue=` | 200 com o nome do leitor, o código do exemplar e o título do livro; `status` é `ACTIVE` ou `RETURNED` (o histórico); `overdue=true` traz só os atrasados e traz `daysLate` (400 com `status=RETURNED`) |
| Listar multas | `GET /api/v1/fines?readerId=&status=` | 200; cada multa traz o `pendingAmount` |
| Pagar multa | `POST /api/v1/fines/{id}/pay` | 200; 422 se já resolvida |
| Abonar multa | `POST /api/v1/fines/{id}/waive` com `{"reason": "..."}` | 200; 400 sem justificativa |
| Listar suspensões | `GET /api/v1/suspensions?readerId=&inForce=` | 200; traz o término e o `releaseDate` |
| Encerrar suspensão | `POST /api/v1/suspensions/{id}/end` com `{"reason": "..."}` | 200; 400 sem justificativa; sem efeito se já terminou |
| Relatório de atrasos | `GET /api/v1/reports/overdue-loans` | 200, do maior para o menor atraso |
| Livros mais emprestados | `GET /api/v1/reports/most-borrowed-books?from=&to=` | 200 paginada, do mais para o menos emprestado, com o livro (id e título) e o total; período obrigatório (AAAA-MM-DD, extremos incluídos, até 366 dias), senão 400 (RF24) |
| Situação do acervo | `GET /api/v1/reports/collection-status` | 200 com `totalCopies` (exemplares ativos), `activeByStatus` (`AVAILABLE`, `BORROWED`, `RESERVED`, `DAMAGED` e `LOST`, sempre os cinco, com zero quando vazio) e `inactiveCopies` à parte; a soma por status bate com o total (RF25) |
| Exportar relatórios em CSV (só `ADMIN`) | `GET /api/v1/reports/overdue-loans/csv`, `/most-borrowed-books/csv?from=&to=` e `/collection-status/csv` | 200 com o arquivo (`text/csv`, UTF-8 com BOM, separado por `;`, cabeçalho em português), todas as linhas do relatório na mesma ordem, no máximo 10.000 (acima disso, 422); 400 sem o período dos mais emprestados (RF29) |
| Exportar relatórios em PDF (só `ADMIN`) | `GET /api/v1/reports/overdue-loans/pdf`, `/most-borrowed-books/pdf?from=&to=` e `/collection-status/pdf` | 200 com o arquivo (`application/pdf`, A4 paisagem): título, data e hora da emissão (`America/Sao_Paulo`), filtros usados, tabela com o cabeçalho repetido em cada página e o total no fim; mesmas linhas, ordem e teto de 10.000 do CSV (acima disso, 422); 400 sem o período dos mais emprestados (RF29) |
| Trilha de auditoria (só `ADMIN`) | `GET /api/v1/audit-log?actorId=&action=&targetType=&targetId=&from=&to=` | 200 paginada, do mais recente para o mais antigo, com quem fez (id e login), quando, a ação, o alvo e um resumo curto; todos os filtros são opcionais; só leitura, nenhuma rota cria, altera ou apaga um registro; nunca traz senha, hash nem token (RF27) |
| Resumo do painel | `GET /api/v1/reports/dashboard` | 200 com `totalCopies`, `activeLoans`, `overdueLoans`, `suspendedReaders` (leitores, não suspensões) e `copiesByCategory` |
| Configuração da biblioteca | `GET` e `PATCH /api/v1/library-configuration` | 200 (só `ADMIN`) |
| Prazo e limites de empréstimo | `GET /api/v1/library-configuration/rules` | 200 com `loanPeriodDays`, `loanLimit`, `renewalLimit` e `reservationPickupDays`; `ADMIN` e `OPERATOR`, só leitura |

O que vale saber:
- A retirada é sempre hoje (no fuso `America/Sao_Paulo`) e a data prevista de devolução soma o prazo da configuração; nenhuma data é aceita no corpo.
- Um empréstimo é recusado por exemplar indisponível, **livro inativo**, limite de empréstimos simultâneos, multa pendente, suspensão em vigor, empréstimo em atraso ou leitor inativo ou bloqueado, e a mensagem traz todos os motivos de uma vez. Um livro inativo também não é reservado, e nem o dono de uma reserva retira o exemplar dele; a renovação de um empréstimo em curso de livro inativo também é recusada, mas a devolução continua funcionando.
- A política de penalidade (multa, suspensão, as duas ou nada) e seus números vêm da configuração. A penalidade é calculada na devolução e guardada: mudar a política depois não recalcula devoluções já registradas.
- Só a multa pendente impede novos empréstimos; paga ou abonada, o leitor volta a poder emprestar. Uma suspensão que já terminou não está em vigor, sem nenhuma rotina para fechá-la.
- Um exemplar com empréstimo ativo não pode ser inativado (`DELETE /api/v1/copies/{id}` devolve 422 até a devolução).
- Dois pedidos simultâneos sobre o mesmo registro deixam só um: o outro recebe 409 (tente novamente) ou 422.

Os erros seguem o formato ProblemDetail (400, 404, 409 e 422). Os detalhes do contrato estão em [`docs/projeto.md`](docs/projeto.md), seção "Interface e API", e cada endpoint está descrito no Swagger.

### Testes
- `make test` (ou `./mvnw test`) roda todos os testes; `make verify` (ou `./mvnw verify`) faz o build completo, inclusive com os testes de arquitetura.
- Os testes de persistência usam o PostgreSQL real via Testcontainers, por isso o Docker precisa estar em execução.
- Para rodar uma classe só: `./mvnw test -Dtest=BookTest`.
- **Cobertura mínima de 95%.** O JaCoCo roda com os testes e o `./mvnw verify` falha se a cobertura de instruções ou de linhas ficar abaixo de 95%, no código todo e, à parte, no domínio e na aplicação. O CI executa o mesmo `verify`, então um pull request abaixo do mínimo não passa (e, com o check `verify` obrigatório na `main`, não pode ser mesclado). O relatório fica em `target/site/jacoco/index.html` e, no CI, como o artefato `coverage-report`.

### CORS
O navegador só deixa o front-end chamar a API se a origem dele estiver liberada. Defina a variável de ambiente **APP_CORS_ALLOWED_ORIGINS** com a origem do front-end (várias, separadas por vírgula), por exemplo `http://localhost:5173`. Sem ela, nenhuma origem é aceita. O `.env-sample` traz o exemplo. São permitidos os métodos `GET`, `POST`, `PATCH`, `DELETE` e `OPTIONS` e os cabeçalhos `Authorization` e `Content-Type`.

### Administrador inicial
Sem nenhum usuário, ninguém consegue entrar para criar os outros. Por isso, na inicialização, a aplicação cria o administrador **se a tabela de usuários estiver vazia** e as duas variáveis de ambiente **APP_ADMIN_LOGIN** e **APP_ADMIN_PASSWORD** estiverem preenchidas (o `.env-sample` traz o exemplo; como no CORS, elas precisam estar no ambiente do processo). A senha precisa ter de 8 a 25 caracteres, com ao menos uma letra maiúscula, uma minúscula, um número e um caractere especial; se não cumprir, a inicialização falha com uma mensagem que diz o que falta (sem repetir a senha). A senha é gravada só como hash BCrypt e não aparece em log, em migração nem em arquivo versionado. Reiniciar a aplicação não cria um segundo administrador nem altera o existente; depois que o primeiro administrador entrar, as variáveis podem ser removidas. Se não houver usuário e as variáveis faltarem, a aplicação sobe normalmente e registra um aviso. O login é guardado em minúsculas.

### Verificação de saúde
A rota pública **http://localhost:8080/actuator/health** informa se a aplicação e o banco estão no ar (200 com `UP`, ou 503 com `DOWN` se a conexão com o banco falhar). É o único endpoint do Actuator exposto.

### Integração contínua
O workflow `.github/workflows/ci.yml` roda `./mvnw verify` (testes, Testcontainers e ArchUnit) com JDK 25 e em seguida constrói a imagem de contêiner (sem publicá-la; uma imagem que não constrói reprova o check) a cada push na `main` e a cada pull request. Se o build falhar, os relatórios de teste ficam disponíveis como artefato da execução por 7 dias. Nenhum segredo é necessário.

O workflow `.github/workflows/publish-image.yml` publica a imagem em `ghcr.io/riverfount/booksmanager` quando uma tag `v*` é enviada: roda o `verify`, confere que a tag é uma versão e que o commit está na `main`, varre a imagem com o Trivy (só avisa) e publica com as etiquetas da versão e do commit, sem `latest`. Usa só o `GITHUB_TOKEN`, com permissão de gravar pacotes apenas no job que publica.

### Banco de dados
O PostgreSQL é executado em um container Docker, definido no arquivo **compose.yaml** na raiz do projeto. As tabelas são criadas e atualizadas por scripts do Flyway, na pasta **src/main/resources/db/migration**; as migrações atuais são: a **V1__create_catalog.sql**, que cria as tabelas do catálogo (`category`, `author`, `book`, `book_author`, `copy`), a **V2__create_reader.sql**, que cria a tabela de leitores (`reader`), a **V3__create_app_user.sql**, que cria a tabela dos usuários do sistema (`app_user`), a **V4__create_refresh_token.sql**, que cria a tabela dos refresh tokens (`refresh_token`), a **V5__add_login_lockout_to_app_user.sql**, que acrescenta o bloqueio temporário de login a `app_user`, a **V6__create_library_configuration.sql**, que cria a configuração da biblioteca (`library_configuration`), a **V7__create_loan.sql**, que cria as tabelas de empréstimos, multas e suspensões (`loan`, `fine`, `suspension`), a **V8__add_display_name_to_app_user.sql**, que acrescenta o nome de exibição a `app_user`, a **V9__add_return_condition_to_loan.sql**, que acrescenta a condição do exemplar na devolução a `loan`, e a **V10__create_reservation.sql**, que cria a tabela das reservas (`reservation`).

---

## 5. Tecnologias

As versões abaixo refletem o que está declarado no **pom.xml** e no **compose.yaml** nesta versão do projeto.

| Área | Tecnologias |
|---|---|
| Linguagem e framework | Java 25 · Spring Boot 4.1.1 |
| Web | Spring MVC (API REST) |
| Persistência | Spring Data JPA (Hibernate) · PostgreSQL 17 · Flyway |
| Segurança e validação | Spring Security · Bean Validation |
| Documentação da API | SpringDoc OpenAPI 3.1.0 (Swagger UI) |
| Mapeamento | MapStruct 1.6.3 |
| Monitoramento | Spring Boot Actuator |
| Testes | JUnit 5 · Mockito · Spring Boot Test (fatias de web, JPA e segurança) · Testcontainers (PostgreSQL) · ArchUnit 1.4.1 |
| Apoio ao desenvolvimento | Spring Boot DevTools · Spring Boot Docker Compose |
| Ambiente | Docker Compose · Maven 3.9.16 (wrapper) · Git/GitHub · GitHub Actions |

O front-end (React + Tailwind CSS) é um projeto separado deste back-end, em repositório próprio, e o acompanhamento dele acontece lá. Suas ferramentas (build, TypeScript/JavaScript, roteamento, cliente HTTP) ainda não foram definidas.

## 6. Uso de inteligência artificial
Utilizei o Claude (Anthropic) como apoio no planejamento inicial do projeto, para discutir a arquitetura, definir a stack tecnológica e organizar as ideias deste documento. As decisões e a implementação são de minha responsabilidade.
