package com.riverfount.booksmanager.shared.adapter.in.web;

import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Fills a {@link CurrentReader} parameter with the reader of the access token (Phase 6). Anything that is not a token
 * with a positive whole {@code readerId} is refused with 403, since a route of the reader without a reader has no
 * owner to act for.
 */
@Component
class CurrentReaderArgumentResolver implements HandlerMethodArgumentResolver, WebMvcConfigurer {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentReader.class) && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt token)) {
            throw new AccessDeniedException("no reader for this caller");
        }
        var claim = token.getClaim(CurrentReader.CLAIM);
        if (claim instanceof Long || claim instanceof Integer) {
            var readerId = ((Number) claim).longValue();
            if (readerId > 0) {
                return readerId;
            }
        }
        throw new AccessDeniedException("no reader for this caller");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this);
    }
}
