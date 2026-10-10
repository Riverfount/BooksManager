package com.riverfount.booksmanager.catalogo.application.port.in;

import com.riverfount.booksmanager.catalogo.domain.Categoria;
import java.util.Optional;

public interface ConsultarCategoriaUseCase {

    Optional<Categoria> buscarPorId(Long id);
}
