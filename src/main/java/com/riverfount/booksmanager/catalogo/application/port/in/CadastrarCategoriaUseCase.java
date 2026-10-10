package com.riverfount.booksmanager.catalogo.application.port.in;

import com.riverfount.booksmanager.catalogo.domain.Categoria;

public interface CadastrarCategoriaUseCase {

    Categoria cadastrar(CadastrarCategoriaCommand command);
}
