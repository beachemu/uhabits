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

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.database.Database
import org.isoron.uhabits.core.models.Category
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.models.Subcategory
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CategoryRepositoryTest : BaseUnitTest() {
    private lateinit var db: Database
    private lateinit var repo: CategoryRepository
    private lateinit var subRepo: SubcategoryRepository

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        db = buildMemoryDatabase()
        repo = CategoryRepository(db)
        subRepo = SubcategoryRepository(db)
    }

    @Test
    fun testSaveAndFind_roundTripsAllFields() {
        val category = Category(
            name = "Health",
            color = PaletteColor(4),
            position = 2
        )
        repo.save(category)
        assertNotNull(category.id)

        val loaded = repo.find(category.id!!)!!
        assertThat(loaded.name, equalTo("Health"))
        assertThat(loaded.color.paletteIndex, equalTo(4))
        assertThat(loaded.position, equalTo(2))
    }

    @Test
    fun testFindAll_orderedByPosition() {
        repo.save(Category(name = "B", position = 2))
        repo.save(Category(name = "A", position = 0))
        repo.save(Category(name = "C", position = 1))

        val all = repo.findAll()
        assertThat(all.map { it.name }, equalTo(listOf("A", "C", "B")))
    }

    @Test
    fun testRemove_cascadesNullingHabitsAndDeletesSubcategories() {
        val category = Category(name = "Work", position = 0)
        repo.save(category)
        val sub = Subcategory(categoryId = category.id!!, name = "Email", position = 0)
        subRepo.save(sub)
        db.execute("insert into habits (id, subcategory_id) values (?, ?)", 100L, sub.id!!)

        repo.remove(category)

        assertNull(category.id)
        assertNull(repo.find(1L))
        assertThat(subRepo.findAll().size, equalTo(0))
        db.query(
            "select subcategory_id from habits where id=?",
            "100"
        ).use { c ->
            c.moveToNext()
            assertNull(c.getLong(0))
        }
    }
}
