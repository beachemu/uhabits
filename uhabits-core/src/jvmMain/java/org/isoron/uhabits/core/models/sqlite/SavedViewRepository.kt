/*
 * Copyright (C) 2016-2021 Álinson Santos Xavier <git@axavier.org>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.isoron.uhabits.core.models.sqlite

import org.isoron.uhabits.core.database.Database
import org.isoron.uhabits.core.database.Repository
import org.isoron.uhabits.core.models.SavedView
import org.isoron.uhabits.core.models.sqlite.records.SavedViewRecord

class SavedViewRepository(private val db: Database) {
    private val repository: Repository<SavedViewRecord> =
        Repository(SavedViewRecord::class.java, db)

    fun find(id: Long): SavedView? {
        val record = repository.find(id) ?: return null
        val view = SavedView()
        record.copyTo(view)
        view.selectedSubcategoryIds = loadJoinIds(
            "saved_view_subcategories",
            "subcategory_id",
            id
        )
        view.selectedCategoryIds = loadJoinIds(
            "saved_view_categories",
            "category_id",
            id
        )
        return view
    }

    fun findAll(): List<SavedView> {
        val records = repository.findAll("order by position")
        if (records.isEmpty()) return emptyList()
        val subsByView = loadAllJoinRows("saved_view_subcategories", "subcategory_id")
        val catsByView = loadAllJoinRows("saved_view_categories", "category_id")
        return records.map { record ->
            val view = SavedView()
            record.copyTo(view)
            view.selectedSubcategoryIds = subsByView[record.id] ?: emptySet()
            view.selectedCategoryIds = catsByView[record.id] ?: emptySet()
            view
        }
    }

    fun save(view: SavedView) {
        repository.executeAsTransaction {
            val record = SavedViewRecord()
            record.copyFrom(view)
            repository.save(record)
            view.id = record.id
            val viewId = record.id!!
            db.delete("saved_view_subcategories", "saved_view_id=?", viewId.toString())
            for (subcategoryId in view.selectedSubcategoryIds) {
                db.insert(
                    "saved_view_subcategories",
                    mapOf(
                        "saved_view_id" to viewId,
                        "subcategory_id" to subcategoryId
                    )
                )
            }
            db.delete("saved_view_categories", "saved_view_id=?", viewId.toString())
            for (categoryId in view.selectedCategoryIds) {
                db.insert(
                    "saved_view_categories",
                    mapOf(
                        "saved_view_id" to viewId,
                        "category_id" to categoryId
                    )
                )
            }
        }
    }

    fun remove(view: SavedView) {
        val id = view.id ?: return
        repository.executeAsTransaction {
            db.delete("saved_view_subcategories", "saved_view_id=?", id.toString())
            db.delete("saved_view_categories", "saved_view_id=?", id.toString())
            db.execute("delete from saved_views where id=?", id)
            view.id = null
        }
    }

    private fun loadJoinIds(table: String, idColumn: String, savedViewId: Long): Set<Long> {
        val ids = mutableSetOf<Long>()
        db.query(
            "select $idColumn from $table where saved_view_id=?",
            savedViewId.toString()
        ).use { c ->
            while (c.moveToNext()) ids.add(c.getLong(0)!!)
        }
        return ids
    }

    private fun loadAllJoinRows(table: String, idColumn: String): Map<Long, Set<Long>> {
        val byView = mutableMapOf<Long, MutableSet<Long>>()
        db.query("select saved_view_id, $idColumn from $table").use { c ->
            while (c.moveToNext()) {
                val viewId = c.getLong(0)!!
                val rowId = c.getLong(1)!!
                byView.getOrPut(viewId) { mutableSetOf() }.add(rowId)
            }
        }
        return byView
    }
}
