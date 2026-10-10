package com.riverfount.booksmanager.catalogo.application.service;

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarLivroCommand;
import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarLivroUseCase;
import com.riverfount.booksmanager.catalogo.application.port.in.ConsultarLivroUseCase;
import com.riverfount.booksmanager.catalogo.application.port.out.AutorRepository;
import com.riverfount.booksmanager.catalogo.application.port.out.CategoriaRepository;
import com.riverfount.booksmanager.catalogo.application.port.out.LivroRepository;
import com.riverfount.booksmanager.catalogo.domain.Autor;
import com.riverfount.booksmanager.catalogo.domain.Isbn;
import com.riverfount.booksmanager.catalogo.domain.Livro;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
        var categoria = categoriaRepository.buscarPorId(categoriaId);
        if (categoria.isEmpty()) {
            throw new RegraDeNegocioException("categoria não encontrada: " + categoriaId);
        }
        if (!categoria.get().isAtivo()) {
            throw new RegraDeNegocioException("categoria inativa: " + categoriaId);
        }
    }

    private void validarAutoresExistem(Set<Long> autorIds) {
        // uma única consulta em lote, em vez de uma por autorId
        var encontrados = autorRepository.buscarTodosPorIds(autorIds);
        var idsEncontrados = encontrados.stream().map(Autor::getId).collect(Collectors.toSet());
        for (var autorId : autorIds) {
            if (!idsEncontrados.contains(autorId)) {
                throw new RegraDeNegocioException("autor não encontrado: " + autorId);
            }
        }
        for (var autor : encontrados) {
            if (!autor.isAtivo()) {
                throw new RegraDeNegocioException("autor inativo: " + autor.getId());
            }
        }
    }
}
