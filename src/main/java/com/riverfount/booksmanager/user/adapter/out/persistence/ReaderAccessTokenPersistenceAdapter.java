package com.riverfount.booksmanager.user.adapter.out.persistence;

import com.riverfount.booksmanager.user.application.port.out.ReaderAccessTokenRepository;
import com.riverfount.booksmanager.user.domain.ReaderAccessToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class ReaderAccessTokenPersistenceAdapter implements ReaderAccessTokenRepository {

    private final ReaderAccessTokenJpaRepository jpaRepository;

    ReaderAccessTokenPersistenceAdapter(ReaderAccessTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ReaderAccessToken save(ReaderAccessToken token) {
        var entity = new ReaderAccessTokenJpaEntity(token.getId(), token.getPurpose(), token.getReaderId(),
                token.getTokenHash(), token.getCreatedAt(), token.getExpiresAt(), token.getUsedAt());
        // flushed here so that a repeated hash or an unknown reader fails in this call
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<ReaderAccessToken> findByTokenHashForUpdate(String tokenHash) {
        if (tokenHash == null) {
            return Optional.empty();
        }
        return jpaRepository.findByTokenHashForUpdate(tokenHash).map(this::toDomain);
    }

    @Override
    public int invalidatePending(Long readerId, ReaderAccessToken.Purpose purpose, Instant now) {
        return jpaRepository.invalidatePending(readerId, purpose, now);
    }

    private ReaderAccessToken toDomain(ReaderAccessTokenJpaEntity entity) {
        return ReaderAccessToken.reconstitute(entity.getId(), entity.getPurpose(), entity.getReaderId(),
                entity.getTokenHash(), entity.getCreatedAt(), entity.getExpiresAt(), entity.getUsedAt());
    }
}
