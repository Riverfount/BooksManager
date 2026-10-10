package com.riverfount.booksmanager.catalogo.adapter.out.persistence;

import com.riverfount.booksmanager.catalogo.application.port.out.CategoriaRepository;
import com.riverfount.booksmanager.catalogo.domain.Categoria;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class CategoriaPersistenceAdapter implements CategoriaRepository {

    private final CategoriaJpaRepository jpaRepository;

    CategoriaPersistenceAdapter(CategoriaJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Categoria salvar(Categoria categoria) {
        var entidade = new CategoriaJpaEntity(categoria.getId(), categoria.getNome(), categoria.isAtivo());
        var salva = jpaRepository.save(entidade);
        return paraDominio(salva);
    }

    @Override
    public Optional<Categoria> buscarPorId(Long id) {
        return jpaRepository.findById(id).map(this::paraDominio);
    }

    @Override
    public boolean existeComNome(String nome) {
        return jpaRepository.existsByNome(nome);
    }

    private Categoria paraDominio(CategoriaJpaEntity entidade) {
        return Categoria.reconstituir(entidade.getId(), entidade.getNome(), entidade.isAtivo());
    }
}
