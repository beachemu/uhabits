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
        view.selectedSubcategoryIds = loadSubcategoryIds(id)
        return view
    }

    fun findAll(): List<SavedView> {
        val records = repository.findAll("order by position")
        if (records.isEmpty()) return emptyList()
        val joinByView = loadAllJoinRows()
        return records.map { record ->
            val view = SavedView()
            record.copyTo(view)
            view.selectedSubcategoryIds = joinByView[record.id] ?: emptySet()
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
        }
    }

    fun remove(view: SavedView) {
        val id = view.id ?: return
        repository.executeAsTransaction {
            db.delete("saved_view_subcategories", "saved_view_id=?", id.toString())
            db.execute("delete from saved_views where id=?", id)
            view.id = null
        }
    }

    private fun loadSubcategoryIds(savedViewId: Long): Set<Long> {
        val ids = mutableSetOf<Long>()
        db.query(
            "select subcategory_id from saved_view_subcategories where saved_view_id=?",
            savedViewId.toString()
        ).use { c ->
            while (c.moveToNext()) ids.add(c.getLong(0)!!)
        }
        return ids
    }

    private fun loadAllJoinRows(): Map<Long, Set<Long>> {
        val byView = mutableMapOf<Long, MutableSet<Long>>()
        db.query("select saved_view_id, subcategory_id from saved_view_subcategories").use { c ->
            while (c.moveToNext()) {
                val viewId = c.getLong(0)!!
                val subId = c.getLong(1)!!
                byView.getOrPut(viewId) { mutableSetOf() }.add(subId)
            }
        }
        return byView
    }
}
