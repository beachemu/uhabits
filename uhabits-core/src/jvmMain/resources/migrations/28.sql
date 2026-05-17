alter table habits add column category_id integer;

update habits set category_id = (
    select category_id from subcategories
    where subcategories.id = habits.subcategory_id
) where subcategory_id is not null;

create table saved_view_categories (
    saved_view_id integer not null,
    category_id integer not null,
    primary key (saved_view_id, category_id)
);

create index idx_saved_view_categories_view
    on saved_view_categories(saved_view_id);
