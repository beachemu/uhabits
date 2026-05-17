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

package org.isoron.uhabits.activities.habits.list.views

import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.SavedViewSortDirection
import org.isoron.uhabits.core.models.SavedViewSortField

fun savedViewSortToOrder(
    field: SavedViewSortField,
    direction: SavedViewSortDirection
): HabitList.Order {
    val asc = direction == SavedViewSortDirection.ASC
    return when (field) {
        SavedViewSortField.NAME ->
            if (asc) HabitList.Order.BY_NAME_ASC else HabitList.Order.BY_NAME_DESC
        SavedViewSortField.SCORE ->
            if (asc) HabitList.Order.BY_SCORE_ASC else HabitList.Order.BY_SCORE_DESC
        SavedViewSortField.COLOR ->
            if (asc) HabitList.Order.BY_COLOR_ASC else HabitList.Order.BY_COLOR_DESC
        SavedViewSortField.STREAK ->
            if (asc) HabitList.Order.BY_STATUS_ASC else HabitList.Order.BY_STATUS_DESC
        SavedViewSortField.CATEGORY ->
            if (asc) HabitList.Order.BY_POSITION else HabitList.Order.BY_POSITION_DESC
    }
}

fun orderToSavedViewSort(order: HabitList.Order): Pair<SavedViewSortField, SavedViewSortDirection> =
    when (order) {
        HabitList.Order.BY_NAME_ASC -> SavedViewSortField.NAME to SavedViewSortDirection.ASC
        HabitList.Order.BY_NAME_DESC -> SavedViewSortField.NAME to SavedViewSortDirection.DESC
        HabitList.Order.BY_SCORE_ASC -> SavedViewSortField.SCORE to SavedViewSortDirection.ASC
        HabitList.Order.BY_SCORE_DESC -> SavedViewSortField.SCORE to SavedViewSortDirection.DESC
        HabitList.Order.BY_COLOR_ASC -> SavedViewSortField.COLOR to SavedViewSortDirection.ASC
        HabitList.Order.BY_COLOR_DESC -> SavedViewSortField.COLOR to SavedViewSortDirection.DESC
        HabitList.Order.BY_STATUS_ASC -> SavedViewSortField.STREAK to SavedViewSortDirection.ASC
        HabitList.Order.BY_STATUS_DESC -> SavedViewSortField.STREAK to SavedViewSortDirection.DESC
        HabitList.Order.BY_POSITION -> SavedViewSortField.CATEGORY to SavedViewSortDirection.ASC
        HabitList.Order.BY_POSITION_DESC -> SavedViewSortField.CATEGORY to SavedViewSortDirection.DESC
    }
