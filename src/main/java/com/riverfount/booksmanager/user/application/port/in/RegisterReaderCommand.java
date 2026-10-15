package com.riverfount.booksmanager.user.application.port.in;

import java.time.LocalDate;

/**
 * Data to register a reader (RF07, HU04). The guardian fields are all
 * null for an adult; for a reader under 18 the domain requires the
 * guardian's name and e-mail.
 */
public record RegisterReaderCommand(String name, String document, String email, String phone,
        LocalDate birthDate, String guardianName, String guardianEmail, String guardianPhone) {
}
