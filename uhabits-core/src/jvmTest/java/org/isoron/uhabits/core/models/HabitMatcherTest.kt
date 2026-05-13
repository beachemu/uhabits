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
package org.isoron.uhabits.core.models

import org.isoron.uhabits.core.BaseUnitTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HabitMatcherTest : BaseUnitTest() {

    private fun habit(subcategoryId: Long? = null): Habit {
        val h = fixtures.createEmptyHabit()
        h.subcategoryId = subcategoryId
        return h
    }

    @Test
    fun defaultMatcher_matchesAnySubcategory() {
        val matcher = HabitMatcher()
        assertTrue(matcher.matches(habit(subcategoryId = null)))
        assertTrue(matcher.matches(habit(subcategoryId = 5L)))
    }

    @Test
    fun subcategoryFilter_rejectsHabitNotInSet() {
        val matcher = HabitMatcher(selectedSubcategoryIds = setOf(5L))
        assertTrue(matcher.matches(habit(subcategoryId = 5L)))
        assertFalse(matcher.matches(habit(subcategoryId = 7L)))
    }

    @Test
    fun subcategoryFilter_hidesUncategorisedByDefault() {
        val matcher = HabitMatcher(selectedSubcategoryIds = setOf(5L))
        assertFalse(matcher.matches(habit(subcategoryId = null)))
    }

    @Test
    fun subcategoryFilter_includesUncategorisedWhenFlagged() {
        val matcher = HabitMatcher(
            selectedSubcategoryIds = setOf(5L),
            includeUncategorised = true
        )
        assertTrue(matcher.matches(habit(subcategoryId = null)))
        assertTrue(matcher.matches(habit(subcategoryId = 5L)))
        assertFalse(matcher.matches(habit(subcategoryId = 7L)))
    }

    @Test
    fun emptySelection_rejectsAllNonUncategorised() {
        val matcher = HabitMatcher(selectedSubcategoryIds = emptySet())
        assertFalse(matcher.matches(habit(subcategoryId = 5L)))
        assertFalse(matcher.matches(habit(subcategoryId = null)))
    }
}
