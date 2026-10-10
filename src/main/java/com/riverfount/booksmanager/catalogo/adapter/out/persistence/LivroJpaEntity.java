package com.riverfount.booksmanager.catalogo.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

/**
 * Entidade JPA de Livro, mapeada para a tabela criada pela migração
 * V1__criar_catalogo.sql. O domínio guarda só os IDs dos autores, então
 * autorIds é mapeado como coleção de valores na tabela livro_autor, não
 * como uma relação para AutorJpaEntity. O ISBN é guardado como String; a
 * conversão de/para Isbn é feita pelo LivroPersistenceAdapter.
 */
@Entity
@Table(name = "livro")
class LivroJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 13)
    private String isbn;

    @Column(nullable = false)
    private String titulo;

    private String editora;

    @Column(name = "ano_publicacao")
    private Integer anoPublicacao;

    @Column(name = "categoria_id", nullable = false)
    private Long categoriaId;

    @ElementCollection
    @CollectionTable(name = "livro_autor", joinColumns = @JoinColumn(name = "livro_id"))
    @Column(name = "autor_id")
    private Set<Long> autorIds = new HashSet<>();

    @Column(nullable = false)
    private boolean ativo;

    protected LivroJpaEntity() {
        // exigido pelo JPA
    }

    LivroJpaEntity(Long id, String isbn, String titulo, String editora, Integer anoPublicacao, Long categoriaId,
            Set<Long> autorIds, boolean ativo) {
        this.id = id;
        this.isbn = isbn;
        this.titulo = titulo;
        this.editora = editora;
        this.anoPublicacao = anoPublicacao;
        this.categoriaId = categoriaId;
        this.autorIds = new HashSet<>(autorIds);
        this.ativo = ativo;
    }

    Long getId() {
        return id;
    }

    String getIsbn() {
        return isbn;
    }

    String getTitulo() {
        return titulo;
    }

    String getEditora() {
        return editora;
    }

    Integer getAnoPublicacao() {
        return anoPublicacao;
    }

    Long getCategoriaId() {
        return categoriaId;
    }

    Set<Long> getAutorIds() {
        return autorIds;
    }

    boolean isAtivo() {
        return ativo;
    }
}
