package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.shared.application.port.out.Notification;
import com.riverfount.booksmanager.user.application.port.in.ReaderContact;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * The text of the notice that a reserved copy is waiting for the reader (RF28, RN13), in Brazilian Portuguese because
 * the reader reads it. Like {@link LoanNoticeMessages}, it carries only what the reader needs to act: the book and the
 * last day to pick the copy up (LGPD: no other personal data).
 */
final class ReservationNoticeMessages {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String UNKNOWN_BOOK = "o livro reservado";

    private ReservationNoticeMessages() {
    }

    static Notification copyReady(ReaderContact reader, String bookTitle, LocalDate lastPickupDay) {
        var book = bookTitle != null && !bookTitle.isBlank() ? "\"" + bookTitle + "\"" : UNKNOWN_BOOK;
        return new Notification(reader.email(), "Livro reservado disponível: " + book,
                "Olá, " + reader.name() + ".\n\n"
                        + "Um exemplar de " + book + " foi guardado para você na biblioteca.\n"
                        + "Retire-o até " + lastPickupDay.format(DATE) + " (inclusive); depois dessa data a reserva "
                        + "expira e o exemplar passa para o próximo da fila.\n");
    }
}
