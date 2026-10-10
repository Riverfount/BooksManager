package com.riverfount.booksmanager.catalogo.application.service;

import com.riverfount.booksmanager.catalogo.application.port.out.CategoriaRepository;
import com.riverfount.booksmanager.catalogo.domain.Categoria;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementação em memória de CategoriaRepository, usada só nos testes
 * unitários dos serviços de aplicação (sem Spring, sem banco). Não
 * precisa ser thread-safe: é um fake de teste, usado de um só thread.
 */
class CategoriaRepositoryEmMemoria implements CategoriaRepository {

    private final Map<Long, Categoria> categorias = new HashMap<>();
    private long proximoId = 1;

    @Override
    public Categoria salvar(Categoria categoria) {
        var id = categoria.getId() != null ? categoria.getId() : proximoId++;
        var salva = Categoria.reconstituir(id, categoria.getNome(), categoria.isAtivo());
        categorias.put(id, salva);
        return salva;
    }

    @Override
    public Optional<Categoria> buscarPorId(Long id) {
        // mesma restrição do JpaRepository.findById real, para que este
        // fake não deixe passar um cenário que quebraria em produção
        if (id == null) {
            throw new IllegalArgumentException("o id não pode ser nulo");
        }
        return Optional.ofNullable(categorias.get(id));
    }

    @Override
    public boolean existeComNome(String nome) {
        return categorias.values().stream().anyMatch(categoria -> categoria.getNome().equals(nome));
    }
}
