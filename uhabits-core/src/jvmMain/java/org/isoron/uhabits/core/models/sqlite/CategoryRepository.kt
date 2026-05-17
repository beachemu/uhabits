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
import org.isoron.uhabits.core.models.Category
import org.isoron.uhabits.core.models.sqlite.records.CategoryRecord

class CategoryRepository(private val db: Database) {
    private val repository: Repository<CategoryRecord> =
        Repository(CategoryRecord::class.java, db)

    fun find(id: Long): Category? {
        val record = repository.find(id) ?: return null
        val category = Category()
        record.copyTo(category)
        return category
    }

    fun findAll(): List<Category> {
        return repository.findAll("order by position").map { record ->
            val category = Category()
            record.copyTo(category)
            category
        }
    }

    fun save(category: Category) {
        val record = CategoryRecord()
        record.copyFrom(category)
        repository.save(record)
        category.id = record.id
    }

    fun remove(category: Category) {
        val id = category.id ?: return
        repository.executeAsTransaction {
            db.execute(
                "update habits set subcategory_id=null " +
                    "where subcategory_id in (select id from subcategories where category_id=?)",
                id
            )
            db.execute("update habits set category_id=null where category_id=?", id)
            db.execute("delete from subcategories where category_id=?", id)
            db.execute("delete from categories where id=?", id)
            category.id = null
        }
    }
}
