package com.riverfount.booksmanager.loan.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.LocalDate;

/**
 * The suspension of a reader for returning a loan late (RF20, RN15), a record of the {@code loan}
 * module (decision of #135), not a field of the reader. It points to the reader and to the loan
 * that caused it by id.
 *
 * <p>It starts on the day of the return and covers {@code days} days from then: the reader is
 * suspended from the start date up to, but not including, the end date, which is already free.
 * It ends by itself on that date: whether it is in force is derived from the date asked
 * ({@link #isInForce(LocalDate)}), so no job has to run to close it. The operator can end it
 * earlier, with a justification that stays recorded; the calculated end date is kept as history.
 * The domain never reads the clock.
 *
 * <p>How many days it lasts comes from the {@link PenaltyPolicy} ({@link Penalty#suspensionDays()}).
 */
public class Suspension {

    public static final int MAX_REASON_LENGTH = 500;

    private final Long id;
    private final Long readerId;
    private final Long loanId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private LocalDate endedEarlyOn;
    private String endedEarlyReason;

    private Suspension(Long id, Long readerId, Long loanId, LocalDate startDate, LocalDate endDate,
            LocalDate endedEarlyOn, String endedEarlyReason) {
        this.id = id;
        this.readerId = readerId;
        this.loanId = loanId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.endedEarlyOn = endedEarlyOn;
        this.endedEarlyReason = endedEarlyReason;
    }

    /** A new suspension that starts on {@code startDate} and lasts {@code days} days. */
    public static Suspension create(Long readerId, Long loanId, LocalDate startDate, int days) {
        validateParties(readerId, loanId);
        if (startDate == null) {
            throw new BusinessRuleException("a data de início da suspensão é obrigatória");
        }
        if (days < 1) {
            throw new IllegalArgumentException("a suspension lasts at least one day");
        }
        return new Suspension(null, readerId, loanId, startDate, startDate.plusDays(days), null, null);
    }

    public static Suspension reconstitute(Long id, Long readerId, Long loanId, LocalDate startDate,
            LocalDate endDate, LocalDate endedEarlyOn, String endedEarlyReason) {
        validateParties(readerId, loanId);
        if (startDate == null || endDate == null) {
            throw new BusinessRuleException("as datas de início e de término da suspensão são obrigatórias");
        }
        if (!endDate.isAfter(startDate)) {
            throw new BusinessRuleException("o término da suspensão deve ser depois do início");
        }
        var hasReason = endedEarlyReason != null && !endedEarlyReason.isBlank();
        if (endedEarlyOn != null && !hasReason) {
            throw new BusinessRuleException("uma suspensão encerrada antes do prazo precisa da justificativa");
        }
        if (endedEarlyOn == null && endedEarlyReason != null) {
            throw new BusinessRuleException("só uma suspensão encerrada antes do prazo tem justificativa");
        }
        if (endedEarlyOn != null && endedEarlyOn.isAfter(endDate)) {
            throw new BusinessRuleException("o encerramento antecipado não pode ser depois do término calculado");
        }
        return new Suspension(id, readerId, loanId, startDate, endDate, endedEarlyOn, endedEarlyReason);
    }

    /**
     * Whether the reader is suspended on {@code day}: from the start date up to the day before
     * the end (the calculated one, or the day it was ended early, whichever came first).
     */
    public boolean isInForce(LocalDate day) {
        requireDate(day);
        return !day.isBefore(startDate) && day.isBefore(releaseDate());
    }

    /**
     * Ends the suspension before the calculated date, from {@code today} on, recording the
     * justification (RN15). The justification is required, kept without the surrounding spaces,
     * and refused when missing or too long whatever the state of the suspension. If it already
     * ended, by itself or earlier, nothing changes and the first record is kept.
     *
     * @return whether the suspension changed and has to be saved
     */
    public boolean endEarly(LocalDate today, String reason) {
        requireDate(today);
        var justification = validJustification(reason);
        if (isOver(today)) {
            return false;
        }
        this.endedEarlyOn = today;
        this.endedEarlyReason = justification;
        return true;
    }

    public boolean isEndedEarly() {
        return endedEarlyOn != null;
    }

    private boolean isOver(LocalDate today) {
        return isEndedEarly() || !today.isBefore(endDate);
    }

    /**
     * The day the reader is free again: the calculated end date, or the day the suspension was
     * ended early when that came first. It is the first day the suspension is no longer in force.
     */
    public LocalDate releaseDate() {
        return endedEarlyOn != null && endedEarlyOn.isBefore(endDate) ? endedEarlyOn : endDate;
    }

    private static String validJustification(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("a justificativa do encerramento antecipado é obrigatória");
        }
        var justification = reason.strip();
        if (justification.length() > MAX_REASON_LENGTH) {
            throw new BusinessRuleException(
                    "a justificativa do encerramento antecipado deve ter até " + MAX_REASON_LENGTH + " caracteres");
        }
        return justification;
    }

    private static void requireDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("the date is required");
        }
    }

    private static void validateParties(Long readerId, Long loanId) {
        if (readerId == null) {
            throw new BusinessRuleException("o leitor da suspensão é obrigatório");
        }
        if (loanId == null) {
            throw new BusinessRuleException("o empréstimo da suspensão é obrigatório");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getReaderId() {
        return readerId;
    }

    public Long getLoanId() {
        return loanId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    /** The calculated end date, kept even if the suspension was ended earlier. */
    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDate getEndedEarlyOn() {
        return endedEarlyOn;
    }

    public String getEndedEarlyReason() {
        return endedEarlyReason;
    }
}
