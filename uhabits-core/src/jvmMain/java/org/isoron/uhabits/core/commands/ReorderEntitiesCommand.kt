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
package org.isoron.uhabits.core.commands

import org.isoron.uhabits.core.models.ReorderableEntity
import org.isoron.uhabits.core.models.sqlite.CategoryRepository
import org.isoron.uhabits.core.models.sqlite.SubcategoryRepository

data class ReorderCategoriesCommand(
    val repository: CategoryRepository,
    val orderedIds: List<Long>
) : Command {
    override fun run() {
        reorder(repository.findAll(), orderedIds, repository::save)
    }
}

data class ReorderSubcategoriesCommand(
    val repository: SubcategoryRepository,
    val categoryId: Long,
    val orderedIds: List<Long>
) : Command {
    override fun run() {
        reorder(repository.findByCategory(categoryId), orderedIds, repository::save)
    }
}

private fun <T : ReorderableEntity> reorder(
    entities: List<T>,
    orderedIds: List<Long>,
    save: (T) -> Unit
) {
    val byId = entities.associateBy { it.id }
    orderedIds.forEachIndexed { index, id ->
        val entity = byId[id] ?: return@forEachIndexed
        if (entity.position != index) {
            entity.position = index
            save(entity)
        }
    }
}
