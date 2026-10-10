package com.riverfount.booksmanager.compartilhado.adapter.in.web;

import com.riverfount.booksmanager.compartilhado.domain.RecursoNaoEncontradoException;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converte as exceções do domínio em respostas ProblemDetail (RFC 9457)
 * (RNF04), compartilhado por todos os módulos. A validação de Bean
 * Validation (400) já é tratada pelo Spring, também como ProblemDetail
 * (spring.mvc.problemdetails.enabled=true).
 */
@RestControllerAdvice
public class TratadorDeExcecaoGlobal {

    @ExceptionHandler(RegraDeNegocioException.class)
    ProblemDetail tratarRegraDeNegocio(RegraDeNegocioException excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, excecao.getMessage());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ProblemDetail tratarRecursoNaoEncontrado(RecursoNaoEncontradoException excecao) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, excecao.getMessage());
    }
}
