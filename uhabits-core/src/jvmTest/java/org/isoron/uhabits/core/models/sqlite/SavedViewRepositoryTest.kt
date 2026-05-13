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
import org.isoron.uhabits.core.models.SavedView
import org.isoron.uhabits.core.models.SavedViewSortDirection
import org.isoron.uhabits.core.models.SavedViewSortField
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SavedViewRepositoryTest : BaseUnitTest() {
    private lateinit var db: Database
    private lateinit var repo: SavedViewRepository

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        db = buildMemoryDatabase()
        repo = SavedViewRepository(db)
    }

    @Test
    fun testSaveAndFind_roundTripsAllFields() {
        val view = SavedView(
            name = "Morning routine",
            selectedSubcategoryIds = setOf(1L, 2L, 5L),
            includeUncategorised = true,
            sortField = SavedViewSortField.STREAK,
            sortDirection = SavedViewSortDirection.DESC,
            position = 3
        )
        repo.save(view)
        assertNotNull(view.id)

        val loaded = repo.find(view.id!!)!!
        assertThat(loaded.name, equalTo("Morning routine"))
        assertThat(loaded.selectedSubcategoryIds, equalTo(setOf(1L, 2L, 5L)))
        assertThat(loaded.includeUncategorised, equalTo(true))
        assertThat(loaded.sortField, equalTo(SavedViewSortField.STREAK))
        assertThat(loaded.sortDirection, equalTo(SavedViewSortDirection.DESC))
        assertThat(loaded.position, equalTo(3))
    }

    @Test
    fun testFindAll_returnsViewsOrderedByPosition() {
        repo.save(SavedView(name = "B", position = 2))
        repo.save(SavedView(name = "A", position = 0))
        repo.save(SavedView(name = "C", position = 1))

        val all = repo.findAll()
        assertThat(all.map { it.name }, equalTo(listOf("A", "C", "B")))
    }

    @Test
    fun testSave_rewritesJoinRowsOnUpdate() {
        val view = SavedView(
            name = "X",
            selectedSubcategoryIds = setOf(10L, 20L, 30L)
        )
        repo.save(view)

        view.selectedSubcategoryIds = setOf(20L, 40L)
        repo.save(view)

        val loaded = repo.find(view.id!!)!!
        assertThat(loaded.selectedSubcategoryIds, equalTo(setOf(20L, 40L)))
    }

    @Test
    fun testRemove_clearsRowAndJoinRows() {
        val view = SavedView(
            name = "X",
            selectedSubcategoryIds = setOf(7L, 8L)
        )
        repo.save(view)
        val id = view.id!!

        repo.remove(view)
        assertNull(view.id)
        assertNull(repo.find(id))

        db.query(
            "select count(*) from saved_view_subcategories where saved_view_id=?",
            id.toString()
        ).use { c ->
            c.moveToNext()
            assertThat(c.getInt(0)!!, equalTo(0))
        }
    }
}
