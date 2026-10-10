package com.riverfount.booksmanager.catalogo.application.port.in;

import com.riverfount.booksmanager.catalogo.domain.Autor;
import java.util.Optional;

public interface ConsultarAutorUseCase {

    Optional<Autor> buscarPorId(Long id);
}
