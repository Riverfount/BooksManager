# Books Manager: Histórias de Usuário

> Histórias de usuário dos requisitos funcionais essenciais (prioridade M) e, a partir da Fase 4, dos itens S (reservas, perda ou dano na devolução e relatórios adicionais) e, na Fase 5, da exportação dos relatórios e da trilha de auditoria. Formato: **Como** *[papel]*, **quero** *[ação]*, **para** *[benefício]*. IDs entre parênteses referem `docs/requisitos.md`.
>
> Os IDs das histórias são estáveis e não seguem a ordem de leitura: histórias novas recebem o próximo número livre e entram na seção do seu módulo, sem renumerar as existentes (que já são citadas em issues e commits).

## Catálogo

**HU01 —** Como operador, quero cadastrar um livro com ISBN, título, editora, ano e categoria, para manter o acervo organizado.
- Critério de aceite: o ISBN deve ser único e válido (dígito verificador de ISBN-10 ou ISBN-13); um ISBN inválido ou repetido é recusado com mensagem clara (RN01). O ISBN-10 e o ISBN-13 do mesmo livro contam como o mesmo ISBN: o livro é guardado com o ISBN-13.
- Critério de aceite: a editora (até 255 caracteres) e o ano de publicação (de 1450 até o ano atual) são obrigatórios no cadastro (RF01).
- Critério de aceite: ao editar, a editora e o ano podem ser trocados por outro valor válido, mas não esvaziados; um livro de antes da exigência, sem os dois, continua editável e pode ser completado (RF01).
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
- Critério de aceite: enquanto o livro está inativo, nenhum exemplar dele é emprestado, o livro não é reservado e os empréstimos em curso não são renovados; a devolução de um empréstimo em curso continua funcionando (RN14).
- Critério de aceite: um livro inativo não aceita novos exemplares nem aparece nas listagens padrão (RF01).

**HU16 —** Como operador, quero cadastrar e editar autores, para associá-los aos livros do acervo.
- Critério de aceite: o nome do autor é obrigatório (RF02).
- Critério de aceite: cadastrar ou renomear um autor para o nome de outro autor ativo é recusado, e a resposta informa quem já existe; o operador pode confirmar que é um homônimo e repetir (RN19).
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
- Critério de aceite: um exemplar de livro inativo não é emprestado, e o sistema informa "o livro está inativo" (RN14).

**HU08 —** Como operador, quero registrar a devolução de um exemplar, para que ele volte a ficar disponível e o atraso seja penalizado sem cálculo manual.
- Critério de aceite: ao devolver, o exemplar volta a disponível e o empréstimo é encerrado; um empréstimo já encerrado não pode ser devolvido de novo (RN09).
- Critério de aceite: se a devolução estiver atrasada, o sistema aplica a penalidade conforme a política configurada (multa, suspensão, ambas ou nenhuma) e mostra o resultado ao operador (RN08).
- Critério de aceite: o atraso conta em dias corridos, sem carência; devolver no dia do vencimento não é atraso. A multa e a suspensão crescem por dia de atraso, até o teto, com a política vigente no dia da devolução (RN08, RN16).

**HU09 —** Como operador, quero renovar um empréstimo, para dar mais prazo ao leitor sem precisar devolver e emprestar de novo.
- Critério de aceite: cada empréstimo pode ser renovado no máximo 2 vezes, valor configurável (RN07).
- Critério de aceite: a renovação é recusada se o empréstimo estiver em atraso ou se houver reserva pendente para o livro (RN07).
- Critério de aceite: a renovação é recusada se o livro foi inativado; o leitor ainda pode devolver o exemplar (RN14).

**HU30 —** Como operador, quero consultar os empréstimos ativos e o histórico de um leitor ou de um exemplar, para responder dúvidas e acompanhar a circulação do acervo.
- Critério de aceite: a consulta filtra por leitor ou por exemplar e separa os empréstimos ativos dos encerrados (RF14).
- Critério de aceite: cada empréstimo mostra as datas de retirada, prevista e efetiva de devolução, e o resultado da penalidade, quando houver (RF14, RN08).
- Critério de aceite: a listagem é paginada, com no máximo 100 itens por página (RNF14).

**HU31 —** Como operador, quero registrar na devolução que o exemplar voltou danificado ou não voltou, para que ele não seja emprestado de novo enquanto não for conferido.
- Critério de aceite: a devolução aceita, de forma opcional, a condição do exemplar: `GOOD` (padrão quando não informada), `DAMAGED` ou `LOST`; a condição fica guardada no empréstimo (RF15).
- Critério de aceite: com a condição `DAMAGED` ou `LOST`, o exemplar passa a danificado ou extraviado, nunca volta a disponível e não entra na fila de reservas (RF15, RF18).
- Critério de aceite: só se registra a ocorrência, sem cobrança; a multa por atraso, se houver, segue as regras de sempre e é independente da condição (RF15, RN08).

## Multas

**HU10 —** Como operador, quero registrar o pagamento ou o abono de uma multa, para manter o controle financeiro do leitor em dia.
- Critério de aceite: o abono exige justificativa registrada; sem ela, a operação é recusada (RF21).
- Critério de aceite: uma multa já paga ou abonada não pode ser paga ou abonada de novo.

**HU11 —** Como operador, quero consultar as multas pendentes e as suspensões em vigor de um leitor, para decidir se ele pode fazer um novo empréstimo.
- Critério de aceite: a consulta mostra o valor pendente e a data de término da suspensão, quando houver (RF22).

## Reservas

**HU32 —** Como operador, quero reservar um livro sem exemplar disponível para um leitor, para que ele entre na fila de espera.
- Critério de aceite: só reserva quem poderia emprestar: leitor ativo, não bloqueado, sem suspensão em vigor, sem multa pendente e sem empréstimo em atraso; a disponibilidade do exemplar e o limite de empréstimos não entram nessa conta (RF16, RN05, RN11).
- Critério de aceite: o leitor não reserva um livro que já tem emprestado nem um que tenha exemplar disponível (RN12), nem um livro inativo (RN14).
- Critério de aceite: o leitor não reserva o mesmo livro duas vezes; o total de reservas ativas dele é limitado pelo mesmo valor do limite de empréstimos da configuração e contado à parte dos empréstimos (RF16, RN04).
- Critério de aceite: a fila de cada livro respeita a ordem das reservas (RF16).

**HU33 —** Como operador, quero cancelar uma reserva, para liberar a vez do leitor ou o exemplar que estava guardado para ele.
- Critério de aceite: o cancelamento é único (`POST /api/v1/reservations/{id}/cancel`), feito por administrador ou operador, sem justificativa; o leitor pede ao operador (RF17).
- Critério de aceite: cancelar a reserva que segura um exemplar passa o exemplar ao próximo da fila ou o devolve a disponível (RF17, RF18).

**HU34 —** Como operador, quero que o exemplar devolvido seja reservado ao primeiro da fila de espera, e que ele possa retirá-lo no prazo, para que a fila seja respeitada.
- Critério de aceite: ao devolver um exemplar em boas condições com fila de espera, ele fica reservado ao primeiro da fila e a reserva passa a ter o prazo de retirada da configuração, contado da data em que o exemplar ficou disponível para o leitor (RF18, RN13).
- Critério de aceite: o `POST /api/v1/loans` aceita o exemplar reservado quando o leitor é o dono da reserva e ainda está no prazo (o último dia conta); o exemplar passa a emprestado e a reserva a atendida na mesma operação (RN03).
- Critério de aceite: outro leitor que tente retirar o exemplar reservado é recusado com a mensagem "exemplar reservado para outro leitor" (RN03).
- Critério de aceite: se o livro foi inativado, nem o dono da reserva retira o exemplar ("o livro está inativo", RN14), e o exemplar devolvido de um livro inativo não é guardado para a fila.
- Critério de aceite: a renovação de um empréstimo só é recusada pela reserva quando há alguém esperando na fila do livro; a reserva que já segura um exemplar não bloqueia (RN07).

**HU38 —** Como operador, quero consultar as reservas e a fila de espera de um livro, para informar ao leitor em que lugar ele está e até quando pode retirar o exemplar.
- Critério de aceite: posso consultar uma reserva (também a já encerrada) e listar as reservas por leitor, por livro e por situação, de forma paginada (RF16, RNF14).
- Critério de aceite: cada reserva mostra a situação, o lugar na fila (quando espera, pela ordem de chegada) e o exemplar e o prazo de retirada (quando segura um) (RF16, RN13).

**HU35 —** Como operador, quero que a reserva não retirada no prazo expire sozinha e passe a vez ao próximo, para que o exemplar não fique parado.
- Critério de aceite: uma rotina agendada, às 03:00 no fuso `America/Sao_Paulo` e também na subida da aplicação, expira as reservas cujo prazo de retirada terminou (RF19, RN13).
- Critério de aceite: ao expirar, o exemplar passa ao próximo da fila ou volta a disponível; rodar a rotina de novo não muda nada (RF19).

## Relatórios e administração

**HU12 —** Como operador, quero ver o relatório de empréstimos em atraso, ordenado do maior para o menor atraso, para priorizar a cobrança.
- Critério de aceite: a lista mostra o leitor, o exemplar e os dias de atraso, ordenados do maior para o menor (RF23).

**HU13 —** Como administrador, quero configurar a política de penalidade por atraso, para que a biblioteca escolha entre multa, suspensão, ambas ou nenhuma sem alterar o sistema.
- Critério de aceite: a política e seus valores (valor diário, dias de suspensão por dia de atraso e tetos) são editáveis pela tela de configuração, apenas pelo administrador (RF26).
- Critério de aceite: a mudança vale só para atrasos futuros; devoluções já registradas não são recalculadas (RN16).

**HU36 —** Como administrador ou operador, quero ver os livros mais emprestados em um período, para decidir o que comprar e o que destacar no acervo.
- Critério de aceite: o relatório recebe o período (obrigatório, com as duas datas incluídas e no máximo 366 dias) e lista os livros ordenados do mais para o menos emprestado, com a quantidade de empréstimos; a renovação não conta como novo empréstimo (RF24).
- Critério de aceite: a listagem é paginada, com no máximo 100 itens por página (RNF14).

**HU37 —** Como administrador ou operador, quero ver a situação do acervo, para saber quantos exemplares há e em que estado estão.
- Critério de aceite: o relatório mostra o total de exemplares ativos e quantos há em cada status (disponível, emprestado, reservado, danificado e extraviado; um status sem exemplares aparece com zero), e os exemplares inativos à parte (RF25, RN14).

**HU39 —** Como administrador, quero exportar os relatórios de empréstimos em atraso, de livros mais emprestados e da situação do acervo em CSV e em PDF, para abrir numa planilha, imprimir ou guardar.
- Critério de aceite: cada relatório sai em CSV (UTF-8 com BOM, separado por `;`, com acentos corretos numa planilha em português) e em PDF (com a data e a hora da emissão, os filtros usados e o cabeçalho repetido em cada página), com as mesmas linhas e a mesma ordem do relatório na tela, até 10.000 linhas; texto que começa com `=`, `+`, `-` ou `@` não vira fórmula no CSV; o operador não exporta (403), mas continua vendo os relatórios na tela (RF29, RN17).

**HU40 —** Como administrador, quero consultar a trilha de auditoria das operações sensíveis, para responder a "quem abonou esta multa" ou "quem mudou a política de penalidade".
- Critério de aceite: cada operação sensível (login com sucesso, falha e bloqueio; criar, editar, inativar e desbloquear usuário e redefinir senha; bloquear, desbloquear e inativar leitor; pagar e abonar multa; encerrar suspensão antes do prazo; alterar a configuração da biblioteca) deixa um registro com quem fez, quando, o quê e em quê; a consulta é paginada, do mais recente para o mais antigo, com filtros por usuário, ação, alvo e período; uma operação desfeita não deixa registro; nenhum registro traz senha, hash ou token, e o login digitado de quem não é usuário nunca é guardado; só o administrador consulta, o operador recebe 403 (RF27, RN18).

**HU41 —** Como administrador, quero reativar um livro, autor, categoria ou exemplar que foi inativado, para desfazer uma inativação feita por engano ou voltar a usar o registro com o histórico.
- Critério de aceite: só o administrador reativa; o operador recebe 403 (RNF08, RN14).
- Critério de aceite: o livro só é reativado se a categoria e todos os autores dele estão ativos; a recusa diz quais estão inativos (RN14).
- Critério de aceite: o exemplar só é reativado se o livro dele está ativo, e volta com o status que tinha; reativar o livro não reativa os exemplares (RN14).
- Critério de aceite: o registro reativado volta às listagens padrão e, no caso do livro, pode ser emprestado e reservado de novo (RN14); reativar o que já está ativo não tem efeito.
- Critério de aceite: o autor só é reativado se nenhum outro autor ativo tem o mesmo nome; a recusa informa quem já existe, e o administrador pode confirmar que é um homônimo e repetir (RN19).

## Referências

Requisitos e regras de negócio: `docs/requisitos.md`. Entidades envolvidas: `docs/diagramas.md`.
