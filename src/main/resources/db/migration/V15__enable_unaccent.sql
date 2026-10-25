-- RF05: the search of books (title, author) and of authors (name) ignores accents, so "jose" finds "José" and
-- "garcia marquez" finds "García Márquez". The unaccent extension of PostgreSQL does it; it is "trusted" since
-- PostgreSQL 13, so the owner of the database can create it without being a superuser. It comes with the contrib
-- modules, which the official postgres image has. The search is not indexed (it looks for a piece of the text), and
-- unaccent does not change that.
create extension if not exists unaccent;
