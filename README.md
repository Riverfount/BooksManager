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

### 1.4 O que o sistema vai fazer (visão inicial)
- Cadastrar livros, autores, categorias, exemplares e leitores
- Registrar empréstimos, devoluções e renovações
- Calcular e controlar multas por atraso
- Pesquisar o acervo e mostrar a disponibilidade
- Listar os empréstimos em atraso

---

## 2. Status do projeto

Projeto em fase inicial. Até o momento, foram configurados o esqueleto da aplicação Spring Boot, as dependências do projeto (web, persistência, segurança, Flyway, documentação da API, mapeamento e testes) e o container do PostgreSQL via Docker Compose. A conexão efetiva com o banco, os scripts de migração do Flyway, o domínio, os pacotes de arquitetura e os endpoints ainda não foram implementados. Os requisitos, as histórias de usuário e o modelo de dados serão detalhados nas próximas etapas e acrescentados a este documento.

O controle de acesso por login será implementado em uma etapa posterior; até lá, a configuração de segurança é provisória.

---

## 3. Arquitetura

O projeto vai seguir a **arquitetura hexagonal** (portas e adaptadores), organizado como um monólito modular dividido nos módulos `catalogo`, `emprestimo`, `usuario`, `seguranca` e `compartilhado`. As regras de negócio ficarão em um domínio escrito em Java puro, sem dependência de Spring ou JPA. Banco de dados, adaptador web (API REST) e serviços externos vão se conectar ao domínio por meio de portas, implementadas por adaptadores. O `ArquiteturaTest` (ArchUnit) já verifica essa separação a cada build: o domínio não pode depender de Spring, JPA/Hibernate, da aplicação nem dos adaptadores, e a aplicação não pode depender dos adaptadores.

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
3. No terminal, execute o comando **mvn spring-boot:run**. O Spring Boot sobe o container do PostgreSQL automaticamente a partir do arquivo **compose.yaml**.
4. Quando aparecer o aviso da porta **8080**, clique em **Abrir no navegador**. Isso abre a raiz da API (sem nada para visualizar); para a documentação, acesse **/swagger-ui.html** (veja a seção "Documentação da API" abaixo).

### No computador (ferramentas instaladas)
Abra o projeto na IDE e execute a classe principal **BooksManagerApplication**, ou use o comando **./mvnw spring-boot:run** na raiz do projeto. Com o Docker em execução, o banco sobe sozinho. A API fica disponível em http://localhost:8080.

### Documentação da API
A API é um back-end puro, sem páginas HTML; não há nada para visualizar na raiz **http://localhost:8080**. Com a aplicação em execução, a documentação Swagger fica em http://localhost:8080/swagger-ui.html, e as requisições à API usam o prefixo **/api/v1**.

### Banco de dados
O PostgreSQL é executado em um container Docker, definido no arquivo **compose.yaml** na raiz do projeto. As tabelas serão criadas e atualizadas por scripts do Flyway, na pasta **src/main/resources/db/migration**; essa pasta e as migrações ainda serão adicionadas nas próximas etapas.

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
| Ambiente | Docker Compose · Maven 3.9.16 (wrapper) · Git/GitHub |

O front-end (React + Tailwind CSS) é um projeto separado deste back-end; suas ferramentas (build, TypeScript/JavaScript, roteamento, cliente HTTP) e se ficará no mesmo repositório ou em um repositório próprio ainda não foram definidos.

## 6. Uso de inteligência artificial
Utilizei o Claude (Anthropic) como apoio no planejamento inicial do projeto, para discutir a arquitetura, definir a stack tecnológica e organizar as ideias deste documento. As decisões e a implementação são de minha responsabilidade.