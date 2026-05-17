# Plan — categories branch clean-up

## Critical (bugs that affect correctness or cause data loss)

- [ ] Fix `defaultSecondaryOrder` returning wrong value
  - `Preferences.kt:81` — catch block sets storage to `BY_NAME_ASC` but return expression is `BY_POSITION`
  - Change line 81 to `HabitList.Order.BY_NAME_ASC`
  - Note: the same pattern in `defaultPrimaryOrder` (line 53) is *correct* — `BY_POSITION` in both places

- [ ] Clean orphan rows in join tables on category/subcategory delete
  - `CategoryRepository.remove` (lines 54–64): must delete from `saved_view_subcategories` (where subcategory_id IN (select id from subcategories where category_id=?)) and `saved_view_categories` (where category_id=?) before the delete statements
  - `SubcategoryRepository.remove` (lines 64–69): must delete from `saved_view_subcategories` (where subcategory_id=?) alongside the `update habits` + `delete from subcategories`
  - Add a migration test (or repo test) that creates a saved view referencing the deleted entity, deletes it, and asserts the join tables are clean

- [ ] Guard against orphan subcategory assignment
  - `AssignCategoryToHabitsCommand.kt:30`: add `require(newSubcategoryId == null || newCategoryId != null)` at top of `run()` — you cannot assign a subcategory without a category
  - Ideally: also validate the subcategory actually belongs to the given category, but that requires injecting `SubcategoryRepository`; at minimum add the null guard

- [ ] Fix edit commands silently no-opping on missing entity
  - `EditCategoryCommand.kt:32` and `EditSubcategoryCommand.kt:32` — follow `EditHabitCommand`'s pattern and throw a domain exception (e.g. `CategoryNotFoundException`, `SubcategoryNotFoundException`) instead of silently `?: return`
  - Define those exception classes if they don't exist

- [ ] Fix memory leak: `ListHabitsMenuBehavior` never removes its listener from `HabitListFilterState`
  - `ListHabitsMenuBehavior.kt:160` calls `filterState.addListener(filterListener)` in `init`, but `removeListener` is never called
  - `HabitListFilterState` already has `removeListener` (line 66–68) — we just need something to call it
  - Add a `destroy()` method to `ListHabitsMenuBehavior` and call it from `ListHabitsActivity.onDestroy()` (or wherever lifecycle cleanup happens for the menu behavior)

- [ ] Fix saved view CATEGORY sort direction silently discarded
  - `SavedViewSortMapping.kt:40`: `CATEGORY → BY_POSITION` ignores `direction`; `orderToSavedViewSort` at line 54 always maps back to `(CATEGORY, ASC)`, corrupting `DESC` on round-trip
  - Short-term: either add a `BY_POSITION_DESC` to `HabitList.Order` with reverse-position logic in the habit list, or prevent saving `CATEGORY + DESC` by only offering ASC for category-based sorting in the UI
  - At minimum: log a warning when `savedViewSortToOrder` encounters `CATEGORY + DESC` so the data loss is visible during development

## Important (tests, accessibility, and robustness)

- [ ] Add command tests
  - No tests exist for any of the 9 new commands (`CreateCategoryCommand`, `EditCategoryCommand`, `DeleteCategoryCommand`, `ReorderCategoriesCommand`, `CreateSubcategoryCommand`, `EditSubcategoryCommand`, `DeleteSubcategoryCommand`, `ReorderSubcategoriesCommand`, `AssignCategoryToHabitsCommand`)
  - Follow existing pattern in `uhabits-core/src/jvmTest/.../commands/` (e.g. `CreateHabitCommandTest.kt`, `EditHabitCommandTest.kt`)
  - At minimum: create, edit, delete (with cascade), reorder, assign-category with null-guard edge cases

- [ ] Add CSV category/subcategory export test
  - `ExportCSVTaskFactory` is untested — no test passes a real `resolveCategory` lambda
  - Extend `HabitListTest.testWriteCSV` or `HabitsCSVExporterTest.testExportCSV` to create a category/subcategory, assign to a habit, export, and assert "Health,Exercise" (or equivalent) appears in the CSV columns
  - Update `csv_export/Habits.csv` test asset accordingly (or create a second asset file)

- [ ] Add `HabitMatcher.selectedCategoryIds` test coverage
  - `HabitMatcherTest` only tests `selectedSubcategoryIds`; no test for `selectedCategoryIds` alone or in combination
  - Add tests for: habit with only `categoryId` matching via `selectedCategoryIds`; habit with `subcategoryId` NOT matching via `selectedCategoryIds` alone

- [ ] Add accessibility labels to raw glyph controls
  - `NavigationDrawerContent.kt:221–224`: expand/collapse arrows "▾"/"▸" — add `Modifier.semantics { contentDescription = "Expand category" }` (or "Collapse")
  - `NavigationDrawerContent.kt:369–371`: "⋮" overflow menu trigger — add content description "More options"
  - `ManageCategoriesScreen.kt:458–464`: same "⋮" overflow trigger — same fix
  - `ManageCategoriesScreen.kt:489–506` (`IconText`): remove `@Suppress("UNUSED_PARAMETER")` and apply `contentDescription` to the `Text` via `.semantics { contentDescription = contentDescription }` so the reorder "▲"/"▼" arrows are accessible

- [ ] Add `Log.w` in null-ID guard branches in `ManageCategoriesScreen`
  - Lines 159, 178, 196 (`return@CategoryCard`) and lines 254–260, 268–275 (silent no-op on edit confirm) — add `Log.w("ManageCategoriesScreen", "Unexpected null ID in ...")` so these don't go undetected if a data-layer bug ever triggers them

- [ ] Fix `SubcategoryRepositoryTest.testRemove` — assert the subcategory row is actually deleted
  - Capture `sub.id!!` before removal, then `assertNull(repo.find(subId))`
  - Same pattern already exists in `CategoryRepositoryTest.testRemove`

- [ ] Fix `CategoryRepositoryTest.testRemove` hardcoded ID
  - Line 84 uses `repo.find(1L)` — capture `categoryId` from the saved entity before removal and use that

## Simplifications (no functional change, less code)

- [ ] Merge `ReorderCategoriesCommand` and `ReorderSubcategoriesCommand`
  - Extract the shared algorithm into a private function or an abstract base class — the only difference is `repository.findAll()` vs `repository.findByCategory(categoryId)`
  - ~15 lines duplicated in each

- [ ] Remove dead constructor arg in `SubcategoryRepository`
  - `SubcategoryRepository.kt:32, 39, 50`: pass `categoryId` to `Subcategory()` constructor, then immediately overwrite with `record.copyTo(subcategory)`
  - Either drop the constructor arg or drop the `copyTo` call and manually assign remaining fields — the double-write is confusing

- [ ] Remove unnecessary `.toSet()` calls
  - `HabitListFilterState.kt:53–54`: parameters are already `Set<Long>`, so `.toSet()` is a no-op defensive copy — remove, or add a comment explaining intent

- [ ] Use `Storage.getLongArray`/`putLongArray` instead of raw `splitLongs`/`joinLongs`
  - `Preferences.kt:59–68`: `lastSelectedSubcategoryIds` and `lastSelectedCategoryIds` do manual string splitting/joining that the `Storage` interface already provides via `getLongArray`/`putLongArray`
  - Switch to `storage.getLongArray(key, longArrayOf())` / `storage.putLongArray(key, value.toLongArray())` to match how `WidgetPreferences` already does it

- [ ] Make `SubcategoryRecord.categoryId` non-null to match the model
  - `SubcategoryRecord.kt:32`: `var categoryId: Long?` but `Subcategory.kt:23` declares it non-null
  - Change the record field to `Long` (drop the `?`), update `copyTo`/`copyFrom` to match, and add `NOT NULL` to migration `26.sql`
  - This eliminates 3 `!!` force-unwraps in `SubcategoryRepository` and one in `SubcategoryRecord.copyTo`

## Nice-to-have (cosmetic, defensive, or minor)

- [ ] `ManageCategoriesScreen` hardcoded `PALETTE_SIZE = 20` — derive from the palette resource array length if possible, or add a comment noting the assumption

- [ ] `UhabitsTheme.kt:44` instantiates `StyledResources(context)` on every recomposition — wrap in `remember` to avoid repeated attribute fetching

- [ ] `NavigationDrawerView` lines 68–78 / `ListHabitsActivity` lines 141–144: extract the `UNCATEGORISED_ID ∈ selectedSubs ⇔ includeUncategorised` mapping into a small helper to avoid manual synchronisation between two sites

- [ ] `MigrationHelper` / `27.sql` vs `28.sql`: `SavedViewRepository` queries `saved_view_categories` (created in 28) but could theoretically run against a DB at version 27 — add a schema-version guard or merge the two migrations

- [ ] Check `Storage.getString` default value for `lastSelectedSubcategoryIds` — the `splitLongs`/`getLongArray` approach handles empty strings, but verify the empty-string sentinel doesn't collide with a legitimate stored empty list
