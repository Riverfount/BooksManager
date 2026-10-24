package com.riverfount.booksmanager.loan.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * The loan of a copy to a reader (RF11, RF12, RF13). It keeps the ids of the copy and
 * of the reader, not the objects, so the aggregates stay small and apart. It is active
 * until the copy is returned, and then it is closed for good (RN09).
 *
 * <p>The domain never reads the clock: the date of every operation comes by parameter.
 * The loan period, the renewal limit and everything else that is a number of the library
 * comes from its configuration, also by parameter (RN16); nothing here is a constant.
 *
 * <p>Being late is derived, not stored: an active loan is late once today is after the
 * due date (the due date itself is still on time), and the days late are calendar days.
 * A returned loan is never late, and keeps the days it was late when it came back.
 */
public class Loan {

    private final Long id;
    private final Long copyId;
    private final Long readerId;
    private final LocalDate pickupDate;
    private LocalDate dueDate;
    private LocalDate returnDate;
    private ReturnCondition returnCondition;
    private int renewals;

    private Loan(Long id, Long copyId, Long readerId, LocalDate pickupDate, LocalDate dueDate, LocalDate returnDate,
            ReturnCondition returnCondition, int renewals) {
        this.id = id;
        this.copyId = copyId;
        this.readerId = readerId;
        this.pickupDate = pickupDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.returnCondition = returnCondition;
        this.renewals = renewals;
    }

    /** A new active loan, due {@code loanPeriodDays} after the pickup (RN06). */
    public static Loan create(Long copyId, Long readerId, LocalDate pickupDate, int loanPeriodDays) {
        validateParties(copyId, readerId);
        if (pickupDate == null) {
            throw new BusinessRuleException("a data de retirada é obrigatória");
        }
        if (loanPeriodDays < 1) {
            throw new IllegalArgumentException("the loan period must be at least one day");
        }
        return new Loan(null, copyId, readerId, pickupDate, pickupDate.plusDays(loanPeriodDays), null, null, 0);
    }

    /** A persisted loan from before the return condition existed: a returned one came back GOOD. */
    public static Loan reconstitute(Long id, Long copyId, Long readerId, LocalDate pickupDate, LocalDate dueDate,
            LocalDate returnDate, int renewals) {
        return reconstitute(id, copyId, readerId, pickupDate, dueDate, returnDate, renewals,
                returnDate != null ? ReturnCondition.GOOD : null);
    }

    public static Loan reconstitute(Long id, Long copyId, Long readerId, LocalDate pickupDate, LocalDate dueDate,
            LocalDate returnDate, int renewals, ReturnCondition returnCondition) {
        validateParties(copyId, readerId);
        if (pickupDate == null || dueDate == null) {
            throw new BusinessRuleException("as datas de retirada e de devolução prevista são obrigatórias");
        }
        if (dueDate.isBefore(pickupDate)) {
            throw new BusinessRuleException("a devolução prevista não pode ser anterior à retirada");
        }
        if (returnDate != null && returnDate.isBefore(pickupDate)) {
            throw new BusinessRuleException("a devolução não pode ser anterior à retirada");
        }
        if (renewals < 0) {
            throw new BusinessRuleException("o número de renovações não pode ser negativo");
        }
        if ((returnDate == null) != (returnCondition == null)) {
            throw new BusinessRuleException("o empréstimo devolvido tem condição de devolução e o ativo não tem");
        }
        return new Loan(id, copyId, readerId, pickupDate, dueDate, returnDate, returnCondition, renewals);
    }

    /**
     * Renews the loan (RF13): the due date moves forward by the loan period, counted from
     * the current due date, so a renewal never gives the reader less time than there was.
     * Refused when the loan is already closed (RN09), is late, when the book was inactivated (RN14), when
     * another reader waits in the queue for the book, or when it has used all the renewals the library allows
     * (RN07). Nothing changes when it is refused.
     *
     * @param renewalLimit how many renewals the library allows per loan; zero means none
     * @param loanPeriodDays how many days a renewal adds
     * @param bookHasWaitingReservation whether a reservation of the book waits in the queue for a copy; a
     *        reservation that already holds a copy does not count (RN07). The domain does not look it up: the
     *        application hands it over, like the other facts the loan does not know
     * @param bookActive whether the book of the copy is still active (RN14): a loan of a book that was inactivated
     *        is not extended, though the copy can still be returned
     */
    public void renew(LocalDate today, int renewalLimit, int loanPeriodDays, boolean bookHasWaitingReservation,
            boolean bookActive) {
        requireDate(today);
        if (renewalLimit < 0 || loanPeriodDays < 1) {
            throw new IllegalArgumentException("the renewal limit cannot be negative and the period must be one day or more");
        }
        requireActive();
        if (isOverdue(today)) {
            throw new BusinessRuleException("o empréstimo está em atraso e não pode ser renovado");
        }
        if (!bookActive) {
            throw new BusinessRuleException("o livro está inativo e o empréstimo não pode ser renovado");
        }
        if (bookHasWaitingReservation) {
            throw new BusinessRuleException(
                    "o livro tem uma reserva esperando na fila e o empréstimo não pode ser renovado");
        }
        if (renewals >= renewalLimit) {
            throw new BusinessRuleException(
                    "o limite de renovações (" + renewalLimit + ") deste empréstimo já foi atingido");
        }
        this.dueDate = dueDate.plusDays(loanPeriodDays);
        this.renewals++;
    }

    /**
     * Closes the loan with the copy back (RF12). A loan that was already closed cannot be
     * returned again (RN09), and the return cannot be before the pickup.
     *
     * @return the days the copy came back late, zero when it was on time or early
     */
    public long returnCopy(LocalDate today) {
        return returnCopy(today, ReturnCondition.GOOD);
    }

    /**
     * Closes the loan with the copy back in the given condition (RF15). The condition is only
     * recorded: the days late, and so the penalty, do not depend on it. A missing condition is
     * refused, and nothing changes when the return is refused.
     *
     * @return the days the copy came back late, zero when it was on time or early
     */
    public long returnCopy(LocalDate today, ReturnCondition condition) {
        requireDate(today);
        requireActive();
        if (condition == null) {
            throw new BusinessRuleException("a condição do exemplar na devolução é obrigatória");
        }
        if (today.isBefore(pickupDate)) {
            throw new BusinessRuleException("a devolução não pode ser anterior à retirada");
        }
        this.returnDate = today;
        this.returnCondition = condition;
        return daysLate(today);
    }

    /**
     * Whether an active loan is late on {@code today}: past the due date (the due date itself
     * is on time). A returned loan is never late.
     */
    public boolean isOverdue(LocalDate today) {
        requireDate(today);
        return isActive() && today.isAfter(dueDate);
    }

    /**
     * The calendar days past the due date, never negative. For an active loan it is counted up
     * to {@code today}; for a returned one, up to the day it came back, whatever the date asked.
     */
    public long daysLate(LocalDate today) {
        requireDate(today);
        var until = returnDate != null ? returnDate : today;
        return Math.max(0, ChronoUnit.DAYS.between(dueDate, until));
    }

    /**
     * The notice the reader is owed on {@code today} (RF28), if any. Only an active loan owes one. From
     * {@code dueSoonNoticeDays} before the due date up to the due date itself (still on time) it is the due-soon
     * notice; once late it is the overdue notice of the band of the late days, one band per seven days. The domain
     * does not know what was already sent: the caller compares the result with what it recorded.
     *
     * @param dueSoonNoticeDays how many days before the due date the first notice is owed; at least one
     */
    public Optional<LoanNotice> noticeDue(LocalDate today, int dueSoonNoticeDays) {
        requireDate(today);
        if (dueSoonNoticeDays < 1) {
            throw new IllegalArgumentException("the days of notice must be at least one");
        }
        if (isReturned()) {
            return Optional.empty();
        }
        if (isOverdue(today)) {
            var band = (int) ((daysLate(today) - 1) / LoanNotice.OVERDUE_BAND_DAYS);
            return Optional.of(new LoanNotice(LoanNoticeKind.OVERDUE, dueDate, band));
        }
        if (!today.isBefore(dueDate.minusDays(dueSoonNoticeDays))) {
            return Optional.of(new LoanNotice(LoanNoticeKind.DUE_SOON, dueDate, 0));
        }
        return Optional.empty();
    }

    public boolean isActive() {
        return returnDate == null;
    }

    public boolean isReturned() {
        return returnDate != null;
    }

    private void requireActive() {
        if (isReturned()) {
            throw new BusinessRuleException("o empréstimo já foi encerrado");
        }
    }

    private static void requireDate(LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("the date is required");
        }
    }

    private static void validateParties(Long copyId, Long readerId) {
        if (copyId == null) {
            throw new BusinessRuleException("o exemplar é obrigatório");
        }
        if (readerId == null) {
            throw new BusinessRuleException("o leitor é obrigatório");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getCopyId() {
        return copyId;
    }

    public Long getReaderId() {
        return readerId;
    }

    public LocalDate getPickupDate() {
        return pickupDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    /** The condition the copy came back in, or null while the loan is active. */
    public ReturnCondition getReturnCondition() {
        return returnCondition;
    }

    public int getRenewals() {
        return renewals;
    }
}
