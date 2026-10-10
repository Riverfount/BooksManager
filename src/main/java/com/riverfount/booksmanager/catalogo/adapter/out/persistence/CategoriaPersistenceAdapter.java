package com.riverfount.booksmanager.catalogo.adapter.out.persistence;

import com.riverfount.booksmanager.catalogo.application.port.out.CategoriaRepository;
import com.riverfount.booksmanager.catalogo.domain.Categoria;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
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
        try {
            var salva = jpaRepository.save(entidade);
            return paraDominio(salva);
        } catch (DataIntegrityViolationException e) {
            // defesa contra a corrida entre o existeComNome() do serviço e
            // este salvar(): a restrição única do banco é quem garante a
            // regra de fato; aqui só traduzimos para a exceção do domínio.
            throw new RegraDeNegocioException("já existe uma categoria com o nome '" + categoria.getNome() + "'");
        }
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
