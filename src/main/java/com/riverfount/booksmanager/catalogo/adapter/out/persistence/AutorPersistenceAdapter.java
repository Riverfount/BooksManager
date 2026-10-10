package com.riverfount.booksmanager.catalogo.adapter.out.persistence;

import com.riverfount.booksmanager.catalogo.application.port.out.AutorRepository;
import com.riverfount.booksmanager.catalogo.domain.Autor;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class AutorPersistenceAdapter implements AutorRepository {

    private final AutorJpaRepository jpaRepository;

    AutorPersistenceAdapter(AutorJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Autor salvar(Autor autor) {
        var entidade = new AutorJpaEntity(autor.getId(), autor.getNome(), autor.isAtivo());
        var salvo = jpaRepository.save(entidade);
        return paraDominio(salvo);
    }

    @Override
    public Optional<Autor> buscarPorId(Long id) {
        return jpaRepository.findById(id).map(this::paraDominio);
    }

    private Autor paraDominio(AutorJpaEntity entidade) {
        return Autor.reconstituir(entidade.getId(), entidade.getNome(), entidade.isAtivo());
    }
}
