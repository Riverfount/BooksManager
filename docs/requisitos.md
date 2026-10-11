# Books Manager: Requisitos

> Requisitos funcionais, regras de negócio e requisitos não funcionais do projeto, extraídos de `docs/projeto.md` na reorganização da documentação. Continua em português — é a referência de negócio do projeto; cite o ID (ex.: `RN05`, `RF12`) em commits e testes.

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

## Referências

Modelo de domínio e diagrama de classes: `docs/diagramas.md`. Histórias de usuário: `docs/use-case.md`. Visão geral, arquitetura, stack, plano de desenvolvimento e decisões: `docs/projeto.md`.
