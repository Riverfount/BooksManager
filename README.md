# Books Manager

| | |
|---|---|
| **Aluno(a)** | Vicente Eduardo Ribeiro Marçal |
| **Turma** | TEC-N-001788/2026 |
| **Opção escolhida** | Proposta própria: controle de acervo e empréstimos de uma biblioteca |
| **Versão atual** | 0.1.0 |

---

## 1. Sobre o projeto

O Books Manager é uma aplicação web para organizar o acervo e os empréstimos de uma biblioteca escolar ou comunitária de pequeno porte.

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

Projeto em fase inicial. A estrutura base da aplicação, a conexão com o banco PostgreSQL e o controle de versão do esquema (Flyway) já estão configurados. Os requisitos, as histórias de usuário e o modelo de dados serão detalhados nas próximas etapas e acrescentados a este documento.

O controle de acesso por login será implementado em uma etapa posterior; até lá, a configuração de segurança é provisória.

---

## 3. Arquitetura

O projeto segue a **arquitetura hexagonal** (portas e adaptadores), organizado como um monólito modular. As regras de negócio ficam em um domínio escrito em Java puro, sem dependência de Spring ou JPA. Banco de dados, interface web e serviços externos se conectam ao domínio por meio de portas e são implementados por adaptadores. Testes com ArchUnit verificam essa separação durante o build.

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
4. Quando aparecer o aviso da porta **8080**, clique em **Abrir no navegador**.

### No computador (ferramentas instaladas)
Abra o projeto na IDE e execute a classe principal **BooksManagerApplication**, ou use o comando **./mvnw spring-boot:run** na raiz do projeto. Com o Docker em execução, o banco sobe sozinho. Acesse http://localhost:8080 no navegador.

### Documentação da API
Com a aplicação em execução, a documentação Swagger fica em http://localhost:8080/swagger-ui.html.

### Banco de dados
O PostgreSQL é executado em um container Docker, definido no arquivo **compose.yaml** na raiz do projeto. As tabelas são criadas e atualizadas por scripts do Flyway, na pasta **src/main/resources/db/migration**.

---

## 5. Tecnologias

| Área | Tecnologias |
|---|---|
| Linguagem e framework | Java 25 · Spring Boot 4.1 |
| Web | Spring MVC · Thymeleaf · Bootstrap 5 |
| Persistência | Spring Data JPA · PostgreSQL · Flyway |
| Segurança e validação | Spring Security · Bean Validation |
| Documentação da API | SpringDoc OpenAPI (Swagger UI) |
| Mapeamento | MapStruct |
| Testes | JUnit 5 · Testcontainers · ArchUnit |
| Ambiente | Docker Compose · Maven · Git/GitHub |

## 6. Uso de inteligência artificial
Utilizei o Claude (Anthropic) como apoio no planejamento inicial do projeto, para discutir a arquitetura, definir a stack tecnológica e organizar as ideias deste documento. As decisões e a implementação são de minha responsabilidade.