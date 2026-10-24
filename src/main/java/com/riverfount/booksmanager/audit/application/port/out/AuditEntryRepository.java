package com.riverfount.booksmanager.audit.application.port.out;

import com.riverfount.booksmanager.audit.domain.AuditEntry;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.PageResult;

/**
 * Storage of the audit trail (RF27). It only adds and reads: there is deliberately no way to change or remove an
 * entry through it.
 */
public interface AuditEntryRepository {

    AuditEntry save(AuditEntry entry);

    PageResult<AuditEntry> findAll(AuditFilter filter, PageQuery page);
}
