package com.riverfount.booksmanager.catalogo.adapter.out.persistence;

import com.riverfount.booksmanager.catalogo.application.port.out.LivroRepository;
import com.riverfount.booksmanager.catalogo.domain.Isbn;
import com.riverfount.booksmanager.catalogo.domain.Livro;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class LivroPersistenceAdapter implements LivroRepository {

    private final LivroJpaRepository jpaRepository;

    LivroPersistenceAdapter(LivroJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Livro salvar(Livro livro) {
        var entidade = new LivroJpaEntity(livro.getId(), livro.getIsbn().valor(), livro.getTitulo(),
                livro.getEditora(), livro.getAnoPublicacao(), livro.getCategoriaId(), livro.getAutorIds(),
                livro.isAtivo());
        try {
            var salvo = jpaRepository.save(entidade);
            return paraDominio(salvo);
        } catch (DataIntegrityViolationException e) {
            // defesa contra a corrida entre o existeComIsbn() do futuro
            // serviço e este salvar(): a restrição única do banco é quem
            // garante RN01 de fato; aqui só traduzimos para a exceção do
            // domínio quando a violação for mesmo a do ISBN — outras
            // violações (FK de categoria_id ou autor_id inexistente, por
            // exemplo) não devem ser confundidas com ISBN duplicado.
            if (violaRestricaoUnicaDoIsbn(e)) {
                throw new RegraDeNegocioException("já existe um livro com o ISBN '" + livro.getIsbn().valor() + "'");
            }
            throw e;
        }
    }

    private static boolean violaRestricaoUnicaDoIsbn(DataIntegrityViolationException excecao) {
        var causaMaisEspecifica = excecao.getMostSpecificCause();
        var mensagem = causaMaisEspecifica == null ? null : causaMaisEspecifica.getMessage();
        return mensagem != null && mensagem.contains("livro_isbn_key");
    }

    @Override
    public Optional<Livro> buscarPorId(Long id) {
        return jpaRepository.findById(id).map(this::paraDominio);
    }

    @Override
    public boolean existeComIsbn(Isbn isbn) {
        return jpaRepository.existsByIsbn(isbn.valor());
    }

    private Livro paraDominio(LivroJpaEntity entidade) {
        return Livro.reconstituir(entidade.getId(), new Isbn(entidade.getIsbn()), entidade.getTitulo(),
                entidade.getEditora(), entidade.getAnoPublicacao(), entidade.getCategoriaId(),
                entidade.getAutorIds(), entidade.isAtivo());
    }
}
