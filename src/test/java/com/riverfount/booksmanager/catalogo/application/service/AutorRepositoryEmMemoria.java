package com.riverfount.booksmanager.catalogo.application.service;

import com.riverfount.booksmanager.catalogo.application.port.out.AutorRepository;
import com.riverfount.booksmanager.catalogo.domain.Autor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementação em memória de AutorRepository, usada só nos testes
 * unitários dos serviços de aplicação (sem Spring, sem banco). Não
 * precisa ser thread-safe: é um fake de teste, usado de um só thread.
 */
class AutorRepositoryEmMemoria implements AutorRepository {

    private final Map<Long, Autor> autores = new HashMap<>();
    private long proximoId = 1;

    @Override
    public Autor salvar(Autor autor) {
        var id = autor.getId() != null ? autor.getId() : proximoId++;
        var salvo = Autor.reconstituir(id, autor.getNome(), autor.isAtivo());
        autores.put(id, salvo);
        return salvo;
    }

    @Override
    public Optional<Autor> buscarPorId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("o id não pode ser nulo");
        }
        return Optional.ofNullable(autores.get(id));
    }
}
