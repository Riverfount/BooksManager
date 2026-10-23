package com.riverfount.booksmanager.loan.application.service;

import com.riverfount.booksmanager.catalog.application.port.in.CopySummary;
import com.riverfount.booksmanager.loan.domain.Loan;
import com.riverfount.booksmanager.loan.domain.LoanNotice;
import com.riverfount.booksmanager.loan.domain.LoanNoticeKind;
import com.riverfount.booksmanager.shared.application.port.out.Notification;
import com.riverfount.booksmanager.user.application.port.in.ReaderContact;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * The text of the loan notices (RF28), in Brazilian Portuguese because the reader reads it: the one place where the
 * wording lives. It carries only what the reader needs to act: the book, the due date and how many days are left or
 * late (LGPD: no document, no phone, no other data).
 */
final class LoanNoticeMessages {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String UNKNOWN_BOOK = "um livro da biblioteca";

    private LoanNoticeMessages() {
    }

    static Notification of(ReaderContact reader, CopySummary copy, Loan loan, LoanNotice notice, LocalDate today) {
        var book = copy != null && copy.bookTitle() != null ? "\"" + copy.bookTitle() + "\"" : UNKNOWN_BOOK;
        var due = loan.getDueDate().format(DATE);
        return notice.kind() == LoanNoticeKind.DUE_SOON
                ? dueSoon(reader, book, due, ChronoUnit.DAYS.between(today, loan.getDueDate()))
                : overdue(reader, book, due, loan.daysLate(today));
    }

    private static Notification dueSoon(ReaderContact reader, String book, String due, long daysLeft) {
        var when = daysLeft == 0 ? "vence hoje" : daysLeft == 1 ? "vence amanhã" : "vence em " + daysLeft + " dias";
        return new Notification(reader.email(), "Devolução próxima: " + book,
                "Olá, " + reader.name() + ".\n\n"
                        + "O empréstimo de " + book + " " + when + " (devolução até " + due + ").\n"
                        + "Devolva o livro até essa data para não gerar multa ou suspensão.\n");
    }

    private static Notification overdue(ReaderContact reader, String book, String due, long daysLate) {
        var late = daysLate == 1 ? "1 dia" : daysLate + " dias";
        return new Notification(reader.email(), "Empréstimo em atraso: " + book,
                "Olá, " + reader.name() + ".\n\n"
                        + "O empréstimo de " + book + " está em atraso há " + late + " (devolução prevista para "
                        + due + ").\n"
                        + "Devolva o livro o quanto antes: a penalidade por atraso aumenta a cada dia.\n");
    }
}
