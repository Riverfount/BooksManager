package com.riverfount.booksmanager.user.application.service;

import com.riverfount.booksmanager.user.application.port.out.ReaderAccessTokenRepository;
import com.riverfount.booksmanager.user.domain.ReaderAccessToken;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory implementation of ReaderAccessTokenRepository for the application services' unit tests. */
class InMemoryReaderAccessTokenRepository implements ReaderAccessTokenRepository {

    private final Map<Long, ReaderAccessToken> tokens = new HashMap<>();
    private long nextId = 1;

    private static ReaderAccessToken copyWithId(ReaderAccessToken token, Long id) {
        return ReaderAccessToken.reconstitute(id, token.getPurpose(), token.getReaderId(), token.getTokenHash(),
                token.getCreatedAt(), token.getExpiresAt(), token.getUsedAt());
    }

    @Override
    public ReaderAccessToken save(ReaderAccessToken token) {
        var id = token.getId() != null ? token.getId() : nextId++;
        tokens.put(id, copyWithId(token, id));
        return copyWithId(token, id);
    }

    @Override
    public Optional<ReaderAccessToken> findByTokenHashForUpdate(String tokenHash) {
        return tokens.values().stream().filter(token -> token.getTokenHash().equals(tokenHash)).findFirst()
                .map(token -> copyWithId(token, token.getId()));
    }

    @Override
    public int invalidatePending(Long readerId, ReaderAccessToken.Purpose purpose, Instant now) {
        var count = 0;
        for (var token : tokens.values()) {
            if (token.getReaderId().equals(readerId) && token.getPurpose() == purpose && token.use(now)) {
                count++;
            }
        }
        return count;
    }

    int size() {
        return tokens.size();
    }
}
