create table saved_views (
    id integer primary key autoincrement,
    name text,
    include_uncategorised integer not null default 0,
    sort_field text not null default 'NAME',
    sort_direction text not null default 'ASC',
    position integer not null default 0
);

create table saved_view_subcategories (
    saved_view_id integer not null,
    subcategory_id integer not null,
    primary key (saved_view_id, subcategory_id)
);

create index idx_saved_view_subcategories_view
    on saved_view_subcategories(saved_view_id);
