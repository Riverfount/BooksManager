package com.riverfount.booksmanager.catalogo.application.port.in;

import com.riverfount.booksmanager.catalogo.domain.Livro;

public interface CadastrarLivroUseCase {

    Livro cadastrar(CadastrarLivroCommand command);
}
