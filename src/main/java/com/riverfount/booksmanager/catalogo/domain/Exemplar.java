package com.riverfount.booksmanager.catalogo.domain;

import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;

/**
 * Exemplar do acervo (RF04): a cópia física do livro, unidade do
 * empréstimo. Referencia o livro por identificador (livroId). A versão é
 * só transportada pelo domínio; o lock otimista de fato (RNF17) é do
 * adaptador de persistência, via {@code @Version}.
 */
public class Exemplar {

    private final Long id;
    private final Long livroId;
    private final String codigoPatrimonio;
    private StatusExemplar status;
    private boolean ativo;
    private final Long versao;

    private Exemplar(Long id, Long livroId, String codigoPatrimonio, StatusExemplar status, boolean ativo,
            Long versao) {
        this.id = id;
        this.livroId = livroId;
        this.codigoPatrimonio = codigoPatrimonio;
        this.status = status;
        this.ativo = ativo;
        this.versao = versao;
    }

    public static Exemplar novo(Long livroId, String codigoPatrimonio) {
        validarLivroId(livroId);
        validarCodigoPatrimonio(codigoPatrimonio);
        return new Exemplar(null, livroId, codigoPatrimonio, StatusExemplar.DISPONIVEL, true, 0L);
    }

    public static Exemplar reconstituir(Long id, Long livroId, String codigoPatrimonio, StatusExemplar status,
            boolean ativo, Long versao) {
        return new Exemplar(id, livroId, codigoPatrimonio, status, ativo, versao);
    }

    public void emprestar() {
        if (status != StatusExemplar.DISPONIVEL || !ativo) {
            throw new RegraDeNegocioException("o exemplar só pode ser emprestado se estiver disponível e ativo");
        }
        this.status = StatusExemplar.EMPRESTADO;
    }

    public void devolver() {
        if (status != StatusExemplar.EMPRESTADO) {
            throw new RegraDeNegocioException("o exemplar só pode ser devolvido se estiver emprestado");
        }
        this.status = StatusExemplar.DISPONIVEL;
    }

    public void marcarDanificado() {
        this.status = StatusExemplar.DANIFICADO;
    }

    public void marcarExtraviado() {
        this.status = StatusExemplar.EXTRAVIADO;
    }

    public void inativar() {
        if (status == StatusExemplar.EMPRESTADO) {
            throw new RegraDeNegocioException("o exemplar não pode ser inativado enquanto estiver emprestado");
        }
        this.ativo = false;
    }

    private static void validarLivroId(Long livroId) {
        if (livroId == null) {
            throw new RegraDeNegocioException("o id do livro é obrigatório");
        }
    }

    private static void validarCodigoPatrimonio(String codigoPatrimonio) {
        if (codigoPatrimonio == null || codigoPatrimonio.isBlank()) {
            throw new RegraDeNegocioException("o código de patrimônio é obrigatório");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getLivroId() {
        return livroId;
    }

    public String getCodigoPatrimonio() {
        return codigoPatrimonio;
    }

    public StatusExemplar getStatus() {
        return status;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Long getVersao() {
        return versao;
    }
}
