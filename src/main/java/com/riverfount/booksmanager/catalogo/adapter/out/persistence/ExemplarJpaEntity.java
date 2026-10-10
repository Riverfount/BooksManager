package com.riverfount.booksmanager.catalogo.adapter.out.persistence;

import com.riverfount.booksmanager.catalogo.domain.StatusExemplar;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entidade JPA de Exemplar, mapeada para a tabela criada pela migração
 * V1__criar_catalogo.sql. O {@code @Version} em versao é o lock otimista
 * de verdade (RNF17); o domínio só transporta esse valor.
 */
@Entity
@Table(name = "exemplar")
class ExemplarJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "livro_id", nullable = false)
    private Long livroId;

    @Column(name = "codigo_patrimonio", nullable = false, unique = true)
    private String codigoPatrimonio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusExemplar status;

    @Column(nullable = false)
    private boolean ativo;

    @Version
    @Column(nullable = false)
    private Long versao;

    protected ExemplarJpaEntity() {
        // exigido pelo JPA
    }

    ExemplarJpaEntity(Long id, Long livroId, String codigoPatrimonio, StatusExemplar status, boolean ativo,
            Long versao) {
        this.id = id;
        this.livroId = livroId;
        this.codigoPatrimonio = codigoPatrimonio;
        this.status = status;
        this.ativo = ativo;
        this.versao = versao;
    }

    Long getId() {
        return id;
    }

    Long getLivroId() {
        return livroId;
    }

    String getCodigoPatrimonio() {
        return codigoPatrimonio;
    }

    StatusExemplar getStatus() {
        return status;
    }

    boolean isAtivo() {
        return ativo;
    }

    Long getVersao() {
        return versao;
    }
}
