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
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.isoron.uhabits.R
import org.isoron.uhabits.core.models.Category
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.models.SavedView
import org.isoron.uhabits.core.models.Subcategory
import org.isoron.uhabits.core.models.sqlite.CategoryRepository
import org.isoron.uhabits.core.models.sqlite.SavedViewRepository
import org.isoron.uhabits.core.models.sqlite.SubcategoryRepository
import org.isoron.uhabits.inject.ActivityContext
import org.isoron.uhabits.inject.ActivityScope
import org.isoron.uhabits.utils.dp
import org.isoron.uhabits.utils.sres
import org.isoron.uhabits.utils.toFixedAndroidColor
import javax.inject.Inject

const val UNCATEGORISED_ID: Long = -1L

@ActivityScope
class NavigationDrawerView @Inject constructor(
    @ActivityContext context: Context,
    private val categoryRepository: CategoryRepository,
    private val subcategoryRepository: SubcategoryRepository,
    private val savedViewRepository: SavedViewRepository
) : ScrollView(context) {

    private val container: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    val selectedSubcategoryIds: MutableSet<Long> = mutableSetOf()
    private val expandedCategoryIds: MutableSet<Long> = mutableSetOf()

    var onSelectionChanged: ((Set<Long>) -> Unit)? = null
    var onSavedViewTapped: ((SavedView) -> Unit)? = null

    init {
        setBackgroundColor(sres.getColor(R.attr.cardBgColor))
        isFillViewport = true
        addView(container, MATCH_PARENT, MATCH_PARENT)
        reload()
    }

    fun reload() {
        container.removeAllViews()
        val categories = categoryRepository.findAll()
        val subsByCategory = subcategoryRepository.findAll().groupBy { it.categoryId }
        val savedViews = savedViewRepository.findAll()

        container.addView(buildSectionHeader(context.getString(R.string.nav_drawer_categories)))
        for (category in categories) {
            val id = category.id ?: continue
            container.addView(buildCategoryRow(category))
            if (expandedCategoryIds.contains(id)) {
                for (sub in subsByCategory[id].orEmpty()) {
                    container.addView(buildSubcategoryRow(sub))
                }
            }
        }
        container.addView(buildUncategorisedRow())

        container.addView(buildSectionHeader(context.getString(R.string.nav_drawer_saved_views)))
        for (view in savedViews) {
            container.addView(buildSavedViewRow(view))
        }
    }

    private fun buildSectionHeader(label: String): TextView {
        return TextView(context).apply {
            text = label.uppercase()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(sres.getColor(R.attr.contrast60))
            setPadding(dp(16f).toInt(), dp(16f).toInt(), dp(16f).toInt(), dp(8f).toInt())
        }
    }

    private fun buildCategoryRow(category: Category): View {
        val id = category.id ?: return View(context)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f).toInt(), dp(10f).toInt(), dp(16f).toInt(), dp(10f).toInt())
            isClickable = true
            isFocusable = true
            setBackgroundResource(selectableItemBackground())
            setOnClickListener {
                if (expandedCategoryIds.contains(id)) {
                    expandedCategoryIds.remove(id)
                } else {
                    expandedCategoryIds.add(id)
                }
                reload()
            }
        }
        val chevron = TextView(context).apply {
            text = if (expandedCategoryIds.contains(id)) "▾" else "▸"
            setTextColor(sres.getColor(R.attr.contrast60))
            setPadding(0, 0, dp(8f).toInt(), 0)
        }
        row.addView(chevron, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        row.addView(buildColorDot(category.color), dotLp())
        row.addView(
            buildLabel(category.name, bold = true),
            labelLp()
        )
        return row
    }

    private fun buildSubcategoryRow(subcategory: Subcategory): View {
        val id = subcategory.id ?: return View(context)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(40f).toInt(), dp(6f).toInt(), dp(16f).toInt(), dp(6f).toInt())
        }
        val checkbox = CheckBox(context).apply {
            isChecked = selectedSubcategoryIds.contains(id)
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    selectedSubcategoryIds.add(id)
                } else {
                    selectedSubcategoryIds.remove(id)
                }
                onSelectionChanged?.invoke(selectedSubcategoryIds.toSet())
            }
        }
        row.addView(checkbox, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        row.addView(buildColorDot(subcategory.color), dotLp())
        row.addView(buildLabel(subcategory.name), labelLp())
        return row
    }

    private fun buildUncategorisedRow(): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f).toInt(), dp(6f).toInt(), dp(16f).toInt(), dp(6f).toInt())
        }
        val checkbox = CheckBox(context).apply {
            isChecked = selectedSubcategoryIds.contains(UNCATEGORISED_ID)
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    selectedSubcategoryIds.add(UNCATEGORISED_ID)
                } else {
                    selectedSubcategoryIds.remove(UNCATEGORISED_ID)
                }
                onSelectionChanged?.invoke(selectedSubcategoryIds.toSet())
            }
        }
        row.addView(checkbox, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        row.addView(
            buildLabel(context.getString(R.string.nav_drawer_uncategorised), italic = true),
            labelLp()
        )
        return row
    }

    private fun buildSavedViewRow(view: SavedView): View {
        return TextView(context).apply {
            text = view.name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(sres.getColor(R.attr.contrast100))
            setPadding(dp(16f).toInt(), dp(12f).toInt(), dp(16f).toInt(), dp(12f).toInt())
            isClickable = true
            isFocusable = true
            setBackgroundResource(selectableItemBackground())
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
            setOnClickListener { onSavedViewTapped?.invoke(view) }
        }
    }

    private fun buildLabel(text: String, bold: Boolean = false, italic: Boolean = false): TextView {
        return TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(sres.getColor(R.attr.contrast100))
            val style = when {
                bold && italic -> android.graphics.Typeface.BOLD_ITALIC
                bold -> android.graphics.Typeface.BOLD
                italic -> android.graphics.Typeface.ITALIC
                else -> android.graphics.Typeface.NORMAL
            }
            setTypeface(typeface, style)
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
        }
    }

    private fun buildColorDot(color: PaletteColor): View {
        val size = dp(12f).toInt()
        return View(context).apply {
            background = ShapeDrawable(OvalShape()).apply {
                paint.color = color.toFixedAndroidColor()
                paint.style = Paint.Style.FILL
            }
            minimumWidth = size
            minimumHeight = size
        }
    }

    private fun dotLp() = LinearLayout.LayoutParams(dp(12f).toInt(), dp(12f).toInt()).apply {
        marginEnd = dp(12f).toInt()
    }

    private fun labelLp() = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)

    private fun selectableItemBackground(): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(
            android.R.attr.selectableItemBackground,
            value,
            true
        )
        return value.resourceId
    }
}
