-- The name a screen shows for the person who is logged in (the sidebar card). It is
-- optional: a user without it is shown by the login. Only the display name of who
-- operates the system, never a reader's personal data.

alter table app_user
    add column display_name varchar(100);
