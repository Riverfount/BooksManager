package com.riverfount.booksmanager.audit.domain;

import java.time.Instant;

/**
 * One line of the audit trail (RF27): who did what, to what, and when. Entries are only ever added, never
 * changed or removed by the application. The actor is absent for the initial administrator created at startup
 * and for a login that names no known user; the login is kept next to the id because it does not change and
 * makes the line readable on its own. The summary is a short free text (for example the justification typed on a
 * waiver) and never carries a password, a hash, a token or personal data beyond what the operation needs (LGPD).
 */
public record AuditEntry(Long id, Instant occurredAt, Long actorId, String actorLogin, AuditAction action,
        AuditTarget target, Long targetId, String summary) {

    /** The longest summary kept; a longer one is cut, so a long justification never makes the operation fail. */
    public static final int MAX_SUMMARY_LENGTH = 500;

    private static final String ELLIPSIS = "…";

    public AuditEntry {
        if (occurredAt == null) {
            throw new IllegalArgumentException("o instante do registro é obrigatório");
        }
        if (action == null) {
            throw new IllegalArgumentException("a ação do registro é obrigatória");
        }
        if (targetId != null && target == null) {
            throw new IllegalArgumentException("o id do alvo exige o tipo do alvo");
        }
        if (actorLogin != null && actorId == null) {
            throw new IllegalArgumentException("o login do autor exige o id do autor");
        }
        summary = normalize(summary);
    }

    /** A new entry, not stored yet. */
    public static AuditEntry record(Instant occurredAt, Long actorId, String actorLogin, AuditAction action,
            AuditTarget target, Long targetId, String summary) {
        return new AuditEntry(null, occurredAt, actorId, actorLogin, action, target, targetId, summary);
    }

    /** An entry as it was stored. */
    public static AuditEntry reconstitute(Long id, Instant occurredAt, Long actorId, String actorLogin,
            AuditAction action, AuditTarget target, Long targetId, String summary) {
        return new AuditEntry(id, occurredAt, actorId, actorLogin, action, target, targetId, summary);
    }

    private static String normalize(String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }
        var trimmed = summary.strip();
        if (trimmed.length() <= MAX_SUMMARY_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_SUMMARY_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }
}
