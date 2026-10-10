package com.riverfount.booksmanager.catalogo.application.port.in;

import com.riverfount.booksmanager.catalogo.domain.Autor;

public interface CadastrarAutorUseCase {

    Autor cadastrar(CadastrarAutorCommand command);
}
