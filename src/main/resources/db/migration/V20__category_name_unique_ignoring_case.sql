-- Category names are compared ignoring case ("Romance" and "ROMANCE" are the same category), so the database enforces
-- the same rule as the application: the plain unique constraint on name is replaced by a unique index on lower(name).
-- Authors are not touched: two real authors can share a name, so that rule stays with the application (RF02, RN19).

alter table category drop constraint category_name_key;
create unique index uq_category_name_lower on category (lower(name));
