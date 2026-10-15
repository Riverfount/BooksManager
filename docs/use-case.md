# Books Manager: Histórias de Usuário

> Histórias de usuário dos requisitos funcionais essenciais (prioridade M). Formato: **Como** *[papel]*, **quero** *[ação]*, **para** *[benefício]*. IDs entre parênteses referem `docs/requisitos.md`.
>
> Os IDs das histórias são estáveis e não seguem a ordem de leitura: histórias novas recebem o próximo número livre e entram na seção do seu módulo, sem renumerar as existentes (que já são citadas em issues e commits).

## Catálogo

**HU01 —** Como operador, quero cadastrar um livro com ISBN, título, editora, ano e categoria, para manter o acervo organizado.
- Critério de aceite: o ISBN deve ser único e válido (dígito verificador de ISBN-10 ou ISBN-13); um ISBN inválido ou repetido é recusado com mensagem clara (RN01).
- Critério de aceite: a categoria e os autores informados precisam existir e estar ativos (RF01, RF02).

**HU02 —** Como operador, quero cadastrar um exemplar de um livro com código de patrimônio único, para controlar cada cópia física separadamente.
- Critério de aceite: o código de patrimônio deve ser único; um código repetido é recusado (RN02).
- Critério de aceite: um exemplar não pode ser cadastrado para um livro inexistente (RF04).

**HU22 —** Como operador, quero listar e consultar os exemplares, para saber quantas cópias de cada livro existem e em que situação estão.
- Critério de aceite: a listagem é paginada, com no máximo 100 itens por página, e permite filtrar por livro e por status (RF04, RNF14).
- Critério de aceite: a consulta de um exemplar mostra o livro, o código de patrimônio e o status atual (RF04).

**HU23 —** Como operador, quero marcar um exemplar como danificado ou extraviado, para que ele não seja oferecido para empréstimo.
- Critério de aceite: posso marcar manualmente um exemplar como danificado ou extraviado; os status emprestado e reservado só mudam pelos fluxos de empréstimo, devolução e reserva (RF04).
- Critério de aceite: um exemplar danificado ou extraviado pode voltar a disponível manualmente, quando é consertado ou encontrado (RF04).
- Critério de aceite: um exemplar emprestado não pode ter o status alterado manualmente; a perda ou o dano dele é registrado na devolução (RF15).

**HU24 —** Como operador, quero inativar um exemplar que saiu do acervo, sem perder o histórico de empréstimos dele.
- Critério de aceite: o exemplar é inativado, nunca excluído fisicamente (RN14).
- Critério de aceite: um exemplar com empréstimo ativo não pode ser inativado (RN10).

**HU03 —** Como operador, quero pesquisar livros por título, autor, ISBN ou categoria, para descobrir rapidamente se há exemplar disponível.
- Critério de aceite: a busca aceita título, autor, ISBN e categoria, com paginação e ordenação (RF05). Título, autor e ISBN casam por trecho, sem diferenciar maiúsculas, e os filtros informados valem em conjunto.
- Critério de aceite: cada resultado mostra quantos exemplares estão disponíveis.

**HU14 —** Como operador, quero editar os dados de um livro, para corrigir erros de cadastro e manter os autores e a categoria atualizados.
- Critério de aceite: posso alterar título, editora, ano, categoria e autores; só os campos informados são alterados (RF01).
- Critério de aceite: o ISBN não pode ser alterado depois do cadastro (RF01, RN01).
- Critério de aceite: a categoria e os autores informados precisam existir e estar ativos (RF01, RF02).

**HU15 —** Como operador, quero inativar um livro que saiu do acervo, para que ele deixe de aparecer nas operações do dia a dia sem perder o histórico.
- Critério de aceite: o livro é inativado, nunca excluído fisicamente, e continua visível no histórico de empréstimos (RN14).
- Critério de aceite: um livro inativo não aceita novos exemplares nem aparece nas listagens padrão (RF01).

**HU16 —** Como operador, quero cadastrar e editar autores, para associá-los aos livros do acervo.
- Critério de aceite: o nome do autor é obrigatório (RF02).
- Critério de aceite: a edição altera apenas os dados do autor; os livros associados continuam associados (RF02).

**HU17 —** Como operador, quero listar e consultar os autores, para encontrar o autor certo ao cadastrar um livro.
- Critério de aceite: a listagem é paginada, com no máximo 100 itens por página, permite filtrar por nome e mostra só os autores ativos por padrão (RF02, RNF14).
- Critério de aceite: a consulta de um autor inexistente informa que ele não foi encontrado.

**HU18 —** Como operador, quero inativar um autor, para que ele não seja mais associado a novos livros.
- Critério de aceite: o autor é inativado, nunca excluído fisicamente, e os livros já cadastrados mantêm a associação (RN14).
- Critério de aceite: um autor inativo não pode ser associado a livros novos ou editados (RF01, RF02).

**HU19 —** Como operador, quero cadastrar e editar categorias, para classificar os livros do acervo.
- Critério de aceite: o nome da categoria é obrigatório e único; um nome repetido é recusado com mensagem clara (RF03).
- Critério de aceite: renomear uma categoria para o próprio nome atual não é considerado repetição.

**HU20 —** Como operador, quero listar e consultar as categorias, para escolher a categoria ao cadastrar ou editar um livro.
- Critério de aceite: a listagem é paginada, com no máximo 100 itens por página, permite filtrar por nome e mostra só as categorias ativas por padrão (RF03, RNF14).

**HU21 —** Como operador, quero inativar uma categoria que não é mais usada, sem perder a classificação dos livros antigos.
- Critério de aceite: a categoria é inativada, nunca excluída fisicamente, e os livros já cadastrados continuam com ela (RN14).
- Critério de aceite: uma categoria inativa não pode ser usada em livros novos ou editados (RF01, RF03).

## Leitores e acesso

**HU04 —** Como operador, quero cadastrar um leitor com nome, documento, e-mail, telefone e data de nascimento, para poder registrar empréstimos em nome dele.
- Critério de aceite: o documento é obrigatório e deve ser único por leitor (RF07).
- Critério de aceite: o e-mail e a data de nascimento são obrigatórios; o telefone é opcional (RF07, RNF11).
- Critério de aceite: para leitor menor de 18 anos, o nome e o e-mail do responsável são obrigatórios e o telefone dele é opcional (RF07, RNF11).
- Critério de aceite: um leitor com histórico de empréstimos é inativado, nunca excluído fisicamente (RN14).

**HU25 —** Como operador, quero listar e consultar os leitores, para encontrar rapidamente quem vai fazer um empréstimo.
- Critério de aceite: a listagem é paginada, com no máximo 100 itens por página, permite buscar por nome ou documento e mostra só os leitores ativos por padrão (RF07, RNF14).
- Critério de aceite: a consulta mostra a situação do leitor (ativo, inativo ou bloqueado) (RF07, RF10).

**HU26 —** Como operador, quero editar os dados de um leitor, para manter o contato dele atualizado.
- Critério de aceite: posso alterar nome, documento, e-mail, telefone, data de nascimento e dados do responsável; o documento continua único por leitor, e o menor de 18 anos continua com o responsável informado (RF07).
- Critério de aceite: não são coletados dados pessoais além de nome, documento, contato, data de nascimento e, para menores, o responsável (RNF11).

**HU27 —** Como operador, quero inativar um leitor que não frequenta mais a biblioteca, sem perder o histórico dele.
- Critério de aceite: o leitor é inativado, nunca excluído fisicamente (RN14).
- Critério de aceite: um leitor inativo não pode fazer empréstimos nem reservas (RN11).

**HU05 —** Como usuário do sistema, quero entrar com login e senha, para acessar só as funções permitidas ao meu perfil.
- Critério de aceite: a senha é validada contra um hash forte (BCrypt), nunca armazenada em texto puro (RNF07).
- Critério de aceite: cada endpoint autoriza o acesso conforme o perfil, administrador ou operador (RF08, RNF08).

**HU06 —** Como operador, quero bloquear um leitor manualmente, para impedir novos empréstimos em casos excepcionais (ex.: perda recorrente de exemplares).
- Critério de aceite: um leitor bloqueado não pode fazer empréstimos nem reservas (RN11).
- Critério de aceite: o operador também pode desbloquear o leitor (RF10).

**HU28 —** Como administrador, quero criar, listar, consultar, editar e inativar os usuários do sistema, para controlar quem opera a biblioteca e com qual perfil.
- Critério de aceite: só o administrador acessa a gestão de usuários (RF09, RNF08).
- Critério de aceite: cada usuário tem um perfil, administrador ou operador; um usuário inativo não consegue entrar no sistema (RF08, RF09).
- Critério de aceite: a listagem é paginada, com no máximo 100 itens por página (RNF14).

**HU29 —** Como administrador, quero redefinir a senha de um usuário, para devolver o acesso a quem a esqueceu.
- Critério de aceite: a nova senha é armazenada com hash forte (BCrypt), nunca em texto puro (RF09, RNF07).

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

**HU30 —** Como operador, quero consultar os empréstimos ativos e o histórico de um leitor ou de um exemplar, para responder dúvidas e acompanhar a circulação do acervo.
- Critério de aceite: a consulta filtra por leitor ou por exemplar e separa os empréstimos ativos dos encerrados (RF14).
- Critério de aceite: cada empréstimo mostra as datas de retirada, prevista e efetiva de devolução, e o resultado da penalidade, quando houver (RF14, RN08).
- Critério de aceite: a listagem é paginada, com no máximo 100 itens por página (RNF14).

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
