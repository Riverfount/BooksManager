-- RF15, RNF03: the condition of the copy when the loan is returned. It stays
-- on the loan, so the history can say "returned lost". Active loans have no
-- condition, as they have no return date: the two always come together.
--
-- The loans returned before this migration are given GOOD, the only outcome
-- the system could record until now.

alter table loan add column return_condition varchar(10);

update loan set return_condition = 'GOOD' where return_date is not null;

alter table loan
    add constraint loan_return_condition_values check (return_condition in ('GOOD', 'DAMAGED', 'LOST')),
    add constraint loan_return_condition_matches_return check ((return_date is null) = (return_condition is null));
