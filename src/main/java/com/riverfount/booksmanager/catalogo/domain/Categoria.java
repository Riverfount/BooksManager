package com.riverfount.booksmanager.catalogo.domain;

import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;

/**
 * Categoria do acervo (RF03). Livros com histórico de empréstimos são
 * inativados, nunca excluídos fisicamente (RN14).
 */
public class Categoria {

    private static final int TAMANHO_MAXIMO_NOME = 100;

    private final Long id;
    private String nome;
    private boolean ativo;

    private Categoria(Long id, String nome, boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.ativo = ativo;
    }

    public static Categoria novo(String nome) {
        validarNome(nome);
        return new Categoria(null, nome, true);
    }

    public static Categoria reconstituir(Long id, String nome, boolean ativo) {
        return new Categoria(id, nome, ativo);
    }

    /**
     * Decide se o nome pode ser usado, dado se já existe outra categoria com
     * ele. Quem busca essa informação (a aplicação, via o repositório) não
     * decide; só traz o fato para o domínio decidir.
     */
    public static void validarNomeUnico(String nome, boolean jaExisteOutraComEsseNome) {
        if (jaExisteOutraComEsseNome) {
            throw new RegraDeNegocioException("já existe uma categoria com o nome '" + nome + "'");
        }
    }

    public void renomear(String novoNome) {
        validarNome(novoNome);
        this.nome = novoNome;
    }

    public void inativar() {
        this.ativo = false;
    }

    private static void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("o nome da categoria é obrigatório");
        }
        if (!nome.equals(nome.strip())) {
            throw new RegraDeNegocioException("o nome da categoria não pode ter espaços nas pontas");
        }
        if (nome.length() > TAMANHO_MAXIMO_NOME) {
            throw new RegraDeNegocioException(
                    "o nome da categoria deve ter até " + TAMANHO_MAXIMO_NOME + " caracteres");
        }
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
