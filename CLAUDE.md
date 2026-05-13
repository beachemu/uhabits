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
- **Migrations** — SQL files in `uhabits-core/src/jvmMain/resources/migrations/NN.sql`, run sequentially. Current head is `27.sql` (`26.sql` adds `categories` / `subcategories` and `habits.subcategory_id`; `27.sql` adds `saved_views` and the `saved_view_subcategories` join). The next migration is `28.sql`. Each file should be idempotent-aware (it only runs once but should not assume prior state beyond what previous migrations established). Bump the schema/version constant if there is one (search for the matching literal in code if unsure — `Constants.kt` and `DatabaseOpener.kt` are likely sites).
- **Commands** (`core/commands/`) — every mutating user action is a `Command` (e.g. `CreateHabitCommand`, `EditHabitCommand`). Routed through `CommandRunner` so the UI can observe + undo. Anything that mutates habit/category state from the UI should go through a new command, not a direct repository call.
- **Preferences** (`core/preferences/Preferences.kt` + `PropertiesStorage`) — simple key/value, used for things like current sort order. Lightweight UI state (e.g. last-applied filter) can live here.
- **IO** (`core/io/`) — `HabitsCSVExporter`, `LoopDBImporter`, etc. CSV export currently writes habit fields only; extending it to include category/subcategory names is part of the planned work.

### Categories, subcategories & saved views (data layer)

Landed on the `categories` branch. The UI side is partially wired (navigation drawer renders these; filter application and edit-habit picker are still pending).

- **Models** (`core/models/`):
  - `Category(id, name, color: PaletteColor, position)`.
  - `Subcategory(id, categoryId, name, color: PaletteColor, position)`.
  - `SavedView(id, name, selectedSubcategoryIds: Set<Long>, includeUncategorised, sortField, sortDirection, position)` with enums `SavedViewSortField { NAME, SCORE, STREAK, COLOR, CATEGORY }` and `SavedViewSortDirection { ASC, DESC }`.
- **Habit** now has `var subcategoryId: Long?` (null = uncategorised); copied through `HabitRecord.copyFrom/copyTo`.
- **Records** (`models/sqlite/records/`): `CategoryRecord`, `SubcategoryRecord`, `SavedViewRecord`. The saved-view ↔ subcategory link is a normalised join table `saved_view_subcategories` (composite PK).
- **Repositories** (`models/sqlite/`): `CategoryRepository`, `SubcategoryRepository`, `SavedViewRepository` — each constructed from a `Database`, wrapping the generic `Repository<T>`. `SubcategoryRepository.findByCategory(categoryId)` is provided. Removing a category nulls `habit.subcategoryId` for affected habits in the same transaction (cascade is enforced in code, not via SQLite FKs). `SavedViewRepository.save` is transactional: it updates the view row then deletes & re-inserts join rows. No observer/listener support — callers manage refresh.
- **Filtering**: `HabitMatcher` has `selectedSubcategoryIds: Set<Long>?` and `includeUncategorised: Boolean`. `null` selectedSubcategoryIds means "no category filter". Currently applied through `HabitMatcher.matches(habit)`; integration into `SQLiteHabitList`'s SQL WHERE clause (so paging stays efficient) is still TODO.
- **DI**: the three repositories are provided as `@AppScope` singletons in `inject/HabitsModule.kt` and exposed on `HabitsApplicationComponent`. No `Command` subclasses exist yet for categories/subcategories/saved-views, and no observable filter-state holder exists yet.

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

## Planned work — categories, subcategories, saved views (UI)

Active feature on this branch. The data layer (see "Categories, subcategories & saved views (data layer)" above) and a navigation drawer that renders categories/subcategories/saved views have landed. The remaining UI scope:

- **Filter wiring** — the drawer currently keeps checkbox state locally (`NavigationDrawerView.selectedSubcategoryIds`) and exposes `onSelectionChanged` / `onSavedViewTapped` callbacks that are no-ops. Still needed: an observable filter-state holder (selected subcategory ids + uncategorised flag) injected via Dagger; the habit list view subscribes and re-queries when the set changes (either by extending `HabitMatcher` usage in `SQLiteHabitList` or via a `FilteredHabitList` decorator).
- **Saved-view tap** — applies the view's filter + sort in one go; needs the filter holder above.
- **"Save current view"** — drawer entry/dialog that snapshots checkbox state + sort, prompts for a name, persists via `SavedViewRepository`.
- **"Manage categories" screen** — list with create/rename/reorder/delete for categories and their subcategories. Reorder uses `position`. Reachable from the drawer and/or settings.
- **Edit-habit picker** — cascading category → subcategory picker in `activities/habits/edit/` (both clearable for uncategorised).
- **Sort selector** — toolbar control over fields {name, score, streak, color, category}, with default unchanged.
- **CSV / SQLite export** — `HabitsCSVExporter` and the DB export need new columns/joins so exports include category and subcategory names.
- **Uncategorised semantics** — uncategorised habits (null `subcategoryId`) appear when nothing is filtered. When any filter is active, uncategorised habits are hidden unless the "Uncategorised" pseudo-row is checked. The drawer represents this with sentinel id `-1L` (`UNCATEGORISED_ID` in `NavigationDrawerView.kt`) — the eventual filter holder should mirror that.
- **Commands** — no `Command` subclasses exist yet for category/subcategory/saved-view mutations. User-driven mutations from the manage-categories screen should go through `CommandRunner` (per the existing convention) rather than calling repositories directly.

## Things to be careful about

- Don't add Android imports to `uhabits-core`. If something seems to need it, it probably belongs in `uhabits-android` (e.g. a new repository wrapper, while the core class stays pure).
- Always go through `CommandRunner` for mutations the UI should be able to observe/undo. Direct repository writes are fine for one-off setup paths but not for user-driven actions.
- Migrations are append-only and never edited after release. If a migration ships and turns out to be wrong, fix forward with a new migration.
- Schema changes need to be reflected in three places: the migration SQL, the `*Record` class, and any model translation code.
