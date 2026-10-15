-- RF08: lockout after repeated failed logins. The user module counts the wrong
-- passwords (failed_login_attempts) and, when the limit is reached, blocks the
-- user until a given instant (blocked_until). Null means not blocked. The block
-- ends by itself when that instant passes, so nothing has to run in the
-- background, and a person cannot be locked out forever by someone guessing
-- passwords. The V3 migration is not edited: a new version adds the columns.

alter table app_user
    add column failed_login_attempts integer     not null default 0,
    add column blocked_until         timestamptz;

alter table app_user
    add constraint app_user_failed_login_attempts_not_negative check (failed_login_attempts >= 0);
