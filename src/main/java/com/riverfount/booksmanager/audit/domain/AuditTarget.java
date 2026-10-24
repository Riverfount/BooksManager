package com.riverfount.booksmanager.audit.domain;

/** What an audited operation acted on. A login that names no known user has no target. */
public enum AuditTarget {
    USER,
    READER,
    FINE,
    SUSPENSION,
    LIBRARY_CONFIGURATION
}
