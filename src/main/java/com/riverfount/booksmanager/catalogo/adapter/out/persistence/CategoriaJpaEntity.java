package com.riverfount.booksmanager.catalogo.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA de Categoria, mapeada para a tabela criada pela migração
 * V1__criar_catalogo.sql. Separada do domínio; a conversão é feita pelo
 * CategoriaPersistenceAdapter.
 */
@Entity
@Table(name = "categoria")
class CategoriaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nome;

    @Column(nullable = false)
    private boolean ativo;

    protected CategoriaJpaEntity() {
        // exigido pelo JPA
    }

    CategoriaJpaEntity(Long id, String nome, boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.ativo = ativo;
    }

    Long getId() {
        return id;
    }

    String getNome() {
        return nome;
    }

    boolean isAtivo() {
        return ativo;
    }
}
