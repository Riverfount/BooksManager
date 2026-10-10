package com.riverfount.booksmanager.catalogo.domain;

import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import java.time.Clock;
import java.time.Year;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Livro do acervo (RF01, RF02). Referencia a categoria e os autores por
 * identificador, não por objeto, mantendo o agregado pequeno. Livros com
 * histórico de empréstimos são inativados, nunca excluídos fisicamente
 * (RN14).
 */
public class Livro {

    public static final int TAMANHO_MAXIMO_TITULO = 255;

    private final Long id;
    private Isbn isbn;
    private String titulo;
    private String editora;
    private Integer anoPublicacao;
    private Long categoriaId;
    private final Set<Long> autorIds;
    private boolean ativo;

    private Livro(Long id, Isbn isbn, String titulo, String editora, Integer anoPublicacao,
            Long categoriaId, Set<Long> autorIds, boolean ativo) {
        this.id = id;
        this.isbn = isbn;
        this.titulo = titulo;
        this.editora = editora;
        this.anoPublicacao = anoPublicacao;
        this.categoriaId = categoriaId;
        this.autorIds = new HashSet<>(Objects.requireNonNull(autorIds, "autorIds não pode ser nulo"));
        this.ativo = ativo;
    }

    public static Livro novo(Isbn isbn, String titulo, String editora, Integer anoPublicacao,
            Long categoriaId, Set<Long> autorIds, Clock relogio) {
        validarIsbn(isbn);
        validarTitulo(titulo);
        validarCategoria(categoriaId);
        validarAutores(autorIds);
        validarAnoPublicacao(anoPublicacao, relogio);
        return new Livro(null, isbn, titulo, editora, anoPublicacao, categoriaId, autorIds, true);
    }

    public static Livro reconstituir(Long id, Isbn isbn, String titulo, String editora, Integer anoPublicacao,
            Long categoriaId, Set<Long> autorIds, boolean ativo) {
        return new Livro(id, isbn, titulo, editora, anoPublicacao, categoriaId, autorIds, ativo);
    }

    /**
     * Decide se o ISBN pode ser usado, dado se já existe outro livro com
     * ele. Quem busca essa informação (a aplicação, via o repositório)
     * não decide; só traz o fato para o domínio decidir.
     */
    public static void validarIsbnUnico(Isbn isbn, boolean jaExisteOutroComEsseIsbn) {
        if (jaExisteOutroComEsseIsbn) {
            throw new RegraDeNegocioException("já existe um livro com o ISBN '" + isbn.valor() + "'");
        }
    }

    public void atualizarDados(String titulo, String editora, Integer anoPublicacao, Long categoriaId,
            Clock relogio) {
        validarTitulo(titulo);
        validarCategoria(categoriaId);
        validarAnoPublicacao(anoPublicacao, relogio);
        this.titulo = titulo;
        this.editora = editora;
        this.anoPublicacao = anoPublicacao;
        this.categoriaId = categoriaId;
    }

    public void adicionarAutor(Long autorId) {
        if (autorId == null) {
            throw new RegraDeNegocioException("o id do autor é obrigatório");
        }
        autorIds.add(autorId);
    }

    public void removerAutor(Long autorId) {
        if (autorId == null) {
            throw new RegraDeNegocioException("o id do autor é obrigatório");
        }
        if (autorIds.size() == 1 && autorIds.contains(autorId)) {
            throw new RegraDeNegocioException("o livro precisa ter ao menos um autor");
        }
        autorIds.remove(autorId);
    }

    public void inativar() {
        this.ativo = false;
    }

    private static void validarIsbn(Isbn isbn) {
        if (isbn == null) {
            throw new RegraDeNegocioException("o ISBN do livro é obrigatório");
        }
    }

    private static void validarTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            throw new RegraDeNegocioException("o título do livro é obrigatório");
        }
        if (!titulo.equals(titulo.strip())) {
            throw new RegraDeNegocioException("o título do livro não pode ter espaços nas pontas");
        }
        if (titulo.length() > TAMANHO_MAXIMO_TITULO) {
            throw new RegraDeNegocioException(
                    "o título do livro deve ter até " + TAMANHO_MAXIMO_TITULO + " caracteres");
        }
    }

    private static void validarCategoria(Long categoriaId) {
        if (categoriaId == null) {
            throw new RegraDeNegocioException("a categoria do livro é obrigatória");
        }
    }

    private static void validarAutores(Set<Long> autorIds) {
        if (autorIds == null || autorIds.isEmpty()) {
            throw new RegraDeNegocioException("o livro precisa ter ao menos um autor");
        }
    }

    private static void validarAnoPublicacao(Integer anoPublicacao, Clock relogio) {
        if (anoPublicacao != null && anoPublicacao > Year.now(relogio).getValue()) {
            throw new RegraDeNegocioException("o ano de publicação não pode ser futuro");
        }
    }

    public Long getId() {
        return id;
    }

    public Isbn getIsbn() {
        return isbn;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getEditora() {
        return editora;
    }

    public Integer getAnoPublicacao() {
        return anoPublicacao;
    }

    public Long getCategoriaId() {
        return categoriaId;
    }

    public Set<Long> getAutorIds() {
        return Set.copyOf(autorIds);
    }

    public boolean isAtivo() {
        return ativo;
    }
}
