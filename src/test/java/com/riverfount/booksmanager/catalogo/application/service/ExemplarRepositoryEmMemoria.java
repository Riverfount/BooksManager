package com.riverfount.booksmanager.catalogo.application.service;

import com.riverfount.booksmanager.catalogo.application.port.out.ExemplarRepository;
import com.riverfount.booksmanager.catalogo.domain.Exemplar;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementação em memória de ExemplarRepository, usada só nos testes
 * unitários dos serviços de aplicação (sem Spring, sem banco). Não
 * precisa ser thread-safe: é um fake de teste, usado de um só thread.
 */
class ExemplarRepositoryEmMemoria implements ExemplarRepository {

    private final Map<Long, Exemplar> exemplares = new HashMap<>();
    private long proximoId = 1;

    @Override
    public Exemplar salvar(Exemplar exemplar) {
        var id = exemplar.getId() != null ? exemplar.getId() : proximoId++;
        var salvo = Exemplar.reconstituir(id, exemplar.getLivroId(), exemplar.getCodigoPatrimonio(),
                exemplar.getStatus(), exemplar.isAtivo(), exemplar.getVersao());
        exemplares.put(id, salvo);
        return salvo;
    }

    @Override
    public Optional<Exemplar> buscarPorId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("o id não pode ser nulo");
        }
        return Optional.ofNullable(exemplares.get(id));
    }

    @Override
    public boolean existeCodigoPatrimonio(String codigoPatrimonio) {
        return exemplares.values().stream()
                .anyMatch(exemplar -> exemplar.getCodigoPatrimonio().equals(codigoPatrimonio));
    }
}
