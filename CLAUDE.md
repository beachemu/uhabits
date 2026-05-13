# CLAUDE.md

Loop Habit Tracker (uhabits) — Android app for tracking habits. This file documents the parts of the codebase relevant to active work and is intended as ambient context for Claude. For build/test/style basics see `docs/BUILD.md`, `docs/TEST.md`, `docs/GUIDELINES.md`.

## Modules

- `uhabits-core` — pure-Kotlin/JVM library. Models, commands, repositories, SQLite access, import/export, preferences. Multiplatform layout (`src/jvmMain`, `src/commonMain`, `jvmTest`, `commonTest`); almost all code lives in `jvmMain`. No Android dependencies — keep it that way.
- `uhabits-android` — Android app. UI, DI (Dagger), intents, widgets, notifications. Depends on `uhabits-core`.

Package root in both: `org.isoron.uhabits` (core under `…uhabits.core`).

## Core architecture

- **Models** (`uhabits-core/.../models/`) — `Habit` is the central entity (Kotlin `data class`). `HabitList` is an abstract ordered collection; SQLite-backed impl is `models/sqlite/SQLiteHabitList.kt`. Each habit has `computedEntries`, `originalEntries`, `scores`, `streaks` lists. `HabitMatcher` is the existing filter object passed to `HabitList`.
- **SQLite records** (`models/sqlite/records/`) — `HabitRecord`, `EntryRecord`. Plain classes annotated with `@Table` / `@field:Column` (see `database/Column.kt`, `Table.kt`). `copyFrom(model)` / `copyTo(model)` translate between domain model and record. New entities should follow this same record pattern.
- **Repository** (`database/Repository.kt`) — generic CRUD over annotated records (`find`, `findAll`, `findFirst`, `save`, `remove`, `execSQL`). Construct as `Repository(HabitRecord::class.java, db)`. Use this rather than hand-written SQL where possible.
- **Database** (`database/Database.kt`, `JdbcDatabase.kt`, `MigrationHelper.kt`) — JDBC on JVM, Android SQLite via `HabitsDatabaseOpener` in `uhabits-android`. Same schema and migrations are used on both sides.
- **Migrations** — SQL files in `uhabits-core/src/jvmMain/resources/migrations/NN.sql`, run sequentially. Current head is `25.sql`. The next migration is `26.sql`. Each file should be idempotent-aware (it only runs once but should not assume prior state beyond what previous migrations established). Bump the schema/version constant if there is one (search for the `25` literal in code if unsure — `Constants.kt` and `DatabaseOpener.kt` are likely sites).
- **Commands** (`core/commands/`) — every mutating user action is a `Command` (e.g. `CreateHabitCommand`, `EditHabitCommand`). Routed through `CommandRunner` so the UI can observe + undo. Anything that mutates habit/category state from the UI should go through a new command, not a direct repository call.
- **Preferences** (`core/preferences/Preferences.kt` + `PropertiesStorage`) — simple key/value, used for things like current sort order. Lightweight UI state (e.g. last-applied filter) can live here.
- **IO** (`core/io/`) — `HabitsCSVExporter`, `LoopDBImporter`, etc. CSV export currently writes habit fields only; extending it to include category/subcategory names is part of the planned work.

## Android architecture

- DI: Dagger components under `uhabits-android/.../inject/` (`HabitsApplicationComponent`, `HabitsActivityComponent`, modules per scope). Wire new singletons/repos through the appropriate module.
- Activities live under `activities/` with sub-packages `habits/list`, `habits/edit`, `habits/show`, `about`, `settings`, `intro`, `common`.
- The main screen is `activities/habits/list/ListHabitsActivity.kt` with `ListHabitsRootView` / `ListHabitsScreen` / `ListHabitsMenu`. Views are mostly programmatic Kotlin (not XML), composed from `common/views/`. The drawer work attaches at this layer — wrap the existing root view in a `DrawerLayout` rather than rebuilding the screen.
- Edit-habit form is in `activities/habits/edit/` — the category/subcategory picker drops in here.
- Settings screen uses Android `Preference` APIs under `activities/settings/`. A "Manage categories" entry point can be added either there or from the drawer.

## Code style & conventions

- New code is Kotlin. Pure data containers preferred for models; behaviour on the side via repositories/commands.
- ktlint default style, enforced by CI (`./gradlew ktlintCheck`). Format with `./gradlew ktlintFormat` before committing.
- Run tests with `./gradlew test` (core JVM tests are fast; Android tests are slower). See `docs/TEST.md`.
- GPL-3.0 header on every new source file (copy from any existing file; update year/author as appropriate).
- Branching: git-flow, PRs target `dev`. Current branch is `categories`.

## Planned work — categories, subcategories, saved views

Active feature on this branch. The user has described the scope; design notes below for continuity. None of this is implemented yet.

### Data layer (`uhabits-core`)

- New models in `core/models/`: `Category(id, name, color, position)`, `Subcategory(id, name, color, position, categoryId)`, `SavedView(id, name, selectedSubcategoryIds: Set<Long>, sortField, sortDirection)`. Match the `Habit` data-class style; mirror `position` semantics already used for habit ordering.
- Add nullable `subcategoryId: Long?` to `Habit` and `HabitRecord` (`@field:Column(name = "subcategory_id")`). Null = uncategorised.
- New records in `models/sqlite/records/`: `CategoryRecord`, `SubcategoryRecord`, `SavedViewRecord` (+ a join record for saved-view ↔ subcategory if normalised; otherwise a JSON column on `SavedViewRecord`). Normalised join is more idiomatic given the existing schema — prefer it unless storage simplicity dominates.
- New repositories — instantiate `Repository(CategoryRecord::class.java, db)` etc. Wrap in `CategoryRepository` / `SubcategoryRepository` / `SavedViewRepository` classes that own domain↔record translation, the same way `SQLiteHabitList` wraps the generic repo.
- Extend the habit-list query path so it can be filtered by a `Set<Long>` of subcategory ids. Likely options: (a) add a field to `HabitMatcher` and the `SQLiteHabitList` WHERE clause, or (b) introduce a new `FilteredHabitList` decorator. (a) keeps the existing matcher contract; (b) is cleaner for reactive observation. Decide when implementing — both fit.

### Migration

- `uhabits-core/src/jvmMain/resources/migrations/26.sql` adding `categories`, `subcategories`, `saved_views` (and `saved_view_subcategories` join if normalised) and `alter table habits add column subcategory_id integer references subcategories(id) on delete set null`.
- The ON DELETE SET NULL handles requirement (11) at the DB level — but SQLite needs `PRAGMA foreign_keys=ON` to enforce it, and the existing schema does not declare FKs elsewhere. Safer to enforce the null-out in the delete command rather than relying on the pragma.

### UI (`uhabits-android`)

- Wrap `ListHabitsRootView` in `androidx.drawerlayout.widget.DrawerLayout`. Drawer content: expandable category list (each subcategory row a checkbox) + saved-views list + "Save current view" + "Manage categories" links.
- Filter state: a small observable holder (set of selected subcategory ids + uncategorised flag) injected via Dagger. The habit list view subscribes and re-queries through the repository when the set changes.
- "Save current view" snapshots the current checkbox state + sort and prompts for a name via a dialog, then persists via `SavedViewRepository`. Tapping a saved view applies its filter and sort in one go.
- Edit-habit screen gets a category → subcategory picker (cascading; subcategory list updates when category changes; both clearable for uncategorised).
- "Manage categories" screen: simple list view with create/rename/reorder/delete for categories and their subcategories. Reorder uses `position` like habits do.
- Sort selector in the toolbar — fields: name, score, streak, color, category. Default to whatever Loop currently uses (find via `Preferences` / current sort wiring; do not change the default).

### Edge cases

- Uncategorised habits (null `subcategoryId`) appear when nothing is filtered. When the user selects any filter, uncategorised habits are hidden unless an explicit "Uncategorised" pseudo-row is checked. Wire that as a sentinel value (e.g. `null` or `-1L`) inside the selected-id set.
- Deleting a category/subcategory: null out `subcategoryId` on affected habits in the same transaction as the delete; do not delete habits.
- CSV export (`HabitsCSVExporter`) and SQLite export need new columns/joins so exports include category and subcategory names.

## Things to be careful about

- Don't add Android imports to `uhabits-core`. If something seems to need it, it probably belongs in `uhabits-android` (e.g. a new repository wrapper, while the core class stays pure).
- Always go through `CommandRunner` for mutations the UI should be able to observe/undo. Direct repository writes are fine for one-off setup paths but not for user-driven actions.
- Migrations are append-only and never edited after release. If a migration ships and turns out to be wrong, fix forward with a new migration.
- Schema changes need to be reflected in three places: the migration SQL, the `*Record` class, and any model translation code.
