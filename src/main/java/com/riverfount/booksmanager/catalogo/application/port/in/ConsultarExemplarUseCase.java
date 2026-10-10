package com.riverfount.booksmanager.catalogo.application.port.in;

import com.riverfount.booksmanager.catalogo.domain.Exemplar;
import java.util.Optional;

public interface ConsultarExemplarUseCase {

    Optional<Exemplar> buscarPorId(Long id);
}
