package com.riverfount.booksmanager.catalogo.application.port.in;

import com.riverfount.booksmanager.catalogo.domain.Exemplar;

public interface CadastrarExemplarUseCase {

    Exemplar cadastrar(CadastrarExemplarCommand command);
}
