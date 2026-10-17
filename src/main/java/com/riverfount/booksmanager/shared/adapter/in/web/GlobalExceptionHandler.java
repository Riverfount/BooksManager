package com.riverfount.booksmanager.shared.adapter.in.web;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.InvalidCredentialsException;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts domain exceptions into ProblemDetail (RFC 9457) responses
 * (RNF04), shared by all modules. Bean Validation (400) is already
 * handled by Spring, also as ProblemDetail
 * (spring.mvc.problemdetails.enabled=true).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleResourceNotFound(ResourceNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    /**
     * 409: another request changed the same record at the same time and got there first
     * (optimistic lock, RNF17). Nothing was saved by this one, so trying again is safe.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Outra operação alterou o mesmo registro ao mesmo tempo; tente novamente.");
    }

    @ExceptionHandler(InvalidQueryException.class)
    ProblemDetail handleInvalidQuery(InvalidQueryException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /** 401 at login (RF08): the same fixed answer whatever was wrong, so it cannot be used to find out logins. */
    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Login ou senha inválidos.");
    }

    /**
     * 401: missing, expired or invalid credentials. Also reached from the
     * security filters (see the entry point in the security module). The
     * message is fixed: it never says why the credentials were refused.
     */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "É necessário estar autenticado com um token válido.");
    }

    /** 403: authenticated, but the profile does not allow it (RNF08). Fixed message, like the 401. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Você não tem permissão para acessar este recurso.");
    }
}
