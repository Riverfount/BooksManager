package com.riverfount.booksmanager.catalogo.application.service;

import com.riverfount.booksmanager.catalogo.application.port.out.LivroRepository;
import com.riverfount.booksmanager.catalogo.domain.Isbn;
import com.riverfount.booksmanager.catalogo.domain.Livro;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implementação em memória de LivroRepository, usada só nos testes
 * unitários dos serviços de aplicação (sem Spring, sem banco). Não
 * precisa ser thread-safe: é um fake de teste, usado de um só thread.
 */
class LivroRepositoryEmMemoria implements LivroRepository {

    private final Map<Long, Livro> livros = new HashMap<>();
    private long proximoId = 1;

    @Override
    public Livro salvar(Livro livro) {
        var id = livro.getId() != null ? livro.getId() : proximoId++;
        var salvo = Livro.reconstituir(id, livro.getIsbn(), livro.getTitulo(), livro.getEditora(),
                livro.getAnoPublicacao(), livro.getCategoriaId(), livro.getAutorIds(), livro.isAtivo());
        livros.put(id, salvo);
        return salvo;
    }

    @Override
    public Optional<Livro> buscarPorId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("o id não pode ser nulo");
        }
        return Optional.ofNullable(livros.get(id));
    }

    @Override
    public boolean existeComIsbn(Isbn isbn) {
        return livros.values().stream().anyMatch(livro -> livro.getIsbn().equals(isbn));
    }
}
