package com.riverfount.booksmanager.catalogo.domain;

import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;

/**
 * Autor do acervo (RF02). Autores com histórico de empréstimos são
 * inativados, nunca excluídos fisicamente (RN14).
 */
public class Autor {

    public static final int TAMANHO_MAXIMO_NOME = 150;

    private final Long id;
    private String nome;
    private boolean ativo;

    private Autor(Long id, String nome, boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.ativo = ativo;
    }

    public static Autor novo(String nome) {
        validarNome(nome);
        return new Autor(null, nome, true);
    }

    public static Autor reconstituir(Long id, String nome, boolean ativo) {
        return new Autor(id, nome, ativo);
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
            throw new RegraDeNegocioException("o nome do autor é obrigatório");
        }
        if (nome.length() > TAMANHO_MAXIMO_NOME) {
            throw new RegraDeNegocioException(
                    "o nome do autor deve ter até " + TAMANHO_MAXIMO_NOME + " caracteres");
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
