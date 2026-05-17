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
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.models.Subcategory
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SubcategoryRepositoryTest : BaseUnitTest() {
    private lateinit var db: Database
    private lateinit var repo: SubcategoryRepository

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        db = buildMemoryDatabase()
        repo = SubcategoryRepository(db)
    }

    @Test
    fun testSaveAndFind_roundTripsAllFields() {
        val sub = Subcategory(
            categoryId = 7L,
            name = "Cardio",
            color = PaletteColor(3),
            position = 1
        )
        repo.save(sub)
        assertNotNull(sub.id)

        val loaded = repo.find(sub.id!!)!!
        assertThat(loaded.categoryId, equalTo(7L))
        assertThat(loaded.name, equalTo("Cardio"))
        assertThat(loaded.color.paletteIndex, equalTo(3))
        assertThat(loaded.position, equalTo(1))
    }

    @Test
    fun testFindAll_orderedByCategoryThenPosition() {
        repo.save(Subcategory(categoryId = 2L, name = "B1", position = 0))
        repo.save(Subcategory(categoryId = 1L, name = "A2", position = 1))
        repo.save(Subcategory(categoryId = 1L, name = "A1", position = 0))

        val names = repo.findAll().map { it.name }
        assertThat(names, equalTo(listOf("A1", "A2", "B1")))
    }

    @Test
    fun testFindByCategory_returnsOnlyThatCategoryInOrder() {
        repo.save(Subcategory(categoryId = 1L, name = "A2", position = 1))
        repo.save(Subcategory(categoryId = 1L, name = "A1", position = 0))
        repo.save(Subcategory(categoryId = 2L, name = "B1", position = 0))

        val names = repo.findByCategory(1L).map { it.name }
        assertThat(names, equalTo(listOf("A1", "A2")))
    }

    @Test
    fun testRemove_nullsOutHabitSubcategoryId() {
        val sub = Subcategory(categoryId = 1L, name = "X", position = 0)
        repo.save(sub)
        val subId = sub.id!!
        db.execute("insert into habits (id, subcategory_id) values (?, ?)", 200L, sub.id!!)

        repo.remove(sub)

        assertNull(sub.id)
        assertNull(repo.find(subId))
        db.query("select subcategory_id from habits where id=?", "200").use { c ->
            c.moveToNext()
            assertNull(c.getLong(0))
        }
    }
}
