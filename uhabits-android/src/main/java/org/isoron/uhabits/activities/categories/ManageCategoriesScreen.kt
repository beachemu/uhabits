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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.common.compose.LocalUhabitsColors
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.commands.CreateCategoryCommand
import org.isoron.uhabits.core.commands.CreateSubcategoryCommand
import org.isoron.uhabits.core.commands.DeleteCategoryCommand
import org.isoron.uhabits.core.commands.DeleteSubcategoryCommand
import org.isoron.uhabits.core.commands.EditCategoryCommand
import org.isoron.uhabits.core.commands.EditSubcategoryCommand
import org.isoron.uhabits.core.commands.ReorderCategoriesCommand
import org.isoron.uhabits.core.commands.ReorderSubcategoriesCommand
import org.isoron.uhabits.core.models.Category
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.models.Subcategory
import org.isoron.uhabits.core.models.sqlite.CategoryRepository
import org.isoron.uhabits.core.models.sqlite.SubcategoryRepository
import org.isoron.uhabits.utils.toFixedAndroidColor

private const val PALETTE_SIZE = 20

private sealed class EditorTarget {
    object NewCategory : EditorTarget()
    data class EditCategory(val category: Category) : EditorTarget()
    data class NewSubcategory(val categoryId: Long) : EditorTarget()
    data class EditSubcategory(val subcategory: Subcategory) : EditorTarget()
}

private sealed class DeleteTarget {
    data class Cat(val category: Category) : DeleteTarget()
    data class Sub(val subcategory: Subcategory) : DeleteTarget()
}

@Composable
fun ManageCategoriesScreen(
    categoryRepository: CategoryRepository,
    subcategoryRepository: SubcategoryRepository,
    commandRunner: CommandRunner,
    onClose: () -> Unit
) {
    val colors = LocalUhabitsColors.current
    var reloadVersion by remember { mutableStateOf(0) }
    val categories = remember(reloadVersion) { categoryRepository.findAll() }
    val subsByCategory = remember(reloadVersion) {
        subcategoryRepository.findAll().groupBy { it.categoryId }
    }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }

    fun refresh() {
        reloadVersion++
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.cardBackground)
            .statusBarsPadding()
    ) {
        TopBar(
            title = stringResource(R.string.manage_categories),
            onClose = onClose
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(categories, key = { it.id ?: 0L }) { category ->
                CategoryCard(
                    category = category,
                    subcategories = subsByCategory[category.id].orEmpty(),
                    canMoveUp = categories.indexOf(category) > 0,
                    canMoveDown = categories.indexOf(category) < categories.size - 1,
                    onEdit = { editorTarget = EditorTarget.EditCategory(category) },
                    onDelete = { deleteTarget = DeleteTarget.Cat(category) },
                    onMoveUp = {
                        val ids = categories.mapNotNull { it.id }.toMutableList()
                        val i = ids.indexOf(category.id)
                        if (i > 0) {
                            val tmp = ids[i - 1]
                            ids[i - 1] = ids[i]
                            ids[i] = tmp
                            commandRunner.run(ReorderCategoriesCommand(categoryRepository, ids))
                            refresh()
                        }
                    },
                    onMoveDown = {
                        val ids = categories.mapNotNull { it.id }.toMutableList()
                        val i = ids.indexOf(category.id)
                        if (i in 0 until ids.size - 1) {
                            val tmp = ids[i + 1]
                            ids[i + 1] = ids[i]
                            ids[i] = tmp
                            commandRunner.run(ReorderCategoriesCommand(categoryRepository, ids))
                            refresh()
                        }
                    },
                    onAddSubcategory = {
                        editorTarget = EditorTarget.NewSubcategory(category.id ?: return@CategoryCard)
                    },
                    onEditSubcategory = { sub ->
                        editorTarget = EditorTarget.EditSubcategory(sub)
                    },
                    onDeleteSubcategory = { sub ->
                        deleteTarget = DeleteTarget.Sub(sub)
                    },
                    onMoveSubcategoryUp = { sub ->
                        val subs = subsByCategory[category.id].orEmpty()
                        val ids = subs.mapNotNull { it.id }.toMutableList()
                        val i = ids.indexOf(sub.id)
                        if (i > 0) {
                            val tmp = ids[i - 1]
                            ids[i - 1] = ids[i]
                            ids[i] = tmp
                            commandRunner.run(
                                ReorderSubcategoriesCommand(
                                    subcategoryRepository,
                                    category.id ?: return@CategoryCard,
                                    ids
                                )
                            )
                            refresh()
                        }
                    },
                    onMoveSubcategoryDown = { sub ->
                        val subs = subsByCategory[category.id].orEmpty()
                        val ids = subs.mapNotNull { it.id }.toMutableList()
                        val i = ids.indexOf(sub.id)
                        if (i in 0 until ids.size - 1) {
                            val tmp = ids[i + 1]
                            ids[i + 1] = ids[i]
                            ids[i] = tmp
                            commandRunner.run(
                                ReorderSubcategoriesCommand(
                                    subcategoryRepository,
                                    category.id ?: return@CategoryCard,
                                    ids
                                )
                            )
                            refresh()
                        }
                    }
                )
            }
            item {
                AddRow(
                    label = stringResource(R.string.add_category),
                    onTap = { editorTarget = EditorTarget.NewCategory }
                )
            }
        }
    }

    val target = editorTarget
    if (target != null) {
        val initialName: String
        val initialColor: PaletteColor
        val title: String
        when (target) {
            EditorTarget.NewCategory -> {
                initialName = ""
                initialColor = PaletteColor(8)
                title = stringResource(R.string.add_category)
            }
            is EditorTarget.EditCategory -> {
                initialName = target.category.name
                initialColor = target.category.color
                title = stringResource(R.string.rename)
            }
            is EditorTarget.NewSubcategory -> {
                initialName = ""
                initialColor = PaletteColor(8)
                title = stringResource(R.string.add_subcategory)
            }
            is EditorTarget.EditSubcategory -> {
                initialName = target.subcategory.name
                initialColor = target.subcategory.color
                title = stringResource(R.string.rename)
            }
        }
        EditorDialog(
            title = title,
            initialName = initialName,
            initialColor = initialColor,
            onDismiss = { editorTarget = null },
            onConfirm = { name, color ->
                when (target) {
                    EditorTarget.NewCategory -> commandRunner.run(
                        CreateCategoryCommand(
                            categoryRepository,
                            Category(name = name, color = color)
                        )
                    )
                    is EditorTarget.EditCategory -> {
                        val id = target.category.id
                        if (id != null) {
                            commandRunner.run(
                                EditCategoryCommand(categoryRepository, id, name, color)
                            )
                        }
                    }
                    is EditorTarget.NewSubcategory -> commandRunner.run(
                        CreateSubcategoryCommand(
                            subcategoryRepository,
                            Subcategory(categoryId = target.categoryId, name = name, color = color)
                        )
                    )
                    is EditorTarget.EditSubcategory -> {
                        val id = target.subcategory.id
                        if (id != null) {
                            commandRunner.run(
                                EditSubcategoryCommand(subcategoryRepository, id, name, color)
                            )
                        }
                    }
                }
                editorTarget = null
                refresh()
            }
        )
    }

    val dt = deleteTarget
    if (dt != null) {
        val (title, message) = when (dt) {
            is DeleteTarget.Cat -> stringResource(R.string.delete_category_title) to
                stringResource(R.string.delete_category_message, dt.category.name)
            is DeleteTarget.Sub -> stringResource(R.string.delete_subcategory_title) to
                stringResource(R.string.delete_subcategory_message, dt.subcategory.name)
        }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    when (dt) {
                        is DeleteTarget.Cat -> commandRunner.run(
                            DeleteCategoryCommand(categoryRepository, dt.category)
                        )
                        is DeleteTarget.Sub -> commandRunner.run(
                            DeleteSubcategoryCommand(subcategoryRepository, dt.subcategory)
                        )
                    }
                    deleteTarget = null
                    refresh()
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TopBar(title: String, onClose: () -> Unit) {
    val colors = LocalUhabitsColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            text = "‹",
            color = colors.contrast100,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable(onClick = onClose)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Text(
            text = title,
            color = colors.contrast100,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    subcategories: List<Subcategory>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onAddSubcategory: () -> Unit,
    onEditSubcategory: (Subcategory) -> Unit,
    onDeleteSubcategory: (Subcategory) -> Unit,
    onMoveSubcategoryUp: (Subcategory) -> Unit,
    onMoveSubcategoryDown: (Subcategory) -> Unit
) {
    val colors = LocalUhabitsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.contrast60.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp)
    ) {
        EntryRow(
            name = category.name,
            color = Color(category.color.toFixedAndroidColor()),
            bold = true,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onEdit = onEdit,
            onDelete = onDelete
        )
        for ((index, sub) in subcategories.withIndex()) {
            Row(modifier = Modifier.padding(start = 24.dp)) {
                EntryRow(
                    name = sub.name,
                    color = Color(sub.color.toFixedAndroidColor()),
                    bold = false,
                    canMoveUp = index > 0,
                    canMoveDown = index < subcategories.size - 1,
                    onMoveUp = { onMoveSubcategoryUp(sub) },
                    onMoveDown = { onMoveSubcategoryDown(sub) },
                    onEdit = { onEditSubcategory(sub) },
                    onDelete = { onDeleteSubcategory(sub) }
                )
            }
        }
        Row(modifier = Modifier.padding(start = 24.dp)) {
            AddRow(
                label = stringResource(R.string.add_subcategory),
                onTap = onAddSubcategory
            )
        }
    }
}

@Composable
private fun EntryRow(
    name: String,
    color: Color,
    bold: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalUhabitsColors.current
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            color = color,
            fontSize = 15.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconText(
            symbol = "▲",
            enabled = canMoveUp,
            onClick = onMoveUp,
            contentDescription = stringResource(R.string.move_up)
        )
        IconText(
            symbol = "▼",
            enabled = canMoveDown,
            onClick = onMoveDown,
            contentDescription = stringResource(R.string.move_down)
        )
        Box {
            Text(
                text = "⋮",
                color = colors.contrast60,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { menuExpanded = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun IconText(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String
) {
    val colors = LocalUhabitsColors.current
    Text(
        text = symbol,
        color = if (enabled) colors.contrast100 else colors.contrast60.copy(alpha = 0.3f),
        fontSize = 14.sp,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

@Composable
private fun AddRow(label: String, onTap: () -> Unit) {
    val colors = LocalUhabitsColors.current
    Text(
        text = "+ $label",
        color = colors.contrast60,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

@Composable
private fun EditorDialog(
    title: String,
    initialName: String,
    initialColor: PaletteColor,
    onDismiss: () -> Unit,
    onConfirm: (String, PaletteColor) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                PaletteGrid(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = { onConfirm(name.trim(), color) }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun PaletteGrid(selected: PaletteColor, onSelect: (PaletteColor) -> Unit) {
    val colors = LocalUhabitsColors.current
    val rows = (0 until PALETTE_SIZE).chunked(5)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (index in row) {
                    val swatch = Color(PaletteColor(index).toFixedAndroidColor())
                    val isSelected = selected.paletteIndex == index
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .then(
                                if (isSelected) {
                                    Modifier.border(2.dp, colors.contrast100, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onSelect(PaletteColor(index)) }
                    )
                }
            }
        }
    }
}
