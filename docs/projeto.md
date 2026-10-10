# Books Manager: Documento do Projeto

Versão 0.1.0 · Outubro de 2026

Este documento reúne a visão, os requisitos, a arquitetura e a stack tecnológica do Books Manager. Ele é baseado em `.claude/spec.md` (07/10/2026), com as decisões tomadas em 10/10/2026 já incorporadas, algumas inconsistências corrigidas contra o estado real do repositório, e uma seção nova de rastreamento com as issues do GitHub. É a referência para o desenvolvimento — consulte-o quando a tarefa envolver uma regra de negócio ou requisito específico, e cite o ID (ex.: `RN05`, `RF12`) em commits e testes.

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

## Requisitos funcionais

Prioridade pelo método MoSCoW: **M** (deve, essencial na v1.0), **S** (deveria, entra na v1.0 se o prazo permitir) e **C** (poderia, versões futuras).

### Catálogo

| ID | Requisito | Prioridade |
| --- | --- | --- |
| RF01 | Cadastrar, listar, editar e inativar livros (ISBN, título, editora, ano e categoria). | M |
| RF02 | Cadastrar, listar, editar e inativar autores e associá-los aos livros (relação N:N). | M |
| RF03 | Cadastrar, listar, editar e inativar categorias. | M |
| RF04 | Cadastrar exemplares de um livro com código de patrimônio único e status, e alterar o status (disponível, emprestado, reservado, danificado, extraviado). | M |
| RF05 | Pesquisar livros por título, autor, ISBN e categoria, informando quantos exemplares estão disponíveis, com paginação e ordenação. | M |
| RF06 | Importar os dados de um livro automaticamente a partir do ISBN, por meio de API externa. | C |

### Leitores e acesso

| ID | Requisito | Prioridade |
| --- | --- | --- |
| RF07 | Cadastrar, listar, editar e inativar leitores (nome, documento, e-mail e telefone). | M |
| RF08 | Autenticar usuários por login e senha e controlar o acesso por perfil (administrador e operador). | M |
| RF09 | Gerenciar os usuários do sistema (criar, editar, inativar e redefinir senha), restrito ao administrador. | M |
| RF10 | Bloquear e desbloquear leitores manualmente. | M |

### Empréstimos

| ID | Requisito | Prioridade |
| --- | --- | --- |
| RF11 | Registrar o empréstimo de um exemplar a um leitor. | M |
| RF12 | Registrar a devolução e aplicar a penalidade por atraso conforme a política configurada pela biblioteca. | M |
| RF13 | Renovar um empréstimo, respeitando o limite de renovações. | M |
| RF14 | Consultar os empréstimos ativos e o histórico por leitor e por exemplar. | M |
| RF15 | Registrar perda ou dano do exemplar na devolução. | S |

### Reservas

| ID | Requisito | Prioridade |
| --- | --- | --- |
| RF16 | Reservar um livro sem exemplar disponível, entrando em uma fila de espera. | S |
| RF17 | Cancelar uma reserva, pelo leitor ou pelo operador. | S |
| RF18 | Ao devolver um exemplar, reservá-lo ao primeiro da fila de espera. | S |
| RF19 | Expirar automaticamente a reserva não retirada no prazo e passar a vez ao próximo da fila. | S |

### Multas

| ID | Requisito | Prioridade |
| --- | --- | --- |
| RF20 | Aplicar a penalidade por atraso conforme a política da biblioteca: multa por dia de atraso, suspensão do leitor, ambas ou nenhuma. | M |
| RF21 | Registrar o pagamento ou o abono de uma multa (quando a política prevê multa), exigindo justificativa no abono. | M |
| RF22 | Consultar as multas pendentes e as suspensões em vigor por leitor. | M |

### Relatórios e administração

| ID | Requisito | Prioridade |
| --- | --- | --- |
| RF23 | Exibir o relatório de empréstimos em atraso, ordenado do maior para o menor atraso. | M |
| RF24 | Exibir o relatório de livros mais emprestados por período. | S |
| RF25 | Exibir a situação do acervo (total, disponível, emprestado e danificado). | S |
| RF26 | Configurar as regras da biblioteca: política de penalidade por atraso (multa, suspensão, ambas ou nenhuma), valor diário da multa, dias de suspensão por dia de atraso, prazo de empréstimo, limite de empréstimos simultâneos, limite de renovações e prazo de retirada de reservas. | M |
| RF27 | Registrar trilha de auditoria das operações sensíveis (quem, quando e o quê). | C |
| RF28 | Notificar o leitor por e-mail sobre vencimento próximo, atraso e disponibilidade de livro reservado. | C |
| RF29 | Exportar relatórios em PDF e CSV. | C |

## Regras de negócio

As regras ficam na camada de domínio, ou em políticas parametrizáveis, e nunca em controllers ou adaptadores.

| ID | Regra |
| --- | --- |
| RN01 | O ISBN deve ser único por livro e válido (dígito verificador de ISBN-10 ou ISBN-13). |
| RN02 | O código de patrimônio deve ser único por exemplar. |
| RN03 | Só é permitido emprestar exemplares com status DISPONÍVEL. |
| RN04 | Um leitor pode ter no máximo 3 empréstimos ativos ao mesmo tempo (valor configurável). |
| RN05 | Leitor com multa pendente, suspensão em vigor ou empréstimo em atraso não pode fazer novos empréstimos. |
| RN06 | O prazo padrão de empréstimo é de 14 dias (valor configurável). |
| RN07 | Cada empréstimo pode ser renovado no máximo 2 vezes, e não pode ser renovado se estiver em atraso ou se houver reserva pendente para o livro. |
| RN08 | A penalidade por atraso segue a política configurada pela biblioteca: MULTA (dias de atraso × valor diário, com teto), SUSPENSÃO (dias de suspensão = dias de atraso × fator configurável, com teto), AMBAS ou NENHUMA. **Valores iniciais decididos em 10/10/2026**: MULTA = R$ 1,00 por dia de atraso, com teto de R$ 10,00; SUSPENSÃO = 1 dia por dia de atraso, com teto de 1 ano. |
| RN09 | Empréstimo já encerrado não pode ser renovado nem devolvido novamente. |
| RN10 | Exemplar com empréstimo ativo não pode ser excluído nem inativado. |
| RN11 | Leitor inativo, bloqueado ou suspenso não pode fazer empréstimos nem reservas. |
| RN12 | O leitor não pode reservar livro que já tem emprestado ou que tenha exemplar disponível. |
| RN13 | A reserva tem prazo de retirada de 3 dias (configurável) a contar da data em que o exemplar fica disponível para o leitor. |
| RN14 | Livros, autores, categorias e leitores com histórico de empréstimos são inativados, nunca excluídos fisicamente. |
| RN15 | A suspensão começa na devolução em atraso e termina automaticamente na data calculada. O operador pode encerrá-la antes, com justificativa registrada. |
| RN16 | A política de penalidade e seus valores são definidos pela biblioteca, em configuração, e nunca fixados no código. A alteração vale apenas para atrasos futuros. |

> Os valores de RN08 ficam pendentes de confirmação quanto ao modelo de acúmulo: assumimos "por dia de atraso, até o teto" (ex.: 3 dias de atraso = R$ 3,00; 15 dias de atraso = R$ 10,00, já no teto). Se a intenção era um valor fixo único por empréstimo em atraso, atualize esta linha.

## Requisitos não funcionais

Mesma escala de prioridade dos requisitos funcionais.

### Arquitetura e manutenibilidade

| ID | Requisito | Prioridade |
| --- | --- | --- |
| RNF01 | Seguir a arquitetura hexagonal: o domínio é Java puro, sem dependência de Spring, JPA ou qualquer framework, e isso é verificado por testes do ArchUnit no build. | M |
| RNF02 | Organizar o sistema como monólito modular, com fronteiras claras entre os módulos e comunicação apenas por portas públicas. | M |
| RNF03 | Versionar o esquema do banco com migrações do Flyway, sem alterações manuais no banco. | M |
| RNF04 | Padronizar os erros da API com ProblemDetail (RFC 9457) e exibir mensagens claras ao usuário na interface. | M |
| RNF05 | Manter cobertura de testes de pelo menos 80% no domínio e na camada de aplicação. | S |
| RNF06 | Executar build e testes automaticamente a cada push, com um pipeline de integração contínua (GitHub Actions). | M |

### Segurança

| ID | Requisito | Prioridade |
| --- | --- | --- |
| RNF07 | Armazenar senhas com hash forte (BCrypt), nunca em texto puro. | M |
| RNF08 | Exigir autenticação na API e autorizar o acesso por perfil em cada endpoint (administrador e operador). | M |
| RNF09 | Validar toda entrada de dados (Bean Validation) e proteger contra as vulnerabilidades do OWASP Top 10, incluindo XSS e CSRF, e restringir o CORS à origem do front-end. | M |
| RNF10 | Manter segredos (senhas de banco e chaves) fora do código-fonte, em variáveis de ambiente. | M |
| RNF11 | Coletar apenas os dados pessoais mínimos dos leitores (nome, documento e contato), em respeito à LGPD. | M |
| RNF12 | Servir a aplicação exclusivamente via HTTPS em produção. | S |
| RNF13 | Limitar tentativas de login e bloquear temporariamente após falhas repetidas. | C |

### Desempenho e confiabilidade

| ID | Requisito | Prioridade |
| --- | --- | --- |
| RNF14 | Paginar todas as listagens, com tamanho máximo de página de 100 itens. | M |
| RNF15 | Responder consultas e operações comuns em até 1 segundo, para acervo de até 100 mil livros. | S |
| RNF16 | Executar cada caso de uso de forma transacional (tudo ou nada). | M |
| RNF17 | Impedir dois empréstimos simultâneos do mesmo exemplar, com controle de concorrência (lock otimista com @Version e restrições no banco). | M |
| RNF18 | Fazer backup diário do banco em produção, com testes periódicos de restauração. | C |
| RNF19 | Garantir que falhas em sistemas externos (ISBN e e-mail) não impeçam as operações centrais. | C |

### Usabilidade

| ID | Requisito | Prioridade |
| --- | --- | --- |
| RNF20 | Oferecer interface web responsiva, desenvolvida em React com Tailwind CSS, em português do Brasil. | M |
| RNF21 | Permitir que o operador conclua um empréstimo ou uma devolução em poucas interações. | S |
| RNF22 | Seguir boas práticas de acessibilidade, com a norma WCAG 2.1 nível AA como meta. | C |

### Operação e interoperabilidade

| ID | Requisito | Prioridade |
| --- | --- | --- |
| RNF23 | Expor um endpoint de health check (Spring Boot Actuator) que também verifique a conexão com o banco. | M |
| RNF24 | Documentar a API REST com OpenAPI (Swagger UI), versioná-la (/api/v1) e trocar dados em JSON, com datas em ISO-8601. | M |
| RNF25 | Subir o ambiente de desenvolvimento com Docker Compose, sem instalar o banco localmente. | M |
| RNF26 | Manter a aplicação sem estado local (stateless) e configurável por variáveis de ambiente, permitindo executar em contêiner. | S |
| RNF27 | Permitir trocar o banco, o provedor de ISBN ou o de e-mail apenas substituindo o adaptador, sem alterar o domínio nem a aplicação. | M |
| RNF28 | Manter o front-end como aplicação separada, que consome a API apenas por HTTP e JSON, sem acesso direto ao banco e sem regras de negócio. | M |
| RNF29 | Conferir os tipos e as chamadas do front-end contra o contrato OpenAPI, para evitar divergência entre API e interface. | C |
| RNF30 | Testar o front-end com testes automatizados de componentes e de interface. | S |

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
├── catalogo        (livros, autores, categorias, exemplares)
│   ├── domain
│   ├── application   (portas de entrada e saída, serviços)
│   └── adapter       (in.web, out.persistence)
├── emprestimo      (empréstimos, multas, reservas)
│   ├── domain
│   ├── application
│   └── adapter
├── usuario         (leitores e usuários do sistema)
│   ├── domain
│   ├── application
│   └── adapter
├── seguranca       (autenticação e autorização)
└── compartilhado   (exceções, configuração e utilitários)
```

**Estado atual** (issue #1, concluída): `catalogo.domain`, `catalogo.application.port.in`, `catalogo.application.port.out`, `catalogo.application.service`, `catalogo.adapter.in.web`, `catalogo.adapter.out.persistence` e `compartilhado.domain` já existem no código (vazios, com `package-info.java` documentando cada um). Os pacotes de `emprestimo`, `usuario`, `seguranca` e os subpacotes `application`/`adapter` de `compartilhado` ainda não foram criados.

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

Testes com ArchUnit garantem no build que o domínio não importa Spring nem JPA, que a aplicação não depende dos adaptadores e que o domínio não depende da aplicação. **Implementado** (issue #4): `ArquiteturaTest`, com as três regras via `archunit-junit5` (o `pom.xml` tinha o artefato `archunit` core, sem o módulo de integração com JUnit 5 — trocado por `archunit-junit5`). Validado também por sabotagem: um import real do Spring usado em `RegraDeNegocioException` faz a regra falhar; revertido depois.

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

> Correção desta revisão: a versão anterior deste documento (derivada do `.claude/spec.md` de 07/10) listava Categoria e Autor sem o campo `ativo`, e Exemplar sem `livroId` nem `versão` — mas RN14 (inativação, não exclusão) e RNF17 (lock otimista) exigem esses campos, e as issues #5, #11 e #13, que já modelam essas entidades em detalhe, os incluem. Corrigido aqui para bater com o que está (ou vai ser) implementado.

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

O diagrama de classes em Mermaid deve ser acrescentado quando o curso exigir essa etapa.

## Banco de dados e migrações

- O esquema é criado e alterado somente por scripts do Flyway, no padrão `V<número>__<descrição>.sql` (dois underscores), na pasta `src/main/resources/db/migration`.
- O Hibernate roda com `ddl-auto=validate`, apenas conferindo o esquema, e com `open-in-view` desativado.
- Ordem sugerida das migrações: catálogo (livro, autor, categoria, exemplar), leitores e usuários, configuração da biblioteca, empréstimos e multas, reservas.
- Restrições no banco reforçam as regras de negócio: ISBN único, código de patrimônio único e controle de versão otimista na tabela de exemplares.
- **Estado atual**: `V1__criar_catalogo.sql` (issue #6) já cria as tabelas `categoria`, `autor`, `livro`, `livro_autor` e `exemplar`, com as restrições de RN01 (ISBN único) e RN02 (código de patrimônio único), e a coluna `versao` em `exemplar` para o lock otimista de RNF17 (o `@Version` da entidade JPA que a usa vem só na issue #16). Aplicada e verificada tanto por Testcontainers quanto contra o PostgreSQL real do `compose.yaml`.

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

## Rastreamento com o GitHub

O plano acima é a visão por fase de produto; o GitHub Projects (projeto "BooksManager", #5) é quem controla o trabalho dia a dia, com milestones e issues.

| Milestone no GitHub | Corresponde à fase do plano | Entrega até | Issues | Situação |
| --- | --- | --- | --- | --- |
| Fase 0 · Base | 0. Base | 30/11/2026 | #1–#4 | #1 mergeada (PR #17); #2 mergeada (PR #18); #3 mergeada (PR #19); #4 concluída nesta PR |
| Fase 1 · Catálogo | 1. Catálogo | 30/11/2026 | #5–#16 | #5 mergeada (PR #22); #6 mergeada (PR #23); #7 mergeada (PR #24); #8 mergeada (PR #25); #9 mergeada (PR #26, primeiro ciclo de ponta a ponta completo); #10 mergeada (PR #27); #11 mergeada (PR #28); #12 concluída nesta PR; #13–#16 com status "Ready" |

As fases 2 a 5 do plano (leitores/acesso, empréstimos, complementos, evolução) **ainda não têm milestone nem issues no GitHub** — só existem como linhas da tabela "Plano de desenvolvimento" acima.

### O que a especificação já cobre para as issues #2–#16 (ainda não feitas)

Conferido issue a issue contra as seções deste documento:

- **#2** (JPA `ddl-auto=validate`, `open-in-view=false`) — coberto pela seção "Banco de dados e migrações".
- **#3** (`RegraDeNegocioException`) — coberto implicitamente pela regra "domínio em Java puro" e por RNF04; a classe em si é detalhe de implementação, não precisa estar na spec.
- **#4** (testes ArchUnit) — coberto por RNF01 e pela seção "Verificação automática".
- **#5** (Categoria: nome até 100 caracteres, sem espaços nas pontas) — a spec cobre a existência da entidade (RF03) e a regra de inativação (RN14), mas **não** a restrição de tamanho/formatação do nome — isso é detalhe de validação que só está na própria issue.
- **#6** (migração `V1`, colunas e constraints exatas) — a spec cobre o requisito (RNF03) e as regras que a migração reforça (RN01, RN02), mas os nomes exatos de coluna/tabela são detalhe de implementação, não de spec.
- **#7–#9** (persistência, casos de uso e endpoints de Categoria) — cobertos pelas diretrizes de arquitetura (porta/adaptador, um caso de uso por interface, `ProblemDetail`).
- **#10** (algoritmo de dígito verificador do ISBN) — RN01 exige ISBN válido, mas não descreve o algoritmo; fica a cargo da issue.
- **#11–#13, #14–#16** (Autor, Livro, Exemplar/StatusExemplar e suas persistências) — cobertos pelo modelo de domínio (já corrigido nesta revisão) e por RN01–RN03, RN10, RN14, RNF17.

Em resumo: a especificação cobre bem o **requisito e a regra** de cada issue pendente; o que ela não cobre (e não precisa cobrir) são detalhes de implementação como algoritmos específicos, nomes de colunas e limites de validação — esses continuam só nas issues.

## Decisões tomadas

- **Java 25.** Versão confirmada para o projeto.
- **Front-end separado, em repositório próprio.** React com Tailwind CSS, consumindo a API REST do backend, em um repositório diferente deste (decidido em 10/10/2026; descartada a opção de pasta `frontend/` no mesmo repo). As dependências do Thymeleaf já saíram do `pom.xml`, e o backend expõe apenas a API.
- **Autenticação por JWT com refresh token** (decidido em 10/10/2026; descartada sessão com cookie).
- **Penalidade por atraso configurável.** A biblioteca escolhe, em configuração, entre multa, suspensão, ambas ou nenhuma (RF20, RF26, RN08 e RN16). O sistema não fixa essa escolha.
- **Valores iniciais da política de penalidade** (decidido em 10/10/2026): multa de R$ 1,00 por dia de atraso com teto de R$ 10,00; suspensão de 1 dia por dia de atraso com teto de 1 ano (ver nota em RN08 sobre o modelo de acúmulo assumido).

## Pontos em aberto

1. **Banco de dados.** O modelo do curso previa MySQL em produção. Confirmar se o PostgreSQL é aceito.
2. **Ferramentas do front-end.** Definir o build (por exemplo, Vite), a linguagem (TypeScript ou JavaScript), o roteamento e a forma de consumir a API. (O framework — React + Tailwind — e a separação em repositório próprio já estão decididos.)
3. **Prioridades da v1.0.** Definir, de acordo com o prazo de 24/02/2027, quais requisitos de prioridade S entram na primeira entrega.
