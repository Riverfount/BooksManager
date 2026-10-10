package com.riverfount.booksmanager.catalogo.application.service;

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarLivroCommand;
import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarLivroUseCase;
import com.riverfount.booksmanager.catalogo.application.port.in.ConsultarLivroUseCase;
import com.riverfount.booksmanager.catalogo.application.port.out.AutorRepository;
import com.riverfount.booksmanager.catalogo.application.port.out.CategoriaRepository;
import com.riverfount.booksmanager.catalogo.application.port.out.LivroRepository;
import com.riverfount.booksmanager.catalogo.domain.Isbn;
import com.riverfount.booksmanager.catalogo.domain.Livro;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra o cadastro e a consulta de Livro: busca o que o domínio
 * precisa para decidir (categoria e autores existem? ISBN já existe?),
 * chama o domínio e persiste. A decisão de negócio em si fica no
 * domínio (Livro.novo, Livro.validarIsbnUnico).
 */
@Service
@Transactional
class LivroService implements CadastrarLivroUseCase, ConsultarLivroUseCase {

    private final LivroRepository livroRepository;
    private final CategoriaRepository categoriaRepository;
    private final AutorRepository autorRepository;
    private final Clock relogio;

    LivroService(LivroRepository livroRepository, CategoriaRepository categoriaRepository,
            AutorRepository autorRepository, Clock relogio) {
        this.livroRepository = livroRepository;
        this.categoriaRepository = categoriaRepository;
        this.autorRepository = autorRepository;
        this.relogio = relogio;
    }

    @Override
    public Livro cadastrar(CadastrarLivroCommand command) {
        var isbn = new Isbn(command.isbn());
        // construído antes das checagens cruzadas: assim, categoriaId e
        // autorIds já estão garantidamente não nulos quando consultamos
        // os outros repositórios.
        var livro = Livro.novo(isbn, command.titulo(), command.editora(), command.anoPublicacao(),
                command.categoriaId(), command.autorIds(), relogio);
        validarCategoriaExiste(livro.getCategoriaId());
        validarAutoresExistem(livro.getAutorIds());
        Livro.validarIsbnUnico(isbn, livroRepository.existeComIsbn(isbn));
        return livroRepository.salvar(livro);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Livro> buscarPorId(Long id) {
        return livroRepository.buscarPorId(id);
    }

    private void validarCategoriaExiste(Long categoriaId) {
        if (categoriaRepository.buscarPorId(categoriaId).isEmpty()) {
            throw new RegraDeNegocioException("categoria não encontrada: " + categoriaId);
        }
    }

    private void validarAutoresExistem(Iterable<Long> autorIds) {
        for (var autorId : autorIds) {
            if (autorRepository.buscarPorId(autorId).isEmpty()) {
                throw new RegraDeNegocioException("autor não encontrado: " + autorId);
            }
        }
    }
}
