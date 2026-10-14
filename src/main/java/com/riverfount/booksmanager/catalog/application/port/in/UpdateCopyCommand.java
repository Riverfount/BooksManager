package com.riverfount.booksmanager.catalog.application.port.in;

import com.riverfount.booksmanager.catalog.domain.CopyStatus;

/**
 * Partial update of a copy (RF04, HU23): a null field means "keep the
 * current value". Only the status is editable; the asset code and the
 * book are immutable, so they are not part of the command.
 */
public record UpdateCopyCommand(Long id, CopyStatus status) {
}
