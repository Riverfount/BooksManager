-- RN01: the ISBN-10 and the ISBN-13 of one book are the same ISBN, so it is stored as the ISBN-13 (prefix 978 and a
-- new check digit). Without this, the two forms of the same book were two books, and the unique constraint could not
-- see the duplicate.

-- The ISBN-13 of each ISBN-10 already stored.
create temporary table isbn_conversion on commit drop as
select b.id,
       b.isbn as isbn10,
       s.body || ((10 - c.total % 10) % 10)::text as isbn13
from book b
cross join lateral (select '978' || substr(b.isbn, 1, 9) as body) s
cross join lateral (
    select sum(substr(s.body, n, 1)::int * case when n % 2 = 1 then 1 else 3 end) as total
    from generate_series(1, 12) n
) c
where length(b.isbn) = 10;

-- The same book stored with both ISBNs: nothing is merged or inactivated on its own. The migration stops and says
-- which books collide, so the owner decides (which one to keep, and what to do with its copies).
do $$
declare
    collisions text;
begin
    select string_agg(format('ISBN-10 %s (livro %s) e ISBN-13 %s (livro %s)', c.isbn10, c.id, other.isbn, other.id),
                      '; ' order by c.id)
      into collisions
      from isbn_conversion c
      join book other on other.isbn = c.isbn13;
    if collisions is not null then
        raise exception 'O mesmo livro está cadastrado com ISBN-10 e com ISBN-13 (RN01): %. Resolva esses livros '
                        '(mantenha um e transfira ou inative o outro) e rode a migração de novo.', collisions;
    end if;
end
$$;

update book b
   set isbn = c.isbn13
  from isbn_conversion c
 where c.id = b.id;

-- From now on the database also refuses anything that is not an ISBN-13.
alter table book
    add constraint book_isbn_13_digits check (isbn ~ '^[0-9]{13}$');
