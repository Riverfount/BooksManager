package com.riverfount.booksmanager.audit.domain;

/**
 * The sensitive operations the audit trail records (RF27). Adding one here is a decision about what the
 * library must be able to answer ("who waived this fine"), so each has a use case that records it.
 */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGIN_LOCKOUT,
    USER_CREATED,
    USER_UPDATED,
    USER_DEACTIVATED,
    USER_UNBLOCKED,
    USER_PASSWORD_RESET,
    READER_BLOCKED,
    READER_UNBLOCKED,
    READER_DEACTIVATED,
    FINE_PAID,
    FINE_WAIVED,
    SUSPENSION_ENDED,
    LIBRARY_CONFIGURATION_UPDATED
}
