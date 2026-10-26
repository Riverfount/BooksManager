package com.riverfount.booksmanager.user.application.port.in;

import java.time.Instant;

/** An invitation that was sent: until when its link is valid. It never carries the token. */
public record ReaderAccessInvitation(Long readerId, Instant expiresAt) {
}
