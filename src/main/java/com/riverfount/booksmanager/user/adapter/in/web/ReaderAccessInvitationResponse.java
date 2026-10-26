package com.riverfount.booksmanager.user.adapter.in.web;

import com.riverfount.booksmanager.user.application.port.in.ReaderAccessInvitation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** An invitation that was sent. It never carries the token: that only goes in the e-mail. */
record ReaderAccessInvitationResponse(
        @Schema(description = "Id do leitor convidado", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
        Long readerId,
        @Schema(description = "Até quando o link do convite vale (ISO-8601, UTC)", example = "2026-10-29T12:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant expiresAt) {

    static ReaderAccessInvitationResponse from(ReaderAccessInvitation invitation) {
        return new ReaderAccessInvitationResponse(invitation.readerId(), invitation.expiresAt());
    }
}
