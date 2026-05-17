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
package org.isoron.uhabits.core.ui.screens.habits.list

import org.isoron.uhabits.core.preferences.Preferences
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HabitListFilterStateTest {

    private class InMemoryStorage : Preferences.Storage {
        private val data = mutableMapOf<String, Any>()
        override fun getBoolean(key: String, defValue: Boolean) = data[key] as? Boolean ?: defValue
        override fun getInt(key: String, defValue: Int) = data[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long) = data[key] as? Long ?: defValue
        override fun getString(key: String, defValue: String) = data[key] as? String ?: defValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun putLong(key: String, value: Long) { data[key] = value }
        override fun putString(key: String, value: String) { data[key] = value }
        override fun clear() { data.clear() }
        override fun remove(key: String) { data.remove(key) }
        override fun onAttached(preferences: Preferences) {}
    }

    private fun newState() = HabitListFilterState(Preferences(InMemoryStorage()))

    @Test
    fun initiallyEmpty() {
        val state = newState()
        assertTrue(state.isEmpty)
        assertTrue(state.selectedSubcategoryIds.isEmpty())
        assertFalse(state.includeUncategorised)
    }

    @Test
    fun updateNotifiesOnChange() {
        val state = newState()
        var fired = 0
        state.addListener(
            object : HabitListFilterState.Listener {
                override fun onFilterChanged() {
                    fired++
                }
            }
        )

        state.update(setOf(1L, 2L), includeUncategorised = false)
        assertEquals(1, fired)
        assertEquals(setOf(1L, 2L), state.selectedSubcategoryIds)
        assertFalse(state.isEmpty)

        state.update(setOf(1L, 2L), includeUncategorised = false)
        assertEquals(1, fired)

        state.update(setOf(1L, 2L), includeUncategorised = true)
        assertEquals(2, fired)
        assertTrue(state.includeUncategorised)

        state.update(emptySet(), includeUncategorised = false)
        assertEquals(3, fired)
        assertTrue(state.isEmpty)
    }

    @Test
    fun removeListenerStopsNotifications() {
        val state = newState()
        var fired = 0
        val listener = object : HabitListFilterState.Listener {
            override fun onFilterChanged() {
                fired++
            }
        }
        state.addListener(listener)
        state.update(setOf(1L), includeUncategorised = false)
        assertEquals(1, fired)
        state.removeListener(listener)
        state.update(setOf(2L), includeUncategorised = false)
        assertEquals(1, fired)
    }

    @Test
    fun statePersistsAcrossInstances() {
        val storage = InMemoryStorage()
        val prefs = Preferences(storage)
        val first = HabitListFilterState(prefs)
        first.update(setOf(7L, 9L), includeUncategorised = true)

        val second = HabitListFilterState(Preferences(storage))
        assertEquals(setOf(7L, 9L), second.selectedSubcategoryIds)
        assertTrue(second.includeUncategorised)
    }
}
