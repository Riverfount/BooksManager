package com.riverfount.booksmanager.catalogo.application.service;

import com.riverfount.booksmanager.catalogo.application.port.out.AutorRepository;
import com.riverfount.booksmanager.catalogo.domain.Autor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementação em memória de AutorRepository, usada só nos testes
 * unitários dos serviços de aplicação (sem Spring, sem banco).
 */
class AutorRepositoryEmMemoria implements AutorRepository {

    private final Map<Long, Autor> autores = new HashMap<>();
    private final AtomicLong proximoId = new AtomicLong(1);

    @Override
    public Autor salvar(Autor autor) {
        var id = autor.getId() != null ? autor.getId() : proximoId.getAndIncrement();
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
