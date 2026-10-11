# Books Manager: Histórias de Usuário

> Histórias de usuário dos requisitos funcionais essenciais (prioridade M). Formato: **Como** *[papel]*, **quero** *[ação]*, **para** *[benefício]*. IDs entre parênteses referem `docs/requisitos.md`.

## Catálogo

**HU01 —** Como operador, quero cadastrar um livro com ISBN, título, editora, ano e categoria, para manter o acervo organizado.
- Critério de aceite: o ISBN deve ser único e válido (dígito verificador de ISBN-10 ou ISBN-13); um ISBN inválido ou repetido é recusado com mensagem clara (RN01).
- Critério de aceite: a categoria e os autores informados precisam existir e estar ativos (RF01, RF02).

**HU02 —** Como operador, quero cadastrar um exemplar de um livro com código de patrimônio único, para controlar cada cópia física separadamente.
- Critério de aceite: o código de patrimônio deve ser único; um código repetido é recusado (RN02).
- Critério de aceite: um exemplar não pode ser cadastrado para um livro inexistente (RF04).

**HU03 —** Como operador, quero pesquisar livros por título, autor, ISBN ou categoria, para descobrir rapidamente se há exemplar disponível.
- Critério de aceite: a busca aceita título, autor, ISBN e categoria, com paginação e ordenação (RF05).
- Critério de aceite: cada resultado mostra quantos exemplares estão disponíveis.

## Leitores e acesso

**HU04 —** Como operador, quero cadastrar um leitor com nome, documento, e-mail e telefone, para poder registrar empréstimos em nome dele.
- Critério de aceite: o documento é obrigatório e deve ser único por leitor (RF07).
- Critério de aceite: um leitor com histórico de empréstimos é inativado, nunca excluído fisicamente (RN14).

**HU05 —** Como usuário do sistema, quero entrar com login e senha, para acessar só as funções permitidas ao meu perfil.
- Critério de aceite: a senha é validada contra um hash forte (BCrypt), nunca armazenada em texto puro (RNF07).
- Critério de aceite: cada endpoint autoriza o acesso conforme o perfil, administrador ou operador (RF08, RNF08).

**HU06 —** Como operador, quero bloquear um leitor manualmente, para impedir novos empréstimos em casos excepcionais (ex.: perda recorrente de exemplares).
- Critério de aceite: um leitor bloqueado não pode fazer empréstimos nem reservas (RN11).
- Critério de aceite: o operador também pode desbloquear o leitor (RF10).

## Empréstimos

**HU07 —** Como operador, quero registrar o empréstimo de um exemplar a um leitor, para saber com quem cada livro está.
- Critério de aceite: só aparecem para empréstimo exemplares com status disponível; após registrar, o exemplar passa a emprestado e a data prevista de devolução respeita o prazo configurado (RN03, RN06).
- Critério de aceite: se o leitor tiver multa pendente, suspensão em vigor, empréstimo em atraso ou já estiver no limite de empréstimos, o sistema recusa o empréstimo e informa o motivo (RN04, RN05).

**HU08 —** Como operador, quero registrar a devolução de um exemplar, para que ele volte a ficar disponível e o atraso seja penalizado sem cálculo manual.
- Critério de aceite: ao devolver, o exemplar volta a disponível e o empréstimo é encerrado; um empréstimo já encerrado não pode ser devolvido de novo (RN09).
- Critério de aceite: se a devolução estiver atrasada, o sistema aplica a penalidade conforme a política configurada (multa, suspensão, ambas ou nenhuma) e mostra o resultado ao operador (RN08).

**HU09 —** Como operador, quero renovar um empréstimo, para dar mais prazo ao leitor sem precisar devolver e emprestar de novo.
- Critério de aceite: cada empréstimo pode ser renovado no máximo 2 vezes (RN07).
- Critério de aceite: a renovação é recusada se o empréstimo estiver em atraso ou se houver reserva pendente para o livro (RN07).

## Multas

**HU10 —** Como operador, quero registrar o pagamento ou o abono de uma multa, para manter o controle financeiro do leitor em dia.
- Critério de aceite: o abono exige justificativa registrada; sem ela, a operação é recusada (RF21).
- Critério de aceite: uma multa já paga ou abonada não pode ser paga ou abonada de novo.

**HU11 —** Como operador, quero consultar as multas pendentes e as suspensões em vigor de um leitor, para decidir se ele pode fazer um novo empréstimo.
- Critério de aceite: a consulta mostra o valor pendente e a data de término da suspensão, quando houver (RF22).

## Relatórios e administração

**HU12 —** Como operador, quero ver o relatório de empréstimos em atraso, ordenado do maior para o menor atraso, para priorizar a cobrança.
- Critério de aceite: a lista mostra o leitor, o exemplar e os dias de atraso, ordenados do maior para o menor (RF23).

**HU13 —** Como administrador, quero configurar a política de penalidade por atraso, para que a biblioteca escolha entre multa, suspensão, ambas ou nenhuma sem alterar o sistema.
- Critério de aceite: a política e seus valores (valor diário, dias de suspensão por dia de atraso e tetos) são editáveis pela tela de configuração, apenas pelo administrador (RF26).
- Critério de aceite: a mudança vale só para atrasos futuros; devoluções já registradas não são recalculadas (RN16).

## Referências

Requisitos e regras de negócio: `docs/requisitos.md`. Entidades envolvidas: `docs/diagramas.md`.
