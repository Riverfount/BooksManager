package com.riverfount.booksmanager.shared.adapter.in.web;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import com.riverfount.booksmanager.shared.domain.InvalidCredentialsException;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts domain exceptions into ProblemDetail (RFC 9457) responses
 * (RNF04), shared by all modules. The other exceptions of Spring MVC are
 * handled by Spring itself, also as ProblemDetail
 * (spring.mvc.problemdetails.enabled=true).
 *
 * <p>The 400 of a request that is wrong in its form (Bean Validation of the body, a body that cannot be read, a
 * parameter of the wrong type or missing) is made here, ahead of Spring's handler (hence the order): the status stays
 * 400 (the 422 is for the business rules), but the answer says in Portuguese which field failed and why, in a list
 * {@code errors} of {@code {field, message}}, sorted by field. The front-end marks each field; it has no rule of its
 * own. Nothing internal is said: no exception name, no stack trace, no text of Jackson, and not even the value that was
 * sent.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    static final String INVALID_FIELDS = "A requisição tem campos inválidos.";
    static final String BODY_REQUIRED = "O corpo da requisição é obrigatório.";
    static final String BODY_NOT_JSON = "O corpo da requisição não é um JSON válido.";
    private static final String INVALID_VALUE = "valor inválido";

    /** Bean Validation of the body: one entry per constraint that failed, with the path of the field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidBody(MethodArgumentNotValidException exception) {
        var errors = new ArrayList<Map<String, String>>();
        for (var error : exception.getBindingResult().getFieldErrors()) {
            errors.add(entry(error.getField(), error.getDefaultMessage()));
        }
        for (var error : exception.getBindingResult().getGlobalErrors()) {
            errors.add(entry(null, error.getDefaultMessage()));
        }
        return invalidFields(errors);
    }

    /** Constraints on the parameters of a method (not on a body): the same list. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleInvalidParameters(HandlerMethodValidationException exception) {
        var errors = new ArrayList<Map<String, String>>();
        for (var result : exception.getParameterValidationResults()) {
            var name = result.getMethodParameter().getParameterName();
            if (result instanceof ParameterErrors parameterErrors) {
                for (var error : parameterErrors.getFieldErrors()) {
                    errors.add(entry(error.getField(), error.getDefaultMessage()));
                }
            } else {
                for (var error : result.getResolvableErrors()) {
                    errors.add(entry(name, error.getDefaultMessage()));
                }
            }
        }
        return invalidFields(errors);
    }

    /**
     * A body that cannot be read. Without a cause there was no body; with a Jackson error that knows where it
     * happened (a date that is not a date, a text where a list was expected) the field is in the list, with the
     * path; otherwise it is not JSON.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        if (exception.getCause() == null) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, BODY_REQUIRED);
        }
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
                return invalidFields(List.of(entry(pathOf(jackson), INVALID_VALUE)));
            }
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, BODY_NOT_JSON);
    }

    /** A path variable or a request parameter that is not of the expected type. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return invalidFields(List.of(entry(exception.getName(), INVALID_VALUE)));
    }

    /** A request parameter that is required and was not sent. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail handleMissingParameter(MissingServletRequestParameterException exception) {
        return invalidFields(List.of(entry(exception.getParameterName(), "é obrigatório")));
    }

    private static ProblemDetail invalidFields(List<Map<String, String>> errors) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_FIELDS);
        problem.setProperty("errors", errors.stream()
                .sorted(Comparator.comparing((Map<String, String> error) -> error.getOrDefault("field", ""))
                        .thenComparing(error -> error.get("message")))
                .toList());
        return problem;
    }

    /** An entry of the list; {@code field} is left out when the error is about the object as a whole. */
    private static Map<String, String> entry(String field, String message) {
        var entry = new LinkedHashMap<String, String>();
        if (field != null && !field.isBlank()) {
            entry.put("field", field);
        }
        entry.put("message", message == null || message.isBlank() ? INVALID_VALUE : message);
        return entry;
    }

    /** {@code guardian.email} or {@code ids[1]}: the names of the fields of the API, as the client sent them. */
    private static String pathOf(JacksonException exception) {
        var path = new StringBuilder();
        for (var reference : exception.getPath()) {
            if (reference.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

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
