# Books Manager: Documento do Projeto

Versão 0.1.0 · Outubro de 2026

Este documento reúne a visão, a arquitetura e a stack tecnológica do Books Manager. Os requisitos funcionais, as regras de negócio e os requisitos não funcionais ficam em `docs/requisitos.md`; o diagrama de classes, em `docs/diagramas.md`; e as histórias de usuário, em `docs/use-case.md`. Este documento é a referência de visão, arquitetura e plano — consulte-o junto com `docs/requisitos.md` quando a tarefa envolver uma regra de negócio ou requisito específico, e cite o ID (ex.: `RN05`, `RF12`).

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
| Observabilidade | Spring Boot Actuator | Só o `/actuator/health` é exposto, público, listando o status de cada componente, inclusive o banco (RNF23, issue #77); os detalhes ficam ocultos e responde 503 se o banco cair. |
| Ambiente | Docker Compose | Sobe o PostgreSQL pelo arquivo `compose.yaml`. |
| Desenvolvimento | Spring Boot DevTools | Reinício automático ao salvar. |
| Testes | Back-end: JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL), ArchUnit 1.4.1. Front-end: testes de componentes (por exemplo, Vitest e React Testing Library), a definir. | |
| Versionamento | Git e GitHub | GitHub Projects com milestones e issues (milestones por fase e uma issue por entrega). |
| Integração contínua | GitHub Actions | Workflow `.github/workflows/ci.yml`: `./mvnw verify` a cada push na `main` e a cada pull request, com JDK 25 e Docker do runner para os testes com Testcontainers (RNF06). |

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

A interface é uma aplicação React separada do backend, em repositório próprio, e se comunica com ele apenas por HTTP e JSON. Os controllers REST são os adaptadores de entrada da API: traduzem as requisições em commands dos casos de uso e devolvem DTOs próprios, nunca as classes de domínio. A API é versionada (`/api/v1`), seu contrato fica registrado no OpenAPI e o CORS é restrito à origem do front-end, configurada pela variável `APP_CORS_ALLOWED_ORIGINS` (issue #76): sem ela, nenhuma origem é aceita; só a rota `/api/**` é coberta e o filtro roda antes da cadeia de segurança, para o pré-voo `OPTIONS` (sem credenciais) não receber 401. O backend não guarda sessão no servidor.

Todos os recursos do catálogo (livros, autores, categorias e exemplares) seguem o mesmo contrato de CRUD, descrito a seguir. Os erros saem sempre em ProblemDetail (RFC 9457): 400 para requisição inválida, 404 para id inexistente e 422 para violação de regra de negócio.

| Operação | Rota | Resposta |
| --- | --- | --- |
| Cadastrar | `POST /api/v1/{recurso}` | 201 com `Location` e o recurso criado |
| Consultar | `GET /api/v1/{recurso}/{id}` | 200 com o recurso, inclusive inativo (`active=false`) |
| Listar | `GET /api/v1/{recurso}` | 200 com uma página (`PageResponse`) |
| Editar | `PATCH /api/v1/{recurso}/{id}` | 200 com o recurso atualizado |
| Inativar | `DELETE /api/v1/{recurso}/{id}` | 204 sem corpo |

#### Compatibilidade do contrato

O contrato da API é o OpenAPI gerado pelo SpringDoc (`/v3/api-docs`), e o front-end, em repositório próprio, depende dele. As regras para evoluí-lo dentro de `/api/v1`:

- **Não quebram** e podem entrar a qualquer momento: campo novo na resposta, parâmetro opcional novo na requisição, endpoint novo e valor novo em um enum de resposta que o cliente trata como desconhecido. O cliente deve ignorar campos que não conhece.
- **Quebram** e exigem uma versão nova (`/api/v2`): remover ou renomear campo, mudar o tipo de um campo, tornar obrigatório um parâmetro que era opcional, mudar o significado de um código de status ou de um valor existente.
- Toda mudança de contrato é citada na descrição da PR que a introduz e anotada na tabela abaixo, para o repositório do front-end acompanhar.
- Limitação conhecida: os esquemas gerados não marcam campos como obrigatórios (`required`), então clientes tipados a partir do OpenAPI veem todos como opcionais.

| Data | Mudança | Tipo | Issue |
| --- | --- | --- | --- |
| 14/10/2026 | `BookResponse` ganhou `availableCopies` (exemplares ativos e `AVAILABLE`), presente em cadastro, consulta, listagem e edição de livros. | Compatível | #83 |
| 14/10/2026 | `GET /api/v1/books` ganhou os filtros opcionais `title`, `author`, `isbn` e `categoryId`. | Compatível | #83 |

#### Listagem e paginação (RNF14)

- Parâmetros `page` (a partir de 0), `size` (de 1 a 100, padrão 20) e `sort` no formato `campo[,asc|desc]`, com os campos permitidos definidos por cada listagem. O `active` vale `true` por padrão, então os inativos só aparecem quando pedidos.
- Filtros próprios de cada recurso: `name` em autores e categorias; `bookId` e `status` em exemplares; `title`, `author`, `isbn` e `categoryId` em livros (ver "Livros", abaixo).
- A resposta é um `PageResponse` com `items`, `page`, `size`, `totalItems` e `totalPages`.
- Tamanho acima de 100, página negativa, campo de ordenação desconhecido ou filtro com valor inválido (um `status` inexistente, por exemplo) são recusados com 400, e nunca ajustados em silêncio.
- No núcleo, a paginação usa os tipos `PageQuery` e `PageResult`, de `shared.domain`, em Java puro. O `Pageable` do Spring Data fica restrito aos adaptadores de persistência, que o obtêm por `SpringDataPages` (em `shared.adapter.out.persistence`) e desempatam a ordenação pelo `id` para as páginas serem estáveis.

#### Edição com `PATCH`

- Usa JSON comum, não JSON Merge Patch. Só os campos enviados são alterados, e um campo omitido ou `null` mantém o valor atual; por isso não é possível "apagar" um campo obrigatório por essa rota.
- Os campos enviados seguem as mesmas regras de formato do cadastro.
- Campos imutáveis não fazem parte do DTO e, se vierem no corpo, são ignorados: o ISBN do livro (RN01), o código de patrimônio e o livro do exemplar.
- Quando o campo editado é único (o nome da categoria, por exemplo), a checagem ignora o próprio registro, porque manter o valor atual não é duplicidade. A restrição `unique` do banco é a defesa final contra a corrida entre a checagem e a gravação; para o `UPDATE` também ser traduzido em regra de negócio, o adaptador grava com `saveAndFlush`.

**Livros.** O conjunto `authorIds` enviado substitui o atual e nunca pode ser vazio. Categoria e autores só precisam existir e estar ativos quando são novos para o livro; os que ele já tinha continuam aceitos mesmo inativados depois (HU18, HU21).

**Pesquisa de livros (RF05).** É a própria listagem `GET /api/v1/books`, com filtros opcionais que valem em conjunto: `title`, `author` (nome de um dos autores) e `isbn` casam por trecho, sem diferenciar maiúsculas, e `categoryId` casa pelo id. No ISBN, hífens e espaços do termo são ignorados; nos textos, `%` e `_` valem como caracteres comuns. Toda resposta de livro (cadastro, consulta, listagem e edição) traz `availableCopies`, a quantidade de exemplares **ativos** com status `AVAILABLE`. A contagem pertence aos exemplares, não ao agregado `Book`: os casos de uso devolvem `BookWithAvailability` (o livro e a contagem), e a página inteira usa uma única consulta agregada em `CopyRepository.countAvailableByBookIds`, sem uma consulta por livro; o `BookSearchQueryCountTest` garante isso, comparando o número de consultas de uma página pequena e de uma grande (quatro por página: a página, o total, os autores em lote e os exemplares disponíveis). O autor é filtrado com `EXISTS`, então um livro com vários autores que casam aparece e é contado uma vez só.

**Exemplares.** Só o status é editável, e a regra fica em `Copy.changeStatus`. A rota manual aceita as transições para `DAMAGED` e `LOST` e a volta para `AVAILABLE` de um exemplar `DAMAGED` ou `LOST` (recuperado ou consertado). `BORROWED` e `RESERVED` só mudam pelos fluxos de empréstimo, devolução e reserva, e um exemplar emprestado não pode ser marcado como danificado ou extraviado por essa rota (RN10); a transição não permitida devolve 422. `markAsDamaged()` e `markAsLost()` não têm essa trava, para os fluxos de empréstimo poderem usá-los.

#### Inativação com `DELETE` (RN14)

- Nunca exclui fisicamente: inativa o registro. A resposta é 204, e inativar um registro já inativo é idempotente (também 204).
- O registro inativo continua acessível em `GET /{id}` com `active=false`, sai das listagens padrão e mantém suas associações (os livros de um autor inativo continuam com ele), mas não pode ser usado em cadastros novos.
- Um livro inativo não aceita exemplares novos (o cadastro de exemplar devolve 422). Um exemplar `BORROWED` não pode ser inativado (RN10) e também devolve 422.

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
- **Listagens sem N+1**: os autores do livro ficam em `book_author`, mapeados como coleção de IDs carregada sob demanda. Para a listagem paginada não disparar uma consulta por livro, a coleção usa `@BatchSize` com o tamanho máximo de página (issue #53), carregando os autores da página inteira em uma única consulta; `JOIN FETCH` foi descartado porque, com paginação, faz o Hibernate paginar em memória. Um teste com as estatísticas do Hibernate garante no máximo duas consultas por página.

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

O front-end é criado, executado e acompanhado separadamente, em seu próprio repositório; os comandos de instalação e execução ficam lá. Em desenvolvimento, a API precisa liberar o CORS para a origem do servidor de desenvolvimento do front-end.

## Plano de desenvolvimento

| Fase | Entrega | Situação |
| --- | --- | --- |
| 0. Base | Projeto configurado, banco com Docker Compose, Flyway, ArchUnit, health check, configuração de segurança provisória, CORS e integração contínua. O projeto do front-end (React e Tailwind CSS) é criado e acompanhado no repositório próprio dele, fora deste plano. | **Concluída** neste repositório: Docker Compose, Flyway, ArchUnit, `Makefile`, validação do esquema, health check do banco (RNF23), CORS (RNF09) e integração contínua (RNF06). |
| 1. Catálogo | Livros, autores, categorias e exemplares, com migrações, interface e testes. | **Concluída na API**, com cadastro, consulta, listagem, edição e inativação dos quatro recursos e a pesquisa de livros com exemplares disponíveis (RF05, issue #83). A interface fica no repositório do front-end. |
| 2. Leitores e acesso | Cadastro de leitores, usuários do sistema, login e perfis, na API e no front-end. | Não iniciada. |
| 3. Empréstimos | Configuração da biblioteca (política de penalidade), empréstimo, devolução, renovação, penalidades por atraso (multa ou suspensão) e relatório de atrasos, com o domínio completo e testes. | Não iniciada. |
| 4. Complementos | Reservas, relatórios adicionais e demais parâmetros configuráveis. | Não iniciada. |
| 5. Evolução | Notificações por e-mail, importação por ISBN, exportação de relatórios. | Não iniciada. |

O empréstimo concentra as regras mais importantes e deve ter o domínio mais completo e mais testado. O catálogo é o ponto de partida natural por ser a base dos demais módulos. Cada fase entrega a API aqui e as telas correspondentes no repositório do front-end.

A capacidade estimada é de cerca de 8 horas por semana, com mínimo de 4. Descontadas as três últimas semanas, reservadas para correções, documentação e estabilização, restam cerca de 135 horas de desenvolvimento no ritmo de 8 horas por semana, ou 70 horas no ritmo mínimo. Esse volume cobre os requisitos de prioridade M e deve deixar folga para boa parte dos itens S. Os itens S só entram depois que todos os M estiverem prontos e testados, e os C ficam para versões futuras.

Marcos sugeridos:

- **Fim de novembro:** fases 0 e 1 (base e catálogo). A API do catálogo já está pronta; restam as pendências acima.
- **Meados de dezembro:** fase 2 (leitores, usuários e login).
- **Meados de janeiro:** fase 3 (empréstimos, penalidades e relatório de atrasos), ou seja, todos os requisitos M.
- **Início de fevereiro:** fase 4, com as reservas e os demais itens S.
- **Últimas três semanas:** apenas correções, documentação e estabilização, sem funcionalidade nova.

Se no fim de dezembro a fase 3 ainda não estiver encaminhada, a fase 4 sai do plano e o escopo é reduzido antes de avançar, adiando primeiro os itens S (como o relatório de livros mais emprestados) e, se necessário, os M de menor impacto.

## Decisões tomadas

- **Java 25.** Versão confirmada para o projeto.
- **Front-end separado, em repositório próprio.** React com Tailwind CSS, consumindo a API REST do backend, em um repositório diferente deste (decidido em 10/10/2026; descartada a opção de pasta `frontend/` no mesmo repo). A criação e o acompanhamento do projeto do front-end ficam nesse repositório, e por isso saem das pendências da Fase 0 deste (decidido em 14/10/2026). As dependências do Thymeleaf já saíram do `pom.xml`, e o backend expõe apenas a API. Ainda em aberto, e a decidir lá: build, TypeScript ou JavaScript, roteamento e cliente HTTP.
- **Autenticação por JWT com refresh token** (decidido em 10/10/2026; descartada sessão com cookie).
- **Penalidade por atraso configurável.** A biblioteca escolhe, em configuração, entre multa, suspensão, ambas ou nenhuma (RF20, RF26, RN08 e RN16). O sistema não fixa essa escolha.
- **Valores iniciais da política de penalidade** (decidido em 10/10/2026): multa de R$ 1,00 por dia de atraso com teto de R$ 10,00; suspensão de 1 dia por dia de atraso com teto de 1 ano (ver nota em RN08 sobre o modelo de acúmulo assumido).