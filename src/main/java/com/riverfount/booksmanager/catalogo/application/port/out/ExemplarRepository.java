package com.riverfount.booksmanager.catalogo.application.port.out;

import com.riverfount.booksmanager.catalogo.domain.Exemplar;
import java.util.Optional;

/**
 * Porta de saída para persistência de Exemplar, na linguagem do domínio.
 */
public interface ExemplarRepository {

    Exemplar salvar(Exemplar exemplar);

    Optional<Exemplar> buscarPorId(Long id);

    boolean existeCodigoPatrimonio(String codigoPatrimonio);
}
