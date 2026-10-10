package com.riverfount.booksmanager.catalogo.application.service;

import com.riverfount.booksmanager.catalogo.application.port.out.CategoriaRepository;
import com.riverfount.booksmanager.catalogo.domain.Categoria;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementação em memória de CategoriaRepository, usada só nos testes
 * unitários dos serviços de aplicação (sem Spring, sem banco).
 */
class CategoriaRepositoryEmMemoria implements CategoriaRepository {

    private final Map<Long, Categoria> categorias = new HashMap<>();
    private final AtomicLong proximoId = new AtomicLong(1);

    @Override
    public Categoria salvar(Categoria categoria) {
        var id = categoria.getId() != null ? categoria.getId() : proximoId.getAndIncrement();
        var salva = Categoria.reconstituir(id, categoria.getNome(), categoria.isAtivo());
        categorias.put(id, salva);
        return salva;
    }

    @Override
    public Optional<Categoria> buscarPorId(Long id) {
        return Optional.ofNullable(categorias.get(id));
    }

    @Override
    public boolean existeComNome(String nome) {
        return categorias.values().stream().anyMatch(categoria -> categoria.getNome().equals(nome));
    }
}
