package com.riverfount.booksmanager.catalogo.application.port.in;

public record CadastrarExemplarCommand(Long livroId, String codigoPatrimonio) {
}
