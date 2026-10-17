package com.riverfount.booksmanager.loan.application.port.in;

/** The early end of a suspension, with the justification that is required (RN15). The date is today, from the clock. */
public record EndSuspensionCommand(Long suspensionId, String reason) {
}
