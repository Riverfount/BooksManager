package com.riverfount.booksmanager.user.application.port.in;

import java.time.LocalDate;

/**
 * Partial update of a reader (RF07, HU26): a null field means "keep the
 * current value", so no field can be cleared by this command. The guardian
 * fields are merged one by one with the current guardian. Only the personal
 * data of RNF11 is editable; the state (active, blocked) is not.
 */
public record UpdateReaderCommand(Long id, String name, String document, String email, String phone,
        LocalDate birthDate, String guardianName, String guardianEmail, String guardianPhone) {
}
