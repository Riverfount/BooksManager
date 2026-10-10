package com.riverfount.booksmanager.catalogo.application.port.out;

import com.riverfount.booksmanager.catalogo.domain.Autor;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Porta de saída para persistência de Autor, na linguagem do domínio.
 */
public interface AutorRepository {

    Autor salvar(Autor autor);

    Optional<Autor> buscarPorId(Long id);

    List<Autor> buscarTodosPorIds(Set<Long> ids);
}
