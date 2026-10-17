package com.riverfount.booksmanager.loan.application.port.in;

import com.riverfount.booksmanager.loan.domain.Suspension;

/**
 * A suspension with whether it is in force today (RF22, HU11): derived from the clock's day by
 * the domain's own rule, so no job has to close it.
 */
public record SuspensionDetails(Suspension suspension, boolean inForce) {
}
