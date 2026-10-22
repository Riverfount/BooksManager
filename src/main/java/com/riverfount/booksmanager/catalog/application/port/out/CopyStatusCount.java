package com.riverfount.booksmanager.catalog.application.port.out;

import com.riverfount.booksmanager.catalog.domain.CopyStatus;

/** How many copies have a status, active or inactive (RF25): a row of the collection status count. */
public record CopyStatusCount(CopyStatus status, boolean active, long total) {
}
