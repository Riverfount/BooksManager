# Books Manager: Documento do Projeto

Versão 0.1.0 · Outubro de 2026

Este documento reúne a visão, a arquitetura e a stack tecnológica do Books Manager. 

Fizemos a reorganização da documentação assim requisitos funcionais, regras de negócio e requisitos não funcionais foram para `docs/requisitos.md`; o diagrama de classes, para `docs/diagramas.md`; e as histórias de usuário, para `docs/use-case.md`. Este documento continua sendo a referência de visão, arquitetura e plano — consulte-o junto com `docs/requisitos.md` quando a tarefa envolver uma regra de negócio ou requisito específico, e cite o ID (ex.: `RN05`, `RF12`).

## Visão geral

O Books Manager é uma aplicação web para organizar o acervo e os empréstimos de uma biblioteca escolar ou comunitária de pequeno porte.

### Problema

Bibliotecas desse porte costumam controlar livros e empréstimos em cadernos, planilhas ou mensagens de WhatsApp. Com isso, é difícil saber quais livros estão disponíveis, quem está com cada exemplar e quais devoluções estão atrasadas. As multas são calculadas à mão e não há histórico confiável do que foi emprestado.

### Objetivo

Oferecer ao bibliotecário uma forma simples de cadastrar o acervo, registrar empréstimos e devoluções, acompanhar atrasos e calcular multas automaticamente, mantendo as regras de negócio isoladas em um domínio testável. O sistema é composto por uma API REST (Java e Spring Boot) e por uma interface web separada (React e Tailwind CSS), em repositório próprio.

### Escopo da versão 1.0

A versão 1.0 é um projeto individual, com entrega prevista para 24/02/2027. Ela inclui o cadastro do acervo e dos leitores, empréstimos, devoluções, renovações, penalidades por atraso (multa ou suspensão, conforme a configuração), pesquisa, relatório de atrasos e controle de acesso por perfil. A política de penalidade por atraso é configurável pela biblioteca desde a primeira versão. Reservas e relatórios adicionais entram se o prazo permitir.

Ficam fora da versão 1.0: venda de livros, livros digitais, integração com sistemas contábeis, aplicativo móvel nativo, catalogação avançada (MARC21), acesso do leitor ao sistema e suporte a várias bibliotecas na mesma instalação (multi-tenancy).

## Atores

| Ator | Descrição |
| --- | --- |
| Administrador | Gerencia usuários do sistema e parâmetros, além de acessar todas as funções do operador. |
| Operador (bibliotecário) | Opera o acervo, os leitores, os empréstimos, as devoluções e as multas. |
| Sistema (agendador) | Executa rotinas automáticas, como a expiração de reservas e o envio de notificações. |
| Sistemas externos | API de consulta por ISBN e servidor de e-mail, ambos previstos para versões futuras. |

## Requisitos, regras de negócio e requisitos não funcionais

Requisitos funcionais (RF01–RF29), regras de negócio (RN01–RN16) e requisitos não funcionais (RNF01–RNF30), com prioridade MoSCoW, estão em `docs/requisitos.md`. Histórias de usuário com critérios de aceite: `docs/use-case.md`.

## Stack tecnológica

Valores conferidos com o `pom.xml` do projeto e atualizados pelas decisões registradas ao final do documento.

| Área | Tecnologia | Observação |
| --- | --- | --- |
| Linguagem | Java 25 | Versão definida para o projeto. |
| Framework | Spring Boot 4.1.1 | Gerenciador de versões das demais dependências. |
| Build | Maven (wrapper `mvnw`) | |
| Web | Spring MVC (`starter-webmvc`) | Controllers REST (JSON) e adaptadores de entrada da API. |
| Interface | React e Tailwind CSS | Em **repositório próprio**, separado deste backend (decidido em 10/10/2026). Ferramenta de build e linguagem (por exemplo, Vite e TypeScript) a definir. |
| Persistência | Spring Data JPA (Hibernate 7) | Usado apenas nos adaptadores de saída. |
| Banco de dados | PostgreSQL 17 | Em container Docker. O H2 foi descartado. |
| Migrações | Flyway com `flyway-database-postgresql` | Scripts em `src/main/resources/db/migration`. |
| Segurança | Spring Security | Autenticação por **JWT com refresh token** (decidido em 10/10/2026). Configuração provisória até a etapa de autenticação. |
| Validação | Bean Validation (`starter-validation`) | |
| Documentação da API | SpringDoc OpenAPI 3.1.0 | Swagger UI em `/swagger-ui.html`. Serve de contrato com o front-end. |
| Mapeamento | MapStruct 1.6.3 | Entre domínio, entidade JPA e DTO. Opcional no início. |
| Observabilidade | Spring Boot Actuator | **Já está no `pom.xml`** (desde o commit `e67ef56`). Endpoint `/actuator/health` disponível; falta confirmar/configurar a verificação explícita da conexão com o banco (RNF23). |
| Ambiente | Docker Compose | Sobe o PostgreSQL pelo arquivo `compose.yaml`. |
| Desenvolvimento | Spring Boot DevTools | Reinício automático ao salvar. |
| Testes | Back-end: JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL), ArchUnit 1.4.1. Front-end: testes de componentes (por exemplo, Vitest e React Testing Library), a definir. | |
| Versionamento | Git e GitHub | GitHub Projects com milestones e issues (ver seção "Rastreamento com o GitHub"). |

## Arquitetura

O sistema segue a arquitetura hexagonal (portas e adaptadores) em um monólito modular. O domínio e os casos de uso ficam no centro, sem depender de framework. Tudo o que é externo (banco, interface web, e-mail, API de ISBN) se conecta por portas, que são interfaces, e é implementado por adaptadores. A regra de dependência é única: tudo aponta para dentro.

### Estrutura de pacotes

```
com.riverfount.booksmanager
├── catalog     (livros, autores, categorias, exemplares)
│   ├── domain
│   ├── application   (portas de entrada e saída, serviços)
│   └── adapter       (in.web, out.persistence)
├── loan        (empréstimos, multas, reservas)
│   ├── domain
│   ├── application
│   └── adapter
├── user        (leitores e usuários do sistema)
│   ├── domain
│   ├── application
│   └── adapter
├── security    (autenticação e autorização)
└── shared      (exceções, configuração e utilitários)
```

### Diretrizes

- **Domínio em Java puro.** Sem anotações de Spring ou JPA. As regras de negócio (prazos, multas, limites) ficam nas entidades e em políticas do domínio.
- **Referência entre agregados por identificador.** O empréstimo guarda o ID do exemplar e do leitor, não os objetos, o que mantém os agregados pequenos e desacoplados.
- **Tempo injetado.** O domínio recebe a data atual por parâmetro ou por `Clock`, em vez de chamar `LocalDate.now()`, o que torna os testes determinísticos.
- **Um caso de uso por interface**, com command próprio, implementado em um serviço de aplicação que apenas orquestra: carrega agregados, chama o domínio e persiste.
- **Portas de saída na linguagem do domínio.** Os repositórios e gateways são definidos pela aplicação, e a implementação com JPA fica no adaptador.
- **Entidade JPA separada do domínio** no módulo de empréstimos, com mapeamento explícito. Nos cadastros simples do catálogo, é aceitável uma abordagem mais pragmática, para evitar cerimônia sem retorno.
- **Comunicação entre módulos** por portas públicas. O módulo de empréstimos consulta o catálogo por uma porta de saída, implementada por um adaptador interno. Eventos de domínio ficam para quando houver necessidade real.
- **Transações** na camada de aplicação, no wiring ou com `@Transactional`, conforme a decisão de cada módulo.

### Política de penalidade configurável

A penalidade por atraso é uma política do domínio, com variantes para multa, suspensão, ambas e nenhuma. A devolução recebe a política vigente, lida da configuração da biblioteca por uma porta de saída, e devolve a penalidade a aplicar. Assim, mudar de multa para suspensão é uma alteração de configuração, não de código, e cada variante é testada isoladamente. Valores iniciais: ver RN08.

### Interface e API

A interface é uma aplicação React separada do backend, em repositório próprio, e se comunica com ele apenas por HTTP e JSON. Os controllers REST são os adaptadores de entrada da API: traduzem as requisições em commands dos casos de uso e devolvem DTOs próprios, nunca as classes de domínio. A API é versionada (`/api/v1`), seu contrato fica registrado no OpenAPI e o CORS é restrito à origem do front-end. O backend não guarda sessão no servidor.

### Verificação automática

Testes com ArchUnit garantem no build que o domínio não importa Spring nem JPA, que a aplicação não depende dos adaptadores e que o domínio não depende da aplicação. **Implementado** (issue #4): `ArchitectureTest` (renomeada de `ArquiteturaTest` na issue #41), com as três regras via `archunit-junit5` (o `pom.xml` tinha o artefato `archunit` core, sem o módulo de integração com JUnit 5 — trocado por `archunit-junit5`). Validado também por sabotagem: um import real do Spring usado na exceção de regra de negócio do domínio (`BusinessRuleException`) faz a regra falhar; revertido depois.

## Modelo de domínio

O livro (a obra) é separado do exemplar (a cópia física), o que permite várias cópias do mesmo título e o controle do empréstimo por unidade.

### Entidades

| Entidade | Atributos principais |
| --- | --- |
| Livro | id, Isbn, título, editora, ano de publicação, categoriaId, autorIds, ativo |
| Autor | id, nome, ativo |
| Categoria | id, nome, ativo |
| Exemplar | id, livroId, código de patrimônio, status (DISPONÍVEL, EMPRESTADO, RESERVADO, DANIFICADO, EXTRAVIADO), ativo, versão (lock otimista) |
| Leitor | id, nome, documento, e-mail, telefone, ativo, bloqueado, suspenso até (data) |
| Empréstimo | id, data de retirada, data prevista de devolução, data de devolução, número de renovações |
| Multa | id, valor, status (PENDENTE, PAGA, ABONADA), justificativa do abono |
| Reserva | id, data da reserva, status (ATIVA, ATENDIDA, CANCELADA, EXPIRADA) |
| Usuário | id, login, hash da senha, perfil (ADMINISTRADOR, OPERADOR), ativo |
| Configuração | id, política de penalidade (MULTA, SUSPENSÃO, AMBAS, NENHUMA), valor diário da multa, teto da multa, dias de suspensão por dia de atraso, teto da suspensão, prazo de empréstimo, limite de empréstimos simultâneos, limite de renovações, prazo de retirada de reservas |

### Relacionamentos

| Entidade A | Relação | Entidade B |
| --- | --- | --- |
| Livro | muitos para muitos | Autor |
| Livro | muitos para um | Categoria |
| Livro | um para muitos | Exemplar |
| Exemplar | um para muitos | Empréstimo |
| Leitor | um para muitos | Empréstimo |
| Empréstimo | um para zero ou um | Multa |
| Leitor | um para muitos | Reserva |
| Livro | um para muitos | Reserva |

Diagrama de classes (Mermaid), com as enumerações e o estado de implementação de cada entidade: `docs/diagramas.md`.

## Banco de dados e migrações

- O esquema é criado e alterado somente por scripts do Flyway, no padrão `V<número>__<descrição>.sql` (dois underscores), na pasta `src/main/resources/db/migration`.
- O Hibernate roda com `ddl-auto=validate`, apenas conferindo o esquema, e com `open-in-view` desativado.
- Ordem sugerida das migrações: catálogo (livro, autor, categoria, exemplar), leitores e usuários, configuração da biblioteca, empréstimos e multas, reservas.
- Restrições no banco reforçam as regras de negócio: ISBN único, código de patrimônio único e controle de versão otimista na tabela de exemplares.
- **Estado atual**: `V1__create_catalog.sql` (issue #6; nomes de tabela e coluna traduzidos para inglês na issue #41, já que o banco ainda não tinha dados reais) cria as tabelas `category`, `author`, `book`, `book_author` e `copy`, com as restrições de RN01 (ISBN único) e RN02 (código de patrimônio único), e a coluna `version` em `copy` para o lock otimista de RNF17. O `@Version` da entidade JPA que a usa de fato veio na issue #16, com teste de concorrência determinístico (duas cópias do mesmo exemplar, a segunda a salvar falha com `ObjectOptimisticLockingFailureException`). Aplicada e verificada tanto por Testcontainers quanto contra o PostgreSQL real do `compose.yaml`.

## Estratégia de testes

| Camada | Tipo de teste | Ferramentas |
| --- | --- | --- |
| Domínio | Unitário puro, sem mocks | JUnit 5 |
| Aplicação | Unitário com portas simuladas ou implementações em memória | JUnit 5 e Mockito |
| Adaptador web | Teste de fatia da camada web | `@WebMvcTest` e MockMvc |
| Adaptador de persistência | Integração com banco real | `@DataJpaTest` e Testcontainers (PostgreSQL) |
| Arquitetura | Regras de dependência entre pacotes | ArchUnit |
| Interface (front-end) | Testes de componentes e de interface | A definir (por exemplo, Vitest e React Testing Library) |

Os testes com Testcontainers exigem o Docker em execução.

## Como executar

1. Instale o JDK 25 e tenha o Docker em execução.
2. Na raiz do projeto, execute `./mvnw spring-boot:run`, ou rode a classe `BooksManagerApplication` pela IDE.
3. O Spring Boot sobe o PostgreSQL automaticamente a partir do arquivo `compose.yaml`.
4. A API responde em http://localhost:8080. A documentação Swagger fica em http://localhost:8080/swagger-ui.html.

Enquanto a autenticação não for implementada, a configuração de segurança é provisória. Sem ela, todas as rotas exigem login com uma senha gerada no console a cada execução.

O front-end é executado separadamente, em seu próprio repositório. Os comandos de instalação e execução serão acrescentados quando o projeto da interface for criado. Em desenvolvimento, a API precisa liberar o CORS para a origem do servidor de desenvolvimento do front-end.

## Plano de desenvolvimento

| Fase | Entrega |
| --- | --- |
| 0. Base | Projeto configurado, banco com Docker Compose, Flyway, ArchUnit, health check, configuração de segurança provisória, CORS e projeto do front-end (React e Tailwind CSS) criado. |
| 1. Catálogo | Livros, autores, categorias e exemplares, com migrações, interface e testes. |
| 2. Leitores e acesso | Cadastro de leitores, usuários do sistema, login e perfis, na API e no front-end. |
| 3. Empréstimos | Configuração da biblioteca (política de penalidade), empréstimo, devolução, renovação, penalidades por atraso (multa ou suspensão) e relatório de atrasos, com o domínio completo e testes. |
| 4. Complementos | Reservas, relatórios adicionais e demais parâmetros configuráveis. |
| 5. Evolução | Notificações por e-mail, importação por ISBN, exportação de relatórios e integração contínua. |

O empréstimo concentra as regras mais importantes e deve ter o domínio mais completo e mais testado. O catálogo é o ponto de partida natural por ser a base dos demais módulos. Cada fase entrega a API e as telas correspondentes do front-end.

A capacidade estimada é de cerca de 8 horas por semana, com mínimo de 4. Descontadas as três últimas semanas, reservadas para correções, documentação e estabilização, restam cerca de 135 horas de desenvolvimento no ritmo de 8 horas por semana, ou 70 horas no ritmo mínimo. Esse volume cobre os requisitos de prioridade M e deve deixar folga para boa parte dos itens S. Os itens S só entram depois que todos os M estiverem prontos e testados, e os C ficam para versões futuras.

Marcos sugeridos:

- **Fim de novembro:** fases 0 e 1 (base e catálogo).
- **Meados de dezembro:** fase 2 (leitores, usuários e login).
- **Meados de janeiro:** fase 3 (empréstimos, penalidades e relatório de atrasos), ou seja, todos os requisitos M.
- **Início de fevereiro:** fase 4, com as reservas e os demais itens S.
- **Últimas três semanas:** apenas correções, documentação e estabilização, sem funcionalidade nova.

Se no fim de dezembro a fase 3 ainda não estiver encaminhada, a fase 4 sai do plano e o escopo é reduzido antes de avançar, adiando primeiro os itens S (como o relatório de livros mais emprestados) e, se necessário, os M de menor impacto.

## Decisões tomadas

- **Java 25.** Versão confirmada para o projeto.
- **Front-end separado, em repositório próprio.** React com Tailwind CSS, consumindo a API REST do backend, em um repositório diferente deste (decidido em 10/10/2026; descartada a opção de pasta `frontend/` no mesmo repo). As dependências do Thymeleaf já saíram do `pom.xml`, e o backend expõe apenas a API.
- **Autenticação por JWT com refresh token** (decidido em 10/10/2026; descartada sessão com cookie).
- **Penalidade por atraso configurável.** A biblioteca escolhe, em configuração, entre multa, suspensão, ambas ou nenhuma (RF20, RF26, RN08 e RN16). O sistema não fixa essa escolha.
- **Valores iniciais da política de penalidade** (decidido em 10/10/2026): multa de R$ 1,00 por dia de atraso com teto de R$ 10,00; suspensão de 1 dia por dia de atraso com teto de 1 ano (ver nota em RN08 sobre o modelo de acúmulo assumido).