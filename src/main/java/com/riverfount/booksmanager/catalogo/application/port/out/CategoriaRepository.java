package com.riverfount.booksmanager.catalogo.application.port.out;

import com.riverfount.booksmanager.catalogo.domain.Categoria;
import java.util.Optional;

/**
 * Porta de saída para persistência de Categoria, na linguagem do domínio.
 */
public interface CategoriaRepository {

    Categoria salvar(Categoria categoria);

    Optional<Categoria> buscarPorId(Long id);

    boolean existeComNome(String nome);
}
