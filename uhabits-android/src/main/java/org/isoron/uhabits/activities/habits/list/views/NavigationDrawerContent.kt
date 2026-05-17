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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.common.compose.LocalUhabitsColors
import org.isoron.uhabits.core.models.Category
import org.isoron.uhabits.core.models.SavedView
import org.isoron.uhabits.core.models.Subcategory
import org.isoron.uhabits.core.models.sqlite.CategoryRepository
import org.isoron.uhabits.core.models.sqlite.SavedViewRepository
import org.isoron.uhabits.core.models.sqlite.SubcategoryRepository
import org.isoron.uhabits.utils.toFixedAndroidColor

const val UNCATEGORISED_ID: Long = -1L

@Composable
fun NavigationDrawerContent(
    categoryRepository: CategoryRepository,
    subcategoryRepository: SubcategoryRepository,
    savedViewRepository: SavedViewRepository,
    reloadVersion: Int,
    selectedSubcategoryIds: Set<Long>,
    onSubcategoryCheckedChange: (Long, Boolean) -> Unit,
    onCategoryToggle: (List<Long>, Boolean) -> Unit,
    onSavedViewTapped: (SavedView) -> Unit
) {
    val colors = LocalUhabitsColors.current
    val categories = remember(reloadVersion) { categoryRepository.findAll() }
    val subsByCategory = remember(reloadVersion) {
        subcategoryRepository.findAll().groupBy { it.categoryId }
    }
    val savedViews = remember(reloadVersion) { savedViewRepository.findAll() }
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.cardBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader(text = stringResource(R.string.nav_drawer_categories))
        for (category in categories) {
            val id = category.id ?: continue
            val isExpanded = expanded[id] == true
            val subIds = subsByCategory[id].orEmpty().mapNotNull { it.id }
            val checkedCount = subIds.count { it in selectedSubcategoryIds }
            val triState = when {
                subIds.isEmpty() -> null
                checkedCount == 0 -> ToggleableState.Off
                checkedCount == subIds.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
            CategoryRow(
                category = category,
                expanded = isExpanded,
                triState = triState,
                onTriStateClick = {
                    onCategoryToggle(subIds, triState != ToggleableState.On)
                },
                onToggle = { expanded[id] = !isExpanded }
            )
            if (isExpanded) {
                for (sub in subsByCategory[id].orEmpty()) {
                    val subId = sub.id ?: continue
                    SubcategoryRow(
                        subcategory = sub,
                        checked = subId in selectedSubcategoryIds,
                        onCheckedChange = { onSubcategoryCheckedChange(subId, it) }
                    )
                }
            }
        }
        UncategorisedRow(
            checked = UNCATEGORISED_ID in selectedSubcategoryIds,
            onCheckedChange = { onSubcategoryCheckedChange(UNCATEGORISED_ID, it) }
        )

        SectionHeader(text = stringResource(R.string.nav_drawer_saved_views))
        for (view in savedViews) {
            SavedViewRow(view = view, onTap = { onSavedViewTapped(view) })
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val colors = LocalUhabitsColors.current
    Text(
        text = text.uppercase(),
        color = colors.contrast60,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun CategoryRow(
    category: Category,
    expanded: Boolean,
    triState: ToggleableState?,
    onTriStateClick: () -> Unit,
    onToggle: () -> Unit
) {
    val colors = LocalUhabitsColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Text(
            text = if (expanded) "▾" else "▸",
            color = colors.contrast60,
            modifier = Modifier.padding(end = 8.dp)
        )
        if (triState != null) {
            val tint = Color(category.color.toFixedAndroidColor())
            TriStateCheckbox(
                state = triState,
                onClick = onTriStateClick,
                colors = CheckboxDefaults.colors(
                    checkedColor = tint,
                    uncheckedColor = tint
                )
            )
        }
        Text(
            text = category.name,
            color = Color(category.color.toFixedAndroidColor()),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = if (triState != null) 0.dp else 12.dp)
                .weight(1f)
        )
    }
}

@Composable
private fun SubcategoryRow(
    subcategory: Subcategory,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, top = 2.dp, end = 16.dp, bottom = 2.dp)
    ) {
        val tint = Color(subcategory.color.toFixedAndroidColor())
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = tint,
                uncheckedColor = tint
            )
        )
        Text(
            text = subcategory.name,
            color = tint,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun UncategorisedRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalUhabitsColors.current
    val label = stringResource(R.string.nav_drawer_uncategorised)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = label,
            color = colors.contrast100,
            fontSize = 14.sp,
            fontStyle = FontStyle.Italic,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        )
    }
}

@Composable
private fun SavedViewRow(view: SavedView, onTap: () -> Unit) {
    val colors = LocalUhabitsColors.current
    Text(
        text = view.name,
        color = colors.contrast100,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}
