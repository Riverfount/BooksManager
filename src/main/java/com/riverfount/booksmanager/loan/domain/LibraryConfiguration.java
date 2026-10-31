package com.riverfount.booksmanager.loan.domain;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.math.BigDecimal;

/**
 * The rules of the library (RF26, RN16): the penalty policy for late returns
 * and its numbers, the loan period, the limits and the reservation pickup
 * period. There is only one configuration; it always exists, created with the
 * initial values by the database migration, so this class has no creation, only
 * reconstitution and update.
 *
 * <p>Nothing here is a constant of the code: the loan period, the limits, the
 * fine and the suspension are read from this configuration (RN16). Every
 * instance respects the rules below, so an invalid state cannot exist:
 * <ul>
 *   <li>the policy and every number are required;</li>
 *   <li>the daily fine is not negative (zero is how a library that only
 *       suspends spends nothing), the fine cap is positive and never below one
 *       day of fine; the money has at most two decimal places;</li>
 *   <li>the suspension days per day of delay, the suspension cap, the loan
 *       period, the loan limit, the reservation pickup period and the days of
 *       notice before a due date are at least one; the renewal limit is not
 *       negative (zero means no renewals);</li>
 *   <li>the asset code prefix has 3 to 4 letters, A to Z, and is kept in uppercase (a lowercase one is uppercased).</li>
 * </ul>
 */
public class LibraryConfiguration {

    /** What the {@code numeric(10, 2)} columns of the database hold. */
    static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");
    private static final int MONEY_SCALE = 2;
    private static final java.util.regex.Pattern ASSET_CODE_PREFIX = java.util.regex.Pattern.compile("[A-Z]{3,4}");

    private final Long id;
    private PenaltyPolicy penaltyPolicy;
    private BigDecimal dailyFineAmount;
    private BigDecimal fineCap;
    private int suspensionDaysPerDay;
    private int suspensionCapDays;
    private int loanPeriodDays;
    private int loanLimit;
    private int renewalLimit;
    private int reservationPickupDays;
    private int dueSoonNoticeDays;
    private String assetCodePrefix;

    private LibraryConfiguration(Long id, PenaltyPolicy penaltyPolicy, BigDecimal dailyFineAmount,
            BigDecimal fineCap, int suspensionDaysPerDay, int suspensionCapDays, int loanPeriodDays, int loanLimit,
            int renewalLimit, int reservationPickupDays, int dueSoonNoticeDays,
            String assetCodePrefix) {
        var normalizedPrefix = normalizedAssetCodePrefix(assetCodePrefix);
        validate(penaltyPolicy, dailyFineAmount, fineCap, suspensionDaysPerDay, suspensionCapDays, loanPeriodDays,
                loanLimit, renewalLimit, reservationPickupDays, dueSoonNoticeDays);
        this.id = id;
        this.penaltyPolicy = penaltyPolicy;
        this.dailyFineAmount = withMoneyScale(dailyFineAmount);
        this.fineCap = withMoneyScale(fineCap);
        this.suspensionDaysPerDay = suspensionDaysPerDay;
        this.suspensionCapDays = suspensionCapDays;
        this.loanPeriodDays = loanPeriodDays;
        this.loanLimit = loanLimit;
        this.renewalLimit = renewalLimit;
        this.reservationPickupDays = reservationPickupDays;
        this.dueSoonNoticeDays = dueSoonNoticeDays;
        this.assetCodePrefix = normalizedPrefix;
    }

    public static LibraryConfiguration reconstitute(Long id, PenaltyPolicy penaltyPolicy, BigDecimal dailyFineAmount,
            BigDecimal fineCap, int suspensionDaysPerDay, int suspensionCapDays, int loanPeriodDays, int loanLimit,
            int renewalLimit, int reservationPickupDays, int dueSoonNoticeDays, String assetCodePrefix) {
        return new LibraryConfiguration(id, penaltyPolicy, dailyFineAmount, fineCap, suspensionDaysPerDay,
                suspensionCapDays, loanPeriodDays, loanLimit, renewalLimit, reservationPickupDays,
                dueSoonNoticeDays, assetCodePrefix);
    }

    /**
     * Partial update: a null value keeps the current one. The rules are checked
     * on the result, so a value kept counts too (a new daily fine above the
     * current cap is refused), and nothing changes if anything is invalid.
     * A change only applies to the delays that come after it (RN16): what was
     * already returned is not recalculated.
     */
    public void update(PenaltyPolicy penaltyPolicy, BigDecimal dailyFineAmount, BigDecimal fineCap,
            Integer suspensionDaysPerDay, Integer suspensionCapDays, Integer loanPeriodDays, Integer loanLimit,
            Integer renewalLimit, Integer reservationPickupDays, Integer dueSoonNoticeDays,
            String assetCodePrefix) {
        var newPolicy = penaltyPolicy != null ? penaltyPolicy : this.penaltyPolicy;
        var newDailyFine = dailyFineAmount != null ? dailyFineAmount : this.dailyFineAmount;
        var newFineCap = fineCap != null ? fineCap : this.fineCap;
        var newDaysPerDay = suspensionDaysPerDay != null ? suspensionDaysPerDay : this.suspensionDaysPerDay;
        var newSuspensionCap = suspensionCapDays != null ? suspensionCapDays : this.suspensionCapDays;
        var newPeriod = loanPeriodDays != null ? loanPeriodDays : this.loanPeriodDays;
        var newLoanLimit = loanLimit != null ? loanLimit : this.loanLimit;
        var newRenewalLimit = renewalLimit != null ? renewalLimit : this.renewalLimit;
        var newPickup = reservationPickupDays != null ? reservationPickupDays : this.reservationPickupDays;
        var newNoticeDays = dueSoonNoticeDays != null ? dueSoonNoticeDays : this.dueSoonNoticeDays;
        var newPrefix = assetCodePrefix != null ? normalizedAssetCodePrefix(assetCodePrefix) : this.assetCodePrefix;
        validate(newPolicy, newDailyFine, newFineCap, newDaysPerDay, newSuspensionCap, newPeriod, newLoanLimit,
                newRenewalLimit, newPickup, newNoticeDays);
        this.penaltyPolicy = newPolicy;
        this.dailyFineAmount = withMoneyScale(newDailyFine);
        this.fineCap = withMoneyScale(newFineCap);
        this.suspensionDaysPerDay = newDaysPerDay;
        this.suspensionCapDays = newSuspensionCap;
        this.loanPeriodDays = newPeriod;
        this.loanLimit = newLoanLimit;
        this.renewalLimit = newRenewalLimit;
        this.reservationPickupDays = newPickup;
        this.dueSoonNoticeDays = newNoticeDays;
        this.assetCodePrefix = newPrefix;
    }

    private static void validate(PenaltyPolicy policy, BigDecimal dailyFineAmount, BigDecimal fineCap,
            int suspensionDaysPerDay, int suspensionCapDays, int loanPeriodDays, int loanLimit, int renewalLimit,
            int reservationPickupDays, int dueSoonNoticeDays) {
        if (policy == null) {
            throw new BusinessRuleException("a política de penalidade é obrigatória");
        }
        if (dailyFineAmount == null) {
            throw new BusinessRuleException("o valor diário da multa é obrigatório");
        }
        if (fineCap == null) {
            throw new BusinessRuleException("o teto da multa é obrigatório");
        }
        if (dailyFineAmount.signum() < 0) {
            throw new BusinessRuleException("o valor diário da multa não pode ser negativo");
        }
        validateMoney(dailyFineAmount, "o valor diário da multa");
        if (fineCap.signum() <= 0) {
            throw new BusinessRuleException("o teto da multa deve ser maior que zero");
        }
        validateMoney(fineCap, "o teto da multa");
        if (fineCap.compareTo(dailyFineAmount) < 0) {
            throw new BusinessRuleException("o teto da multa não pode ser menor que o valor diário da multa");
        }
        if (suspensionDaysPerDay < 1) {
            throw new BusinessRuleException("os dias de suspensão por dia de atraso devem ser de pelo menos 1");
        }
        if (suspensionCapDays < 1) {
            throw new BusinessRuleException("o teto da suspensão deve ser de pelo menos 1 dia");
        }
        if (loanPeriodDays < 1) {
            throw new BusinessRuleException("o prazo de empréstimo deve ser de pelo menos 1 dia");
        }
        if (loanLimit < 1) {
            throw new BusinessRuleException("o limite de empréstimos simultâneos deve ser de pelo menos 1");
        }
        if (renewalLimit < 0) {
            throw new BusinessRuleException("o limite de renovações não pode ser negativo");
        }
        if (reservationPickupDays < 1) {
            throw new BusinessRuleException("o prazo de retirada de reservas deve ser de pelo menos 1 dia");
        }
        if (dueSoonNoticeDays < 1) {
            throw new BusinessRuleException("a antecedência do aviso de vencimento deve ser de pelo menos 1 dia");
        }
    }

    /** The prefix in uppercase; it must have 3 to 4 letters, A to Z. */
    private static String normalizedAssetCodePrefix(String prefix) {
        if (prefix == null) {
            throw new BusinessRuleException("o prefixo do código de patrimônio é obrigatório");
        }
        var normalized = prefix.toUpperCase(java.util.Locale.ROOT);
        if (!ASSET_CODE_PREFIX.matcher(normalized).matches()) {
            throw new BusinessRuleException("o prefixo do código de patrimônio deve ter de 3 a 4 letras, de A a Z");
        }
        return normalized;
    }

    /**
     * The money always has two decimal places, as the database stores it, so what the
     * API answers after a change is what it answers later (1.5 becomes 1.50). The
     * value was already validated to have at most two, so this never rounds.
     */
    private static BigDecimal withMoneyScale(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE);
    }

    private static void validateMoney(BigDecimal amount, String subject) {
        if (amount.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new BusinessRuleException(subject + " deve ter no máximo duas casas decimais");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessRuleException(subject + " não pode passar de " + MAX_AMOUNT.toPlainString());
        }
    }

    public Long getId() {
        return id;
    }

    public PenaltyPolicy getPenaltyPolicy() {
        return penaltyPolicy;
    }

    public BigDecimal getDailyFineAmount() {
        return dailyFineAmount;
    }

    public BigDecimal getFineCap() {
        return fineCap;
    }

    public int getSuspensionDaysPerDay() {
        return suspensionDaysPerDay;
    }

    public int getSuspensionCapDays() {
        return suspensionCapDays;
    }

    public int getLoanPeriodDays() {
        return loanPeriodDays;
    }

    public int getLoanLimit() {
        return loanLimit;
    }

    public int getRenewalLimit() {
        return renewalLimit;
    }

    public int getReservationPickupDays() {
        return reservationPickupDays;
    }

    /** The prefix of the asset codes the library suggests (e.g. {@code PAT} for {@code PAT-00001}), in uppercase. */
    public String getAssetCodePrefix() {
        return assetCodePrefix;
    }

    /** How many days before the due date the reader is told by e-mail that the loan is about to fall due. */
    public int getDueSoonNoticeDays() {
        return dueSoonNoticeDays;
    }
}
