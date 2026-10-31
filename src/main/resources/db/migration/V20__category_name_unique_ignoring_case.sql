-- Category names are compared ignoring case ("Romance" and "ROMANCE" are the same category), so the database enforces
-- the same rule as the application: the plain unique constraint on name is replaced by a unique index on lower(name).
-- Authors are not touched: two real authors can share a name, so that rule stays with the application (RF02, RN19).

-- Categories that differ only in case: nothing is merged or renamed on its own. The migration stops and says which
-- ones collide, so the owner decides (which one to keep, and what to do with the books of the other).
do $$
declare
    collisions text;
begin
    select string_agg(format('%s (categorias %s)', names, ids), '; ' order by names)
      into collisions
      from (select string_agg(quote_literal(name), ', ' order by id) as names,
                   string_agg(id::text, ', ' order by id) as ids
              from category
             group by lower(name)
            having count(*) > 1) duplicated;
    if collisions is not null then
        raise exception 'Há categorias que só diferem na caixa das letras: %. Una ou renomeie essas categorias '
                        'antes de migrar.', collisions;
    end if;
end
$$;

alter table category drop constraint category_name_key;
create unique index uq_category_name_lower on category (lower(name));
