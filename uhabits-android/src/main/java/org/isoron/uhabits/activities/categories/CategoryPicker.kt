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

package org.isoron.uhabits.activities.categories

import android.content.Context
import androidx.appcompat.app.AlertDialog
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.sqlite.CategoryRepository
import org.isoron.uhabits.core.models.sqlite.SubcategoryRepository
import org.isoron.uhabits.utils.dismissCurrentAndShow

object CategoryPicker {
    fun pickCategory(
        context: Context,
        categoryRepository: CategoryRepository,
        onPicked: (Long?) -> Unit
    ) {
        val categories = categoryRepository.findAll()
        val labels = mutableListOf(context.getString(R.string.uncategorised))
        labels.addAll(categories.map { it.name })
        AlertDialog.Builder(context)
            .setTitle(R.string.category)
            .setItems(labels.toTypedArray()) { dialog, which ->
                onPicked(if (which == 0) null else categories[which - 1].id)
                dialog.dismiss()
            }
            .create()
            .dismissCurrentAndShow()
    }

    fun pickSubcategory(
        context: Context,
        subcategoryRepository: SubcategoryRepository,
        categoryId: Long,
        onPicked: (Long?) -> Unit
    ) {
        val subcategories = subcategoryRepository.findByCategory(categoryId)
        val labels = mutableListOf(context.getString(R.string.none))
        labels.addAll(subcategories.map { it.name })
        AlertDialog.Builder(context)
            .setTitle(R.string.subcategory)
            .setItems(labels.toTypedArray()) { dialog, which ->
                onPicked(if (which == 0) null else subcategories[which - 1].id)
                dialog.dismiss()
            }
            .create()
            .dismissCurrentAndShow()
    }
}
