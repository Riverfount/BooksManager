# Books Manager

| | |
|---|---|
| **Aluno(a)** | Vicente Eduardo Ribeiro Marçal |
| **Turma** | TEC-N-001788/2026 |
| **Opção escolhida** | Proposta própria: controle de acervo e empréstimos de uma biblioteca |
| **Versão atual** | 0.0.1-SNAPSHOT |

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
A Fase 1 (Catálogo) está concluída na API: livros, autores, categorias e exemplares têm cadastro, consulta, listagem paginada, edição e inativação. A pesquisa de livros, com a quantidade de exemplares disponíveis, é a própria listagem com filtros. A Fase 2 (Leitores e acesso) está concluída na API: os leitores têm cadastro, consulta, listagem paginada, edição, inativação e bloqueio (`/api/v1/readers`), e há login com JWT, autorização por perfil e a gestão dos usuários do sistema (`/api/v1/users`); a Fase 3 (empréstimos, multas e suspensões) está em andamento, e a modelagem dela já está decidida em `docs/projeto.md`. O plano completo e a situação de cada fase estão em [`docs/projeto.md`](docs/projeto.md), seção "Plano de desenvolvimento".

---

## 2. Documentação

| Documento | Conteúdo |
|---|---|
| [`docs/projeto.md`](docs/projeto.md) | Visão geral, atores, arquitetura, stack tecnológica, modelo de domínio, banco de dados, estratégia de testes, plano de desenvolvimento, rastreamento com o GitHub e decisões tomadas. |
| [`docs/requisitos.md`](docs/requisitos.md) | Requisitos funcionais (RF), regras de negócio (RN) e requisitos não funcionais (RNF), com prioridade MoSCoW. |
| [`docs/diagramas.md`](docs/diagramas.md) | Diagrama de classes das entidades e seus relacionamentos, com as enumerações e o estado de implementação de cada uma. |
| [`docs/use-case.md`](docs/use-case.md) | Histórias de usuário com critérios de aceite, organizadas por módulo. |

---

## 3. Arquitetura

O projeto segue a **arquitetura hexagonal** (portas e adaptadores), organizado como um monólito modular dividido nos módulos `catalog`, `loan`, `user`, `security` e `shared`. Até agora estão implementados o `catalog`, o `user`, o `security` e o `shared`; o `loan` entra na Fase 3. As regras de negócio ficam em um domínio escrito em Java puro, sem dependência de Spring ou JPA. Banco de dados, adaptador web (API REST) e serviços externos se conectam ao domínio por meio de portas, implementadas por adaptadores. O `ArchitectureTest` (ArchUnit) já verifica essa separação a cada build: o domínio não pode depender de Spring, JPA/Hibernate, da aplicação nem dos adaptadores, e a aplicação não pode depender dos adaptadores.

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
| `make up` | `docker compose up -d` |
| `make down` | `docker compose down` |
| `make clean` | `./mvnw clean` |

### Configuração e segredos
As variáveis de ambiente da aplicação estão no **.env-sample**. Copie-o para **.env** (que o Git ignora) e preencha: ao rodar localmente, a aplicação lê o **.env** da raiz do projeto sozinha (perfil `local`, o padrão), e uma variável exportada no ambiente tem prioridade sobre o arquivo. Os testes não leem o **.env**. Em produção, defina `SPRING_PROFILES_ACTIVE` com outro perfil (por exemplo, `prod`) e passe as variáveis pelo ambiente.

| Variável | Para quê |
|---|---|
| `APP_JWT_SECRET` | **Obrigatória.** Segredo que assina e valida os tokens JWT (HS256), com ao menos 32 bytes; a aplicação não sobe sem ele. Gere um com `openssl rand -base64 48`. |
| `APP_CORS_ALLOWED_ORIGINS` | Origem do front-end liberada no CORS, que permite credenciais (o cookie do refresh token); por isso aceita só origens exatas, nunca `*`. |
| `APP_ADMIN_LOGIN` e `APP_ADMIN_PASSWORD` | Administrador inicial (veja abaixo). |

### Autenticação
A API usa tokens JWT no cabeçalho `Authorization: Bearer <token>`; não há sessão no servidor. Todas as rotas exigem um token válido, exceto a documentação (Swagger e OpenAPI), o health check e as rotas `/api/v1/auth/**`. Sem token, com token expirado ou inválido, a resposta é 401 em ProblemDetail. Há dois perfis, `ADMIN` e `OPERATOR`: o catálogo, os leitores e as operações do dia a dia são dos dois; os usuários do sistema (`POST /api/v1/users` cria um usuário, `GET /api/v1/users` e `GET /api/v1/users/{id}` os consultam, mostrando se estão bloqueados por tentativas de login, `PATCH /api/v1/users/{id}` muda o perfil `POST /api/v1/users/{id}/unblock` desbloqueia um usuário e `DELETE /api/v1/users/{id}` o inativa, sem excluí-lo e revogando seus refresh tokens, e `PUT /api/v1/users/{id}/password` redefine a senha, revogando os refresh tokens do usuário) e a configuração da biblioteca (`GET` e `PATCH /api/v1/library-configuration`: política de penalidade, prazos e limites) são só do `ADMIN`. Quem não tem permissão recebe 403 em ProblemDetail, e quem não está autenticado recebe 401 (a matriz completa está em `docs/projeto.md`). Para obter o token, faça `POST /api/v1/auth/login` com `{"login": "...", "password": "..."}`: a resposta traz o `accessToken` (válido por 15 minutos) e define o refresh token em um cookie `httpOnly` (7 dias), que o JavaScript não lê. O front-end deve fazer essa requisição com credenciais (`credentials: 'include'`). Login inexistente, senha errada, usuário inativo e usuário bloqueado recebem a mesma resposta 401. Depois de **3 senhas erradas** seguidas o usuário fica bloqueado por **15 minutos** (configurável em `app.login-lockout.*`) e o bloqueio termina sozinho; o 401 não diz que a conta está bloqueada. No Swagger, use o botão **Authorize** para colar o `accessToken`. Para renovar, `POST /api/v1/auth/refresh` (sem corpo) troca o refresh token do cookie por um novo access token e por um novo refresh token (cada um vale uma vez só); `POST /api/v1/auth/logout` encerra a sessão. As duas exigem o cabeçalho `X-Requested-With: XMLHttpRequest` (proteção contra CSRF) e a requisição com credenciais; um refresh token inválido, expirado ou já usado devolve 401 e limpa o cookie.

### Documentação da API
A API é um back-end puro, sem páginas HTML; não há nada para visualizar na raiz **http://localhost:8080**. Com a aplicação em execução, a documentação Swagger fica em http://localhost:8080/swagger-ui.html, e as requisições à API usam o prefixo **/api/v1**.

### Endpoints do catálogo
Todos os recursos do catálogo seguem o mesmo contrato, sob o prefixo **/api/v1**:

| Recurso | Rota base | Observações |
|---|---|---|
| Livros | `/books` | O ISBN é imutável depois do cadastro. A listagem pesquisa por `title`, `author`, `isbn` (por trecho) e `categoryId`, e cada livro traz `availableCopies`. |
| Autores | `/authors` | Filtro por `name`. |
| Categorias | `/categories` | Filtro por `name`; o nome é único. |
| Exemplares | `/copies` | Filtros por `bookId` e `status`; a edição altera só o status. |

| Operação | Requisição | Resposta |
|---|---|---|
| Cadastrar | `POST /{recurso}` | 201 |
| Consultar | `GET /{recurso}/{id}` | 200 |
| Listar | `GET /{recurso}?page=&size=&sort=&active=` | 200, página de até 100 itens |
| Editar | `PATCH /{recurso}/{id}` | 200, altera só os campos enviados |
| Inativar | `DELETE /{recurso}/{id}` | 204, nunca exclui fisicamente |

Os empréstimos começam em `POST /api/v1/loans`, que recebe só o exemplar e o leitor (`copyId` e `readerId`): a retirada é hoje e a data prevista de devolução vem do prazo da configuração da biblioteca. A resposta é 201 com `Location`; um empréstimo não permitido (exemplar indisponível, limite de empréstimos, multa pendente, suspensão, atraso, leitor inativo ou bloqueado) devolve 422 com todos os motivos, e dois pedidos simultâneos para o mesmo exemplar deixam só um (o outro recebe 409 ou 422).

A devolução é `POST /api/v1/loans/{id}/return`, sem corpo: encerra o empréstimo com a data de hoje, deixa o exemplar disponível e, se veio depois da data prevista, aplica a penalidade da política vigente na configuração (multa, suspensão, as duas ou nada). A resposta traz os dias de atraso, a multa e a suspensão geradas (nulas quando não houve); devolver de novo um empréstimo encerrado é 422, e mudar a política depois não recalcula devoluções já registradas.

A renovação é `POST /api/v1/loans/{id}/renew`, sem corpo: a data prevista avança o prazo da configuração a partir da data prevista atual. É recusada (422) para empréstimo encerrado, em atraso ou que já usou o limite de renovações da biblioteca.

Para consultar, `GET /api/v1/loans/{id}` (vale também para empréstimo encerrado) e `GET /api/v1/loans`, paginado (até 100 por página) com os filtros `readerId`, `copyId` e `status` (`ACTIVE` ou `RETURNED`): é o histórico de um leitor ou de um exemplar. Cada empréstimo traz as datas de retirada, prevista e de devolução e, se foi devolvido, os dias de atraso e a multa e a suspensão geradas.

A multa gerada por um atraso é resolvida com `POST /api/v1/fines/{id}/pay` (pagamento, sem corpo) ou `POST /api/v1/fines/{id}/waive` (abono, com `{"reason": "..."}`, justificativa obrigatória). Só a multa pendente impede novos empréstimos; depois de paga ou abonada o leitor volta a poder emprestar, e uma multa já resolvida não pode ser paga nem abonada de novo (422).

Os erros seguem o formato ProblemDetail (400, 404, 409 e 422). Os detalhes do contrato estão em [`docs/projeto.md`](docs/projeto.md), seção "Interface e API", e cada endpoint está descrito no Swagger.

### Testes
- `make test` (ou `./mvnw test`) roda todos os testes; `make verify` (ou `./mvnw verify`) faz o build completo, inclusive com os testes de arquitetura.
- Os testes de persistência usam o PostgreSQL real via Testcontainers, por isso o Docker precisa estar em execução.
- Para rodar uma classe só: `./mvnw test -Dtest=BookTest`.

### CORS
O navegador só deixa o front-end chamar a API se a origem dele estiver liberada. Defina a variável de ambiente **APP_CORS_ALLOWED_ORIGINS** com a origem do front-end (várias, separadas por vírgula), por exemplo `http://localhost:5173`. Sem ela, nenhuma origem é aceita. O `.env-sample` traz o exemplo. São permitidos os métodos `GET`, `POST`, `PATCH`, `DELETE` e `OPTIONS` e os cabeçalhos `Authorization` e `Content-Type`.

### Administrador inicial
Sem nenhum usuário, ninguém consegue entrar para criar os outros. Por isso, na inicialização, a aplicação cria o administrador **se a tabela de usuários estiver vazia** e as duas variáveis de ambiente **APP_ADMIN_LOGIN** e **APP_ADMIN_PASSWORD** estiverem preenchidas (o `.env-sample` traz o exemplo; como no CORS, elas precisam estar no ambiente do processo). A senha precisa ter de 8 a 25 caracteres, com ao menos uma letra maiúscula, uma minúscula, um número e um caractere especial; se não cumprir, a inicialização falha com uma mensagem que diz o que falta (sem repetir a senha). A senha é gravada só como hash BCrypt e não aparece em log, em migração nem em arquivo versionado. Reiniciar a aplicação não cria um segundo administrador nem altera o existente; depois que o primeiro administrador entrar, as variáveis podem ser removidas. Se não houver usuário e as variáveis faltarem, a aplicação sobe normalmente e registra um aviso. O login é guardado em minúsculas.

### Verificação de saúde
A rota pública **http://localhost:8080/actuator/health** informa se a aplicação e o banco estão no ar (200 com `UP`, ou 503 com `DOWN` se a conexão com o banco falhar). É o único endpoint do Actuator exposto.

### Integração contínua
O workflow `.github/workflows/ci.yml` roda `./mvnw verify` (testes, Testcontainers e ArchUnit) com JDK 25 a cada push na `main` e a cada pull request. Se o build falhar, os relatórios de teste ficam disponíveis como artefato da execução por 7 dias. Nenhum segredo é necessário.

### Banco de dados
O PostgreSQL é executado em um container Docker, definido no arquivo **compose.yaml** na raiz do projeto. As tabelas são criadas e atualizadas por scripts do Flyway, na pasta **src/main/resources/db/migration**; as migrações atuais são: a **V1__create_catalog.sql**, que cria as tabelas do catálogo (`category`, `author`, `book`, `book_author`, `copy`), a **V2__create_reader.sql**, que cria a tabela de leitores (`reader`), a **V3__create_app_user.sql**, que cria a tabela dos usuários do sistema (`app_user`), a **V4__create_refresh_token.sql**, que cria a tabela dos refresh tokens (`refresh_token`), a **V5__add_login_lockout_to_app_user.sql**, que acrescenta o bloqueio temporário de login a `app_user`, a **V6__create_library_configuration.sql**, que cria a configuração da biblioteca (`library_configuration`), e a **V7__create_loan.sql**, que cria as tabelas de empréstimos, multas e suspensões (`loan`, `fine`, `suspension`).

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
