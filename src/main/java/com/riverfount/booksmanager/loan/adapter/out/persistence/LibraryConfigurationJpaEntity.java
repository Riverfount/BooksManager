package com.riverfount.booksmanager.loan.adapter.out.persistence;

import com.riverfount.booksmanager.loan.domain.PenaltyPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Library configuration JPA entity, mapped to the table created by migration
 * V6__create_library_configuration.sql. Separate from the domain; the conversion
 * is done by LibraryConfigurationPersistenceAdapter. The id is a {@code Short}
 * because the column is a {@code smallint} fixed at 1 (there is a single row),
 * and it is never generated.
 */
@Entity
@Table(name = "library_configuration")
class LibraryConfigurationJpaEntity {

    @Id
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_policy", nullable = false, length = 20)
    private PenaltyPolicy penaltyPolicy;

    @Column(name = "daily_fine_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyFineAmount;

    @Column(name = "fine_cap", nullable = false, precision = 10, scale = 2)
    private BigDecimal fineCap;

    @Column(name = "suspension_days_per_day", nullable = false)
    private int suspensionDaysPerDay;

    @Column(name = "suspension_cap_days", nullable = false)
    private int suspensionCapDays;

    @Column(name = "loan_period_days", nullable = false)
    private int loanPeriodDays;

    @Column(name = "loan_limit", nullable = false)
    private int loanLimit;

    @Column(name = "renewal_limit", nullable = false)
    private int renewalLimit;

    @Column(name = "reservation_pickup_days", nullable = false)
    private int reservationPickupDays;

    @Column(name = "due_soon_notice_days", nullable = false)
    private int dueSoonNoticeDays;

    protected LibraryConfigurationJpaEntity() {
        // required by JPA
    }

    LibraryConfigurationJpaEntity(Short id, PenaltyPolicy penaltyPolicy, BigDecimal dailyFineAmount,
            BigDecimal fineCap, int suspensionDaysPerDay, int suspensionCapDays, int loanPeriodDays, int loanLimit,
            int renewalLimit, int reservationPickupDays, int dueSoonNoticeDays) {
        this.id = id;
        this.penaltyPolicy = penaltyPolicy;
        this.dailyFineAmount = dailyFineAmount;
        this.fineCap = fineCap;
        this.suspensionDaysPerDay = suspensionDaysPerDay;
        this.suspensionCapDays = suspensionCapDays;
        this.loanPeriodDays = loanPeriodDays;
        this.loanLimit = loanLimit;
        this.renewalLimit = renewalLimit;
        this.reservationPickupDays = reservationPickupDays;
        this.dueSoonNoticeDays = dueSoonNoticeDays;
    }

    Short getId() {
        return id;
    }

    PenaltyPolicy getPenaltyPolicy() {
        return penaltyPolicy;
    }

    BigDecimal getDailyFineAmount() {
        return dailyFineAmount;
    }

    BigDecimal getFineCap() {
        return fineCap;
    }

    int getSuspensionDaysPerDay() {
        return suspensionDaysPerDay;
    }

    int getSuspensionCapDays() {
        return suspensionCapDays;
    }

    int getLoanPeriodDays() {
        return loanPeriodDays;
    }

    int getLoanLimit() {
        return loanLimit;
    }

    int getRenewalLimit() {
        return renewalLimit;
    }

    int getReservationPickupDays() {
        return reservationPickupDays;
    }

    int getDueSoonNoticeDays() {
        return dueSoonNoticeDays;
    }
}
