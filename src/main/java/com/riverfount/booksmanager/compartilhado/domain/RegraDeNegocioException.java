package com.riverfount.booksmanager.compartilhado.domain;

/**
 * Sinaliza a violação de uma regra de negócio do domínio (por exemplo, um
 * dado inválido ou uma transição de estado não permitida).
 *
 * <p>Use esta exceção nas entidades e políticas do domínio, nunca nos
 * adaptadores. Mais tarde, o adaptador web converte essa exceção em um
 * {@code ProblemDetail} (RFC 9457), com status 422, na resposta da API
 * (RNF04).
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
