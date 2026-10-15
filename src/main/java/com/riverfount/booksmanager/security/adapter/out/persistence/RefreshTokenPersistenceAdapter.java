package com.riverfount.booksmanager.security.adapter.out.persistence;

import com.riverfount.booksmanager.security.application.port.out.RefreshTokenRepository;
import com.riverfount.booksmanager.security.domain.RefreshToken;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpaRepository;

    RefreshTokenPersistenceAdapter(RefreshTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        var entity = new RefreshTokenJpaEntity(token.getId(), token.getUserId(), token.getTokenHash(),
                token.getExpiresAt(), token.getRevokedAt(), token.getReplacedBy(), token.getCreatedAt());
        // flushed here so that a repeated hash or an unknown user fails in this call
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        if (tokenHash == null) {
            return Optional.empty();
        }
        return jpaRepository.findByTokenHash(tokenHash).map(this::toDomain);
    }

    private RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        return RefreshToken.reconstitute(entity.getId(), entity.getUserId(), entity.getTokenHash(),
                entity.getCreatedAt(), entity.getExpiresAt(), entity.getRevokedAt(), entity.getReplacedBy());
    }
}
