-- RNF17: optimistic lock on the book and on the author, as the copy already has. Without it, two edits of the same
-- book at the same time (above all the replacement of its authors) ended with "the last to save wins", with no warning;
-- with it, the second one gets a conflict (409) instead of silently replacing what the first saved.
-- The records that already exist start at version 0.
alter table book
    add column version bigint not null default 0;

alter table author
    add column version bigint not null default 0;
