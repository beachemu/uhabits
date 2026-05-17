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

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.platform.AbstractComposeView
import org.isoron.uhabits.activities.common.compose.UhabitsTheme
import org.isoron.uhabits.core.models.SavedView
import org.isoron.uhabits.core.models.sqlite.CategoryRepository
import org.isoron.uhabits.core.models.sqlite.SavedViewRepository
import org.isoron.uhabits.core.models.sqlite.SubcategoryRepository
import org.isoron.uhabits.inject.ActivityContext
import org.isoron.uhabits.inject.ActivityScope
import javax.inject.Inject

@ActivityScope
class NavigationDrawerView @Inject constructor(
    @ActivityContext context: Context,
    private val categoryRepository: CategoryRepository,
    private val subcategoryRepository: SubcategoryRepository,
    private val savedViewRepository: SavedViewRepository
) : AbstractComposeView(context) {

    private val selected: SnapshotStateMap<Long, Boolean> = mutableStateMapOf()
    private var reloadVersion by mutableStateOf(0)

    val selectedSubcategoryIds: Set<Long>
        get() = selected.filterValues { it }.keys

    var onSelectionChanged: ((Set<Long>) -> Unit)? = null
    var onSavedViewTapped: ((SavedView) -> Unit)? = null

    fun reload() {
        reloadVersion++
    }

    @Composable
    override fun Content() {
        UhabitsTheme {
            NavigationDrawerContent(
                categoryRepository = categoryRepository,
                subcategoryRepository = subcategoryRepository,
                savedViewRepository = savedViewRepository,
                reloadVersion = reloadVersion,
                selectedSubcategoryIds = selectedSubcategoryIds,
                onSubcategoryCheckedChange = { id, checked ->
                    if (checked) selected[id] = true else selected.remove(id)
                    onSelectionChanged?.invoke(selectedSubcategoryIds)
                },
                onSavedViewTapped = { view -> onSavedViewTapped?.invoke(view) }
            )
        }
    }
}
