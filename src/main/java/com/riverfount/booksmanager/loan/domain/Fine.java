package com.riverfount.booksmanager.loan.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.math.BigDecimal;

/**
 * The fine a loan returned late generates (RF20, RF21), when the library's policy charges one.
 * It points to the loan and to the reader by id. It starts pending and is resolved exactly
 * once: paid, or waived with a justification that stays recorded (RF21). A resolved fine
 * cannot be paid or waived again, and only a pending one stops the reader from borrowing
 * (RN05, checked elsewhere).
 *
 * <p>The amount is always positive (a zero fine is not a fine, see {@link Penalty#hasFine()}),
 * with two decimal places, as the database stores it. How much it is comes from the
 * {@link PenaltyPolicy}; this class only holds it.
 */
public class Fine {

    public static final int MAX_WAIVER_REASON_LENGTH = 500;

    /** What the {@code numeric(10, 2)} columns of the database hold. */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");
    private static final int MONEY_SCALE = 2;

    private final Long id;
    private final Long loanId;
    private final Long readerId;
    private final BigDecimal amount;
    private FineStatus status;
    private String waiverReason;

    private Fine(Long id, Long loanId, Long readerId, BigDecimal amount, FineStatus status, String waiverReason) {
        this.id = id;
        this.loanId = loanId;
        this.readerId = readerId;
        this.amount = amount;
        this.status = status;
        this.waiverReason = waiverReason;
    }

    /** A new pending fine. */
    public static Fine create(Long loanId, Long readerId, BigDecimal amount) {
        validateParties(loanId, readerId);
        return new Fine(null, loanId, readerId, validAmount(amount), FineStatus.PENDING, null);
    }

    public static Fine reconstitute(Long id, Long loanId, Long readerId, BigDecimal amount, FineStatus status,
            String waiverReason) {
        validateParties(loanId, readerId);
        if (status == null) {
            throw new BusinessRuleException("a situação da multa é obrigatória");
        }
        var hasReason = waiverReason != null && !waiverReason.isBlank();
        if (status == FineStatus.WAIVED && !hasReason) {
            throw new BusinessRuleException("uma multa abonada precisa ter a justificativa do abono");
        }
        if (status != FineStatus.WAIVED && waiverReason != null) {
            throw new BusinessRuleException("só uma multa abonada tem justificativa de abono");
        }
        return new Fine(id, loanId, readerId, validAmount(amount), status, waiverReason);
    }

    /** The reader paid. Refused when the fine was already paid or waived. */
    public void pay() {
        requirePending();
        this.status = FineStatus.PAID;
    }

    /**
     * The library forgives the fine (RF21). The justification is required, kept without the
     * surrounding spaces, and recorded. Refused when the fine was already paid or waived.
     */
    public void waive(String reason) {
        requirePending();
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("a justificativa do abono é obrigatória");
        }
        var justification = reason.strip();
        if (justification.length() > MAX_WAIVER_REASON_LENGTH) {
            throw new BusinessRuleException(
                    "a justificativa do abono deve ter até " + MAX_WAIVER_REASON_LENGTH + " caracteres");
        }
        this.status = FineStatus.WAIVED;
        this.waiverReason = justification;
    }

    public boolean isPending() {
        return status == FineStatus.PENDING;
    }

    private void requirePending() {
        if (status == FineStatus.PAID) {
            throw new BusinessRuleException("a multa já foi paga");
        }
        if (status == FineStatus.WAIVED) {
            throw new BusinessRuleException("a multa já foi abonada");
        }
    }

    private static void validateParties(Long loanId, Long readerId) {
        if (loanId == null) {
            throw new BusinessRuleException("o empréstimo da multa é obrigatório");
        }
        if (readerId == null) {
            throw new BusinessRuleException("o leitor da multa é obrigatório");
        }
    }

    private static BigDecimal validAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("o valor da multa deve ser maior que zero");
        }
        if (amount.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new BusinessRuleException("o valor da multa deve ter no máximo duas casas decimais");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessRuleException("o valor da multa não pode passar de " + MAX_AMOUNT.toPlainString());
        }
        return amount.setScale(MONEY_SCALE);
    }

    public Long getId() {
        return id;
    }

    public Long getLoanId() {
        return loanId;
    }

    public Long getReaderId() {
        return readerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public FineStatus getStatus() {
        return status;
    }

    public String getWaiverReason() {
        return waiverReason;
    }
}
