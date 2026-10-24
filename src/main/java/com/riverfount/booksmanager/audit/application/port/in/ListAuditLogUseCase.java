package com.riverfount.booksmanager.audit.application.port.in;

import com.riverfount.booksmanager.audit.domain.AuditEntry;
import com.riverfount.booksmanager.shared.domain.PageResult;

/** Lists the audit trail (RF27), the newest first. Only for the administrator, which the web adapter enforces. */
public interface ListAuditLogUseCase {

    PageResult<AuditEntry> list(ListAuditLogQuery query);
}
