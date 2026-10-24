package com.riverfount.booksmanager.audit.application.port.in;

import com.riverfount.booksmanager.audit.domain.AuditAction;
import com.riverfount.booksmanager.audit.domain.AuditTarget;
import com.riverfount.booksmanager.shared.domain.InvalidQueryException;
import com.riverfount.booksmanager.shared.domain.PageQuery;
import com.riverfount.booksmanager.shared.domain.SortDirection;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The filters and the page asked of the audit log (RF27, RNF14). Every filter is optional; the period is in
 * days, both ends included, in the application's time zone. The order is fixed, the newest first, so there is no
 * sort to choose: only the page and its size (up to 100). A target id only makes sense with its target type.
 */
public record ListAuditLogQuery(Long actorId, AuditAction action, AuditTarget target, Long targetId, LocalDate from,
        LocalDate to, PageQuery page) {

    private static final String ORDER_FIELD = "occurredAt";

    public ListAuditLogQuery {
        if (targetId != null && target == null) {
            throw new InvalidQueryException("o filtro targetId exige o targetType");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidQueryException("o período está invertido: from (" + from + ") é depois de to (" + to + ")");
        }
        Objects.requireNonNull(page, "page");
    }

    public static ListAuditLogQuery of(Long actorId, AuditAction action, AuditTarget target, Long targetId,
            LocalDate from, LocalDate to, int page, int size) {
        return new ListAuditLogQuery(actorId, action, target, targetId, from, to,
                new PageQuery(page, size, ORDER_FIELD, SortDirection.DESC));
    }
}
