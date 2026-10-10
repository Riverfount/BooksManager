package com.riverfount.booksmanager.catalogo.application.port.in;

import com.riverfount.booksmanager.catalogo.domain.Livro;
import java.util.Optional;

public interface ConsultarLivroUseCase {

    Optional<Livro> buscarPorId(Long id);
}
