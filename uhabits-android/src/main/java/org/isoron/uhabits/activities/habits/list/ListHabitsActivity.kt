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

package org.isoron.uhabits.activities.habits.list

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.widget.addTextChangedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.isoron.uhabits.BaseExceptionHandler
import org.isoron.uhabits.HabitsApplication
import org.isoron.uhabits.R
import org.isoron.uhabits.activities.habits.list.views.HabitCardListAdapter
import org.isoron.uhabits.activities.habits.list.views.UNCATEGORISED_ID
import org.isoron.uhabits.activities.habits.list.views.orderToSavedViewSort
import org.isoron.uhabits.activities.habits.list.views.savedViewSortToOrder
import org.isoron.uhabits.core.models.Category
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.PaletteColor
import org.isoron.uhabits.core.models.SavedView
import org.isoron.uhabits.core.models.Subcategory
import org.isoron.uhabits.core.models.Timestamp
import org.isoron.uhabits.core.preferences.Preferences
import org.isoron.uhabits.core.tasks.TaskRunner
import org.isoron.uhabits.core.ui.ThemeSwitcher.Companion.THEME_DARK
import org.isoron.uhabits.core.utils.MidnightTimer
import org.isoron.uhabits.database.AutoBackup
import org.isoron.uhabits.inject.ActivityContextModule
import org.isoron.uhabits.inject.DaggerHabitsActivityComponent
import org.isoron.uhabits.inject.HabitsActivityComponent
import org.isoron.uhabits.inject.HabitsApplicationComponent
import org.isoron.uhabits.utils.applyRootViewInsets
import org.isoron.uhabits.utils.dismissCurrentDialog
import org.isoron.uhabits.utils.restartWithFade

class ListHabitsActivity : AppCompatActivity(), Preferences.Listener {

    var pureBlack: Boolean = false
    lateinit var appComponent: HabitsApplicationComponent
    lateinit var component: HabitsActivityComponent
    lateinit var taskRunner: TaskRunner
    lateinit var adapter: HabitCardListAdapter
    lateinit var rootView: ListHabitsRootView
    lateinit var screen: ListHabitsScreen
    lateinit var prefs: Preferences
    lateinit var midnightTimer: MidnightTimer
    private val scope = CoroutineScope(Dispatchers.Main)

    private var permissionAlreadyRequested = false
    private val permissionLauncher =
        registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                scheduleReminders()
            } else {
                Log.i("ListHabitsActivity", "POST_NOTIFICATIONS denied")
            }
        }

    private lateinit var menu: ListHabitsMenu
    private lateinit var drawerToggle: ActionBarDrawerToggle

    override fun onQuestionMarksChanged() {
        invalidateOptionsMenu()
        menu.behavior.onPreferencesChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appComponent = (applicationContext as HabitsApplication).component
        component = DaggerHabitsActivityComponent
            .builder()
            .activityContextModule(ActivityContextModule(this))
            .habitsApplicationComponent(appComponent)
            .build()
        component.themeSwitcher.apply()

        prefs = appComponent.preferences
        prefs.addListener(this)
        pureBlack = prefs.isPureBlackEnabled
        midnightTimer = appComponent.midnightTimer
        rootView = component.listHabitsRootView
        screen = component.listHabitsScreen
        adapter = component.habitCardListAdapter
        taskRunner = appComponent.taskRunner
        menu = component.listHabitsMenu
        Thread.setDefaultUncaughtExceptionHandler(BaseExceptionHandler(this))
        component.listHabitsBehavior.onStartup()
        rootView.applyRootViewInsets()
        setContentView(rootView)
        setSupportActionBar(rootView.tbar)
        drawerToggle = ActionBarDrawerToggle(
            this,
            rootView.drawerLayout,
            rootView.tbar,
            R.string.nav_drawer_open,
            R.string.nav_drawer_close
        )
        rootView.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        val filterState = appComponent.habitListFilterState
        rootView.drawerView.applySelection(
            filterState.selectedSubcategoryIds,
            filterState.selectedCategoryIds,
            filterState.includeUncategorised
        )
        rootView.drawerView.onSelectionChanged = { subSelection, catSelection ->
            val includeUncategorised = UNCATEGORISED_ID in subSelection
            val realSubs = subSelection - UNCATEGORISED_ID
            filterState.update(realSubs, catSelection, includeUncategorised)
        }
        rootView.drawerView.onSavedViewTapped = { view -> applySavedView(view) }
        rootView.drawerView.onSaveCurrentView = { showSaveCurrentViewDialog() }
        rootView.drawerView.onOverwriteSavedView = { view -> overwriteSavedView(view) }
        rootView.drawerView.onDeleteSavedView = { view -> showDeleteSavedViewDialog(view) }
        rootView.drawerView.onManageCategoriesTapped = { openManageCategories() }

        menu.onSeedDemoData = { seedDemoData() }
    }

    private fun applySavedView(view: SavedView) {
        rootView.drawerView.applySelection(
            view.selectedSubcategoryIds,
            view.selectedCategoryIds,
            view.includeUncategorised
        )
        appComponent.habitListFilterState.update(
            view.selectedSubcategoryIds,
            view.selectedCategoryIds,
            view.includeUncategorised
        )
        adapter.primaryOrder = savedViewSortToOrder(view.sortField, view.sortDirection)
        rootView.drawerLayout.closeDrawer(Gravity.START)
    }

    private fun showSaveCurrentViewDialog() {
        val input = EditText(this).apply {
            setHint(R.string.save_current_view_hint)
            setSingleLine(true)
        }
        val container = android.widget.FrameLayout(this).apply {
            val pad = (resources.displayMetrics.density * 16).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.save_current_view_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                persistCurrentView(input.text.toString().trim())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.isEnabled = false
            input.addTextChangedListener { editable ->
                positive.isEnabled = editable?.toString()?.trim()?.isNotEmpty() == true
            }
        }
        dialog.show()
    }

    private fun overwriteSavedView(view: SavedView) {
        val rawSelection = rootView.drawerView.selectedSubcategoryIds
        val includeUncategorised = UNCATEGORISED_ID in rawSelection
        val subIds = rawSelection - UNCATEGORISED_ID
        val catIds = rootView.drawerView.selectedCategoryIds
        val (field, direction) = orderToSavedViewSort(adapter.primaryOrder)
        val updated = view.copy(
            selectedSubcategoryIds = subIds,
            selectedCategoryIds = catIds,
            includeUncategorised = includeUncategorised,
            sortField = field,
            sortDirection = direction
        )
        val repo = appComponent.savedViewRepository
        scope.launch {
            withContext(Dispatchers.IO) { repo.save(updated) }
            rootView.drawerView.reload()
            Log.i("ListHabitsActivity", "Overwrote saved view '${updated.name}'")
        }
    }

    private fun showDeleteSavedViewDialog(view: SavedView) {
        AlertDialog.Builder(this)
            .setTitle(R.string.saved_view_delete_title)
            .setMessage(getString(R.string.saved_view_delete_message, view.name))
            .setPositiveButton(R.string.saved_view_delete) { _, _ -> deleteSavedView(view) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteSavedView(view: SavedView) {
        val repo = appComponent.savedViewRepository
        scope.launch {
            withContext(Dispatchers.IO) { repo.remove(view) }
            rootView.drawerView.reload()
            Log.i("ListHabitsActivity", "Deleted saved view '${view.name}'")
        }
    }

    private fun persistCurrentView(name: String) {
        if (name.isEmpty()) return
        val rawSelection = rootView.drawerView.selectedSubcategoryIds
        val includeUncategorised = UNCATEGORISED_ID in rawSelection
        val subIds = rawSelection - UNCATEGORISED_ID
        val catIds = rootView.drawerView.selectedCategoryIds
        val (field, direction) = orderToSavedViewSort(adapter.primaryOrder)
        val repo = appComponent.savedViewRepository
        scope.launch {
            val position = withContext(Dispatchers.IO) {
                val existing = repo.findAll()
                val nextPosition = (existing.maxOfOrNull { it.position } ?: -1) + 1
                val view = SavedView(
                    name = name,
                    selectedSubcategoryIds = subIds,
                    selectedCategoryIds = catIds,
                    includeUncategorised = includeUncategorised,
                    sortField = field,
                    sortDirection = direction,
                    position = nextPosition
                )
                repo.save(view)
                nextPosition
            }
            rootView.drawerView.reload()
            Log.i("ListHabitsActivity", "Saved view '$name' at position $position")
        }
    }

    private fun openManageCategories() {
        startActivity(
            Intent(this, org.isoron.uhabits.activities.categories.ManageCategoriesActivity::class.java)
        )
    }

    private fun seedDemoData() {
        val categoryRepo = appComponent.categoryRepository
        val subcategoryRepo = appComponent.subcategoryRepository
        val habitList = appComponent.habitList
        val modelFactory = appComponent.modelFactory

        if (categoryRepo.findAll().isNotEmpty()) {
            Log.i("ListHabitsActivity", "Demo data already seeded; skipping")
            return
        }

        val health = Category(name = "Health", color = PaletteColor(2), position = 0)
        val work = Category(name = "Work", color = PaletteColor(8), position = 1)
        categoryRepo.save(health)
        categoryRepo.save(work)

        val fitness = Subcategory(categoryId = health.id!!, name = "Fitness", color = PaletteColor(2), position = 0)
        val nutrition = Subcategory(categoryId = health.id!!, name = "Nutrition", color = PaletteColor(4), position = 1)
        val deepWork = Subcategory(categoryId = work.id!!, name = "Deep work", color = PaletteColor(8), position = 0)
        subcategoryRepo.save(fitness)
        subcategoryRepo.save(nutrition)
        subcategoryRepo.save(deepWork)

        val seedHabits = listOf(
            Triple("Run", PaletteColor(2), fitness.id),
            Triple("Stretch", PaletteColor(2), fitness.id),
            Triple("Eat vegetables", PaletteColor(4), nutrition.id),
            Triple("Drink water", PaletteColor(4), nutrition.id),
            Triple("Write deeply for 1h", PaletteColor(8), deepWork.id),
            Triple("Inbox zero", PaletteColor(8), deepWork.id),
            Triple("Read a book", PaletteColor(11), null),
            Triple("Call a friend", PaletteColor(11), null)
        )
        for ((index, spec) in seedHabits.withIndex()) {
            val (name, color, subId) = spec
            val habit = modelFactory.buildHabit()
            habit.name = name
            habit.color = color
            habit.frequency = Frequency.DAILY
            habit.position = index
            habit.subcategoryId = subId
            habit.categoryId = subId?.let { subcategoryRepo.find(it)?.categoryId }
            habitList.add(habit)
            habit.recompute()
        }

        rootView.drawerView.reload()
        adapter.refresh()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onBackPressed() {
        if (rootView.drawerLayout.isDrawerOpen(Gravity.START)) {
            rootView.drawerLayout.closeDrawer(Gravity.START)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onPause() {
        midnightTimer.onPause()
        screen.onDetached()
        adapter.cancelRefresh()
        dismissCurrentDialog()
        super.onPause()
    }

    override fun onResume() {
        adapter.refresh()
        screen.onAttached()
        rootView.postInvalidate()
        rootView.drawerView.reload()
        midnightTimer.onResume()

        if (appComponent.reminderScheduler.hasHabitsWithReminders()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                scheduleReminders()
            } else {
                if (checkSelfPermission(this, POST_NOTIFICATIONS) == PERMISSION_GRANTED) {
                    scheduleReminders()
                } else {
                    // If we have not requested the permission yet, request it. Otherwide do
                    // nothing. This check is necessary to avoid an infinite onResume loop in case
                    // the user denies the permission.
                    if (!permissionAlreadyRequested) {
                        Log.i("ListHabitsActivity", "Requestion permission: POST_NOTIFICATIONS")
                        permissionLauncher.launch(POST_NOTIFICATIONS)
                        permissionAlreadyRequested = true
                    }
                }
            }
        }

        taskRunner.run {
            try {
                AutoBackup(this@ListHabitsActivity).run()
                appComponent.widgetUpdater.updateWidgets()
            } catch (e: Exception) {
                Log.e("ListHabitActivity", "TaskRunner failed", e)
            }
        }
        if (prefs.theme == THEME_DARK && prefs.isPureBlackEnabled != pureBlack) {
            restartWithFade(ListHabitsActivity::class.java)
        }
        parseIntents()
        super.onResume()
    }

    private fun scheduleReminders() {
        appComponent.reminderScheduler.scheduleAll()
    }

    override fun onCreateOptionsMenu(m: Menu): Boolean {
        menu.onCreate(menuInflater, m)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        invalidateOptionsMenu()
        return menu.onItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        screen.onResult(request, result, data)
    }

    private fun parseIntents() {
        if (intent == null) return
        if (intent.action == ACTION_EDIT) {
            val habitId = intent.extras?.getLong("habit")
            val timestamp = intent.extras?.getLong("timestamp")
            if (habitId != null && timestamp != null) {
                val habit = appComponent.habitList.getById(habitId)!!
                component.listHabitsBehavior.onEdit(habit, Timestamp(timestamp), 0f, 0f)
            }
        }
        intent = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        menu.behavior.destroy()
        super.onDestroy()
    }

    companion object {
        const val ACTION_EDIT = "org.isoron.uhabits.ACTION_EDIT"
    }
}
