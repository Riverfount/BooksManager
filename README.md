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
A Fase 1 (Catálogo) está concluída na API: livros, autores, categorias e exemplares têm cadastro, consulta, listagem paginada, edição e inativação. A pesquisa de livros, com a quantidade de exemplares disponíveis, é a própria listagem com filtros. Na Fase 2, os leitores já têm cadastro, consulta, listagem paginada, edição, inativação e bloqueio (`/api/v1/readers`); faltam os usuários do sistema e o login (Fase 2) e os empréstimos e multas (Fase 3). O plano completo e a situação de cada fase estão em [`docs/projeto.md`](docs/projeto.md), seção "Plano de desenvolvimento".

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

O projeto segue a **arquitetura hexagonal** (portas e adaptadores), organizado como um monólito modular dividido nos módulos `catalog`, `loan`, `user`, `security` e `shared`. Até agora estão implementados o `catalog` e o `shared`; os demais entram nas próximas fases. As regras de negócio ficam em um domínio escrito em Java puro, sem dependência de Spring ou JPA. Banco de dados, adaptador web (API REST) e serviços externos se conectam ao domínio por meio de portas, implementadas por adaptadores. O `ArchitectureTest` (ArchUnit) já verifica essa separação a cada build: o domínio não pode depender de Spring, JPA/Hibernate, da aplicação nem dos adaptadores, e a aplicação não pode depender dos adaptadores.

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
3. No terminal, execute o comando **./mvnw spring-boot:run**. O Spring Boot sobe o container do PostgreSQL automaticamente a partir do arquivo **compose.yaml**.
4. Quando aparecer o aviso da porta **8080**, clique em **Abrir no navegador**. Isso abre a raiz da API (sem nada para visualizar); para a documentação, acesse **/swagger-ui.html** (veja a seção "Documentação da API" abaixo).

### No computador (ferramentas instaladas)
Abra o projeto na IDE e execute a classe principal **BooksManagerApplication**, ou use o comando **./mvnw spring-boot:run** na raiz do projeto. Com o Docker em execução, o banco sobe sozinho. A API fica disponível em http://localhost:8080.

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

Os erros seguem o formato ProblemDetail (400, 404 e 422). Os detalhes do contrato estão em [`docs/projeto.md`](docs/projeto.md), seção "Interface e API", e cada endpoint está descrito no Swagger.

### Testes
- `make test` (ou `./mvnw test`) roda todos os testes; `make verify` (ou `./mvnw verify`) faz o build completo, inclusive com os testes de arquitetura.
- Os testes de persistência usam o PostgreSQL real via Testcontainers, por isso o Docker precisa estar em execução.
- Para rodar uma classe só: `./mvnw test -Dtest=BookTest`.

### CORS
O navegador só deixa o front-end chamar a API se a origem dele estiver liberada. Defina a variável de ambiente **APP_CORS_ALLOWED_ORIGINS** com a origem do front-end (várias, separadas por vírgula), por exemplo `http://localhost:5173`. Sem ela, nenhuma origem é aceita. O `.env-sample` traz o exemplo; a variável precisa estar no ambiente do processo (exportada no terminal ou configurada na IDE), pois o Spring não lê o `.env` sozinho. São permitidos os métodos `GET`, `POST`, `PATCH`, `DELETE` e `OPTIONS` e os cabeçalhos `Authorization` e `Content-Type`.

### Verificação de saúde
A rota pública **http://localhost:8080/actuator/health** informa se a aplicação e o banco estão no ar (200 com `UP`, ou 503 com `DOWN` se a conexão com o banco falhar). É o único endpoint do Actuator exposto.

### Integração contínua
O workflow `.github/workflows/ci.yml` roda `./mvnw verify` (testes, Testcontainers e ArchUnit) com JDK 25 a cada push na `main` e a cada pull request. Se o build falhar, os relatórios de teste ficam disponíveis como artefato da execução por 7 dias. Nenhum segredo é necessário.

### Banco de dados
O PostgreSQL é executado em um container Docker, definido no arquivo **compose.yaml** na raiz do projeto. As tabelas são criadas e atualizadas por scripts do Flyway, na pasta **src/main/resources/db/migration**; as migrações atuais são a **V1__create_catalog.sql**, que cria as tabelas do catálogo (`category`, `author`, `book`, `book_author`, `copy`), e a **V2__create_reader.sql**, que cria a tabela de leitores (`reader`).

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
