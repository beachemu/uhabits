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

import org.isoron.uhabits.core.AppScope
import org.isoron.uhabits.core.preferences.Preferences
import javax.inject.Inject

@AppScope
class HabitListFilterState @Inject constructor(
    private val preferences: Preferences
) {
    var selectedSubcategoryIds: Set<Long> = preferences.lastSelectedSubcategoryIds
        private set
    var includeUncategorised: Boolean = preferences.lastIncludeUncategorised
        private set

    interface Listener {
        fun onFilterChanged()
    }

    private val listeners = mutableListOf<Listener>()

    fun update(ids: Set<Long>, includeUncategorised: Boolean) {
        if (ids == selectedSubcategoryIds && includeUncategorised == this.includeUncategorised) return
        selectedSubcategoryIds = ids.toSet()
        this.includeUncategorised = includeUncategorised
        preferences.lastSelectedSubcategoryIds = selectedSubcategoryIds
        preferences.lastIncludeUncategorised = includeUncategorised
        listeners.toList().forEach { it.onFilterChanged() }
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    val isEmpty: Boolean
        get() = selectedSubcategoryIds.isEmpty() && !includeUncategorised
}
