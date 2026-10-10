package com.riverfount.booksmanager.catalogo.application.port.out;

import com.riverfount.booksmanager.catalogo.domain.Isbn;
import com.riverfount.booksmanager.catalogo.domain.Livro;
import java.util.Optional;

/**
 * Porta de saída para persistência de Livro, na linguagem do domínio.
 */
public interface LivroRepository {

    Livro salvar(Livro livro);

    Optional<Livro> buscarPorId(Long id);

    boolean existeComIsbn(Isbn isbn);
}
