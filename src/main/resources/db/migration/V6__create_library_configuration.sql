-- RF26, RN16: the rules of the library live here, never in the code: the
-- penalty policy for late returns and its numbers, the loan period, the
-- limits and the reservation pickup period. There is a single row (id is
-- fixed at 1 by a check constraint), created with the values decided on
-- 10/10/2026 (RN08) and the usual defaults (RN04, RN06, RN07, RN13); the
-- administrator changes them later. Every column is required, so no value
-- falls back to a constant in the code.
--
-- The checks refuse what makes no sense (a zero loan period, a negative fine);
-- the domain validates the same rules with messages for the user. A daily fine
-- of zero is allowed (a library that only suspends), and so is a limit of zero
-- renewals. The cap on the fine can never be below one day of fine.

create table library_configuration (
    id                      smallint      primary key default 1
        check (id = 1),
    penalty_policy          varchar(20)   not null
        check (penalty_policy in ('FINE', 'SUSPENSION', 'BOTH', 'NONE')),
    daily_fine_amount       numeric(10, 2) not null
        check (daily_fine_amount >= 0),
    fine_cap                numeric(10, 2) not null
        check (fine_cap > 0),
    suspension_days_per_day integer       not null
        check (suspension_days_per_day > 0),
    suspension_cap_days     integer       not null
        check (suspension_cap_days > 0),
    loan_period_days        integer       not null
        check (loan_period_days > 0),
    loan_limit              integer       not null
        check (loan_limit > 0),
    renewal_limit           integer       not null
        check (renewal_limit >= 0),
    reservation_pickup_days integer       not null
        check (reservation_pickup_days > 0),
    constraint library_configuration_fine_cap_covers_a_day check (fine_cap >= daily_fine_amount)
);

insert into library_configuration (id, penalty_policy, daily_fine_amount, fine_cap, suspension_days_per_day,
                                   suspension_cap_days, loan_period_days, loan_limit, renewal_limit,
                                   reservation_pickup_days)
values (1, 'FINE', 1.00, 10.00, 1, 365, 14, 3, 2, 3);
