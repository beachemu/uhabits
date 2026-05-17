create table categories (
    id integer primary key autoincrement,
    name text,
    color integer,
    position integer
);

create table subcategories (
    id integer primary key autoincrement,
    category_id integer not null,
    name text,
    color integer,
    position integer
);

alter table habits add column subcategory_id integer;
