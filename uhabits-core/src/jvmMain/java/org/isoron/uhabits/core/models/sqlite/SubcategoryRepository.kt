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
import org.isoron.uhabits.core.models.Subcategory
import org.isoron.uhabits.core.models.sqlite.records.SubcategoryRecord

class SubcategoryRepository(private val db: Database) {
    private val repository: Repository<SubcategoryRecord> =
        Repository(SubcategoryRecord::class.java, db)

    fun find(id: Long): Subcategory? {
        val record = repository.find(id) ?: return null
        val subcategory = Subcategory(categoryId = record.categoryId!!)
        record.copyTo(subcategory)
        return subcategory
    }

    fun findAll(): List<Subcategory> {
        return repository.findAll("order by category_id, position").map { record ->
            val subcategory = Subcategory(categoryId = record.categoryId!!)
            record.copyTo(subcategory)
            subcategory
        }
    }

    fun findByCategory(categoryId: Long): List<Subcategory> {
        return repository.findAll(
            "where category_id=? order by position",
            categoryId.toString()
        ).map { record ->
            val subcategory = Subcategory(categoryId = record.categoryId!!)
            record.copyTo(subcategory)
            subcategory
        }
    }

    fun save(subcategory: Subcategory) {
        val record = SubcategoryRecord()
        record.copyFrom(subcategory)
        repository.save(record)
        subcategory.id = record.id
    }

    fun remove(subcategory: Subcategory) {
        val id = subcategory.id ?: return
        repository.executeAsTransaction {
            db.execute("update habits set subcategory_id=null where subcategory_id=?", id)
            db.execute("delete from subcategories where id=?", id)
            subcategory.id = null
        }
    }
}
