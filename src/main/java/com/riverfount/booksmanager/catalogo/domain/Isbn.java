package com.riverfount.booksmanager.catalogo.domain;

import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;

/**
 * ISBN do livro (RN01): objeto de valor imutável, normalizado (sem hífens
 * ou espaços, em maiúsculas) e validado como ISBN-10 ou ISBN-13, com o
 * dígito verificador correto.
 */
public record Isbn(String valor) {

    public Isbn(String valor) {
        valor = normalizar(valor);
        validar(valor);
        this.valor = valor;
    }

    private static String normalizar(String valor) {
        if (valor == null) {
            throw new RegraDeNegocioException("o ISBN é obrigatório");
        }
        return valor.replace("-", "").replace(" ", "").toUpperCase();
    }

    private static void validar(String valor) {
        switch (valor.length()) {
            case 10 -> validarIsbn10(valor);
            case 13 -> validarIsbn13(valor);
            default -> throw new RegraDeNegocioException("ISBN deve ter 10 ou 13 dígitos");
        }
    }

    private static void validarIsbn10(String valor) {
        var soma = 0;
        for (var i = 0; i < 10; i++) {
            soma += valorDoDigito(valor.charAt(i), i == 9) * (10 - i);
        }
        if (soma % 11 != 0) {
            throw new RegraDeNegocioException("dígito verificador do ISBN-10 inválido");
        }
    }

    private static void validarIsbn13(String valor) {
        var soma = 0;
        for (var i = 0; i < 13; i++) {
            var peso = (i % 2 == 0) ? 1 : 3;
            soma += valorDoDigito(valor.charAt(i), false) * peso;
        }
        if (soma % 10 != 0) {
            throw new RegraDeNegocioException("dígito verificador do ISBN-13 inválido");
        }
    }

    private static int valorDoDigito(char caractere, boolean podeSerX) {
        if (podeSerX && caractere == 'X') {
            return 10;
        }
        if (Character.isDigit(caractere)) {
            return caractere - '0';
        }
        throw new RegraDeNegocioException("ISBN contém caractere inválido");
    }
}
