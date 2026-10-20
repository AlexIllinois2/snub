package io.github.AlexIllinois2.snub.ui.home

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.AlexIllinois2.snub.BuildConfig
import io.github.AlexIllinois2.snub.HailApp.Companion.app
import io.github.AlexIllinois2.snub.R
import io.github.AlexIllinois2.snub.app.AppInfo
import io.github.AlexIllinois2.snub.app.AppManager
import io.github.AlexIllinois2.snub.app.HailApi
import io.github.AlexIllinois2.snub.app.HailData
import io.github.AlexIllinois2.snub.app.Tag
import io.github.AlexIllinois2.snub.databinding.FragmentPagerBinding
import io.github.AlexIllinois2.snub.extensions.*
import io.github.AlexIllinois2.snub.ui.main.MainFragment
import io.github.AlexIllinois2.snub.ui.theme.AppTheme
import io.github.AlexIllinois2.snub.utils.*
import io.github.AlexIllinois2.snub.work.HWork
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class PagerFragment : MainFragment(), PagerAdapter.OnItemClickListener, PagerAdapter.OnItemLongClickListener,
    MenuProvider {
    private var query: String = String()
    private var _binding: FragmentPagerBinding? = null
    private val binding get() = _binding!!
    private lateinit var pagerAdapter: PagerAdapter
    private var multiselect: Boolean
        set(value) {
            (parentFragment as HomeFragment).multiselect = value
        }
        get() = (parentFragment as HomeFragment).multiselect
    private val selectedList get() = (parentFragment as HomeFragment).selectedList
    private val tabs: TabLayout? get() = (parentFragment as? HomeFragment)?.bindingOrNull?.tabs
    private val adapter: HomeAdapter? get() = (parentFragment as? HomeFragment)?.bindingOrNull?.pager?.adapter as? HomeAdapter
    private val tag: Tag? get() = tabs?.let { HailData.displayTags.getOrNull(it.selectedTabPosition) }
    private val frozenTab: Boolean get() = tag?.id == HailData.FROZEN_TAB_ID

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val menuHost = requireActivity() as MenuHost
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        _binding = FragmentPagerBinding.inflate(inflater, container, false)
        pagerAdapter = PagerAdapter(selectedList).apply {
            onItemClickListener = this@PagerFragment
            onItemLongClickListener = this@PagerFragment
        }
        binding.recyclerView.run {
            layoutManager = GridLayoutManager(
                activity, resources.getInteger(
                    if (HailData.compactIcon) R.integer.home_span_compact else R.integer.home_span
                )
            )
            adapter = pagerAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    when (newState) {
                        RecyclerView.SCROLL_STATE_IDLE -> activity.fab.run {
                            // The FAB outlives this view: the post may fire after
                            // onDestroyView, so check the binding before showing.
                            postDelayed({ if (_binding != null && !frozenTab) show() }, 1000)
                        }

                        RecyclerView.SCROLL_STATE_DRAGGING -> activity.fab.hide()
                    }
                }
            })
            applyDefaultInsetter { paddingRelative(isRtl, bottom = isLandscape) }

        }

        binding.refresh.apply {
            setOnRefreshListener {
                updateCurrentList()
                binding.refresh.isRefreshing = false
            }
            applyDefaultInsetter { marginRelative(isRtl, start = !isLandscape, end = true) }
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        updateCurrentList()
        updateBarTitle()
        activity.appbar.setLiftOnScrollTargetView(binding.recyclerView)
        tabs?.let { tabLayout ->
            tabLayout.getTabAt(tabLayout.selectedTabPosition)?.view?.setOnLongClickListener {
                if (isResumed && !frozenTab) showTagDialog()
                true
            }
        }
        // The frozen tab has no floating button: unfreezing is per-app there,
        // and a one-tap "freeze everything listed" would be dangerous.
        if (frozenTab) {
            activity.fab.setOnClickListener(null)
            activity.fab.setOnLongClickListener(null)
            activity.fab.hide()
        } else {
            activity.fab.setOnClickListener {
                setListFrozen(true, pagerAdapter.currentList.filterNot { it.whitelisted })
            }
            activity.fab.setOnLongClickListener {
                setListFrozen(true)
                true
            }
            activity.fab.show()
        }
    }

    private fun updateCurrentList() = (if (frozenTab) frozenList() else HailData.checkedList.filter {
        if (query.isEmpty()) it.tagId == tag?.id
        else ((HailData.nineKeySearch && NineKeySearch.search(
            query, it.packageName, it.name.toString()
        )) || FuzzySearch.search(it.packageName, query) || FuzzySearch.search(
            it.name.toString(), query
        ) || PinyinSearch.searchPinyinAll(it.name.toString(), query))
    }).sortedWith(NameComparator).let {
        binding.empty.isVisible = it.isEmpty()
        pagerAdapter.submitList(it)
        app.setAutoFreezeService()
    }

    /**
     * Every app currently frozen under the working mode, managed or not, so that
     * apps can always be seen and unfrozen here no matter which tool froze them.
     */
    private fun frozenList(): List<AppInfo> {
        val managed = HailData.checkedList.associateBy { it.packageName }
        return HPackages.getInstalledApplications().asSequence().filter {
            it.flags and ApplicationInfo.FLAG_INSTALLED != 0
                    && it.packageName != BuildConfig.APPLICATION_ID && AppManager.isAppFrozen(it)
        }.map { managed[it.packageName] ?: AppInfo(it.packageName) }.filter {
            query.isEmpty() || ((HailData.nineKeySearch && NineKeySearch.search(
                query, it.packageName, it.name.toString()
            )) || FuzzySearch.search(it.packageName, query) || FuzzySearch.search(
                it.name.toString(), query
            ) || PinyinSearch.searchPinyinAll(it.name.toString(), query))
        }.toList()
    }

    private fun updateBarTitle() {
        activity.supportActionBar?.title =
            if (multiselect) getString(R.string.msg_selected, selectedList.size.toString())
            else getString(R.string.app_name)
    }

    override fun onItemClick(info: AppInfo) {
        if (multiselect) {
            if (info in selectedList) selectedList.remove(info)
            else selectedList.add(info)
            updateCurrentList()
            updateBarTitle()
            return
        }
        if (info.applicationInfo == null) {
            Snackbar.make(activity.fab, R.string.app_not_installed, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_remove_home) { removeCheckedApp(info.packageName) }.show()
            return
        }
        if (frozenTab && !HailData.isChecked(info.packageName)) {
            // Unmanaged frozen app: unfreezing is all we are allowed to do.
            if (AppManager.setAppFrozen(info.packageName, false)) updateCurrentList()
            else HUI.showToast(R.string.permission_denied)
            return
        }
        launchApp(info.packageName)
    }

    override fun onItemLongClick(info: AppInfo): Boolean {
        if (info.applicationInfo == null && (!multiselect || info !in selectedList)) {
            exportToClipboard(listOf(info))
            return true
        }
        if (info in selectedList) {
            onMultiSelect()
            return true
        }
        val pkg = info.packageName
        if (frozenTab && !HailData.isChecked(pkg)) {
            // Unmanaged frozen app: unfreezing is all we are allowed to do.
            MaterialAlertDialogBuilder(activity).setTitle(info.name).setItems(
                arrayOf(getString(R.string.action_unfreeze))
            ) { _, _ ->
                if (AppManager.setAppFrozen(pkg, false)) updateCurrentList()
                else HUI.showToast(R.string.permission_denied)
            }.setNeutralButton(R.string.action_details) { _, _ ->
                HUI.startActivity(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS, HPackages.packageUri(pkg)
                )
            }.setNegativeButton(android.R.string.cancel, null).show()
            return true
        }
        val frozen = AppManager.isAppFrozen(pkg)
        val action = getString(if (frozen) R.string.action_unfreeze else R.string.action_freeze)
        MaterialAlertDialogBuilder(activity).setTitle(info.name).setItems(
            resources.getStringArray(R.array.home_action_entries).filter {
                (it != getString(R.string.action_freeze) || !frozen) && (it != getString(R.string.action_unfreeze) || frozen) && (it != getString(
                    R.string.action_pin
                ) || !info.pinned) && (it != getString(R.string.action_unpin) || info.pinned) && (it != getString(
                    R.string.action_whitelist
                ) || !info.whitelisted) && (it != getString(R.string.action_remove_whitelist) || info.whitelisted) && (it != getString(
                    R.string.action_unfreeze_remove_home
                ) || frozen)
            }.toTypedArray()
        ) { _, which ->
            when (which) {
                0 -> launchApp(pkg)
                1 -> setListFrozen(!frozen, listOf(info))
                2 -> {
                    val values = resources.getIntArray(R.array.deferred_task_values)
                    val entries = arrayOfNulls<String>(values.size)
                    values.forEachIndexed { i, it ->
                        entries[i] = resources.getQuantityString(R.plurals.deferred_task_entry, it, it)
                    }
                    MaterialAlertDialogBuilder(activity).setTitle(R.string.action_deferred_task)
                        .setItems(entries) { _, i ->
                            HWork.setDeferredFrozen(pkg, !frozen, values[i].toLong())
                            Snackbar.make(
                                activity.fab, resources.getQuantityString(
                                    R.plurals.msg_deferred_task, values[i], values[i], action, info.name
                                ), Snackbar.LENGTH_INDEFINITE
                            ).setAction(R.string.action_undo) { HWork.cancelWork(pkg) }.show()
                        }.setNegativeButton(android.R.string.cancel, null).show()
                }

                3 -> {
                    info.pinned = !info.pinned
                    HailData.saveApps()
                    updateCurrentList()
                }

                4 -> {
                    info.whitelisted = !info.whitelisted
                    HailData.saveApps()
                    updateCurrentList()
                }

                5 -> tagDialog(info)

                6 -> HShortcuts.addPinShortcut(
                    info, pkg, info.name, HailApi.getIntentForPackage(HailApi.ACTION_LAUNCH, pkg)
                )

                7 -> exportToClipboard(listOf(info))
                8 -> removeCheckedApp(pkg)
                9 -> {
                    // Remove from management first, to keep the swipe-freeze service away,
                    // then unfreeze so the app cannot be auto-frozen again.
                    removeCheckedApp(pkg, false)
                    HailData.saveApps()
                    setListFrozen(false, listOf(info))
                }
            }
        }.setNeutralButton(R.string.action_details) { _, _ ->
            HUI.startActivity(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, HPackages.packageUri(pkg)
            )
        }.setNegativeButton(android.R.string.cancel, null).show()
        return true
    }

    private fun tagDialog(info: AppInfo) {
        val initialIndex = HailData.tags.indexOfFirst { it.id == info.tagId }.coerceAtLeast(0)
        var selected = initialIndex
        MaterialAlertDialogBuilder(activity).setTitle(R.string.action_tag_set).setSingleChoiceItems(
            HailData.tags.map { it.name }.toTypedArray(), initialIndex
        ) { _, index ->
            selected = index
        }.setPositiveButton(android.R.string.ok) { _, _ ->
            info.tagId = HailData.tags[selected].id
            HailData.saveApps()
            updateCurrentList()
        }.setNeutralButton(R.string.action_tag_add) { _, _ ->
            showTagDialog(listOf(info))
        }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun deselect(update: Boolean = true) {
        selectedList.clear()
        if (!update) return
        updateCurrentList()
        updateBarTitle()
    }

    private fun onMultiSelect() {
        MaterialAlertDialogBuilder(activity).setTitle(
            getString(
                R.string.msg_selected, selectedList.size.toString()
            )
        ).setItems(
            intArrayOf(
                R.string.action_freeze,
                R.string.action_unfreeze,
                R.string.action_tag_set,
                R.string.action_export_clipboard,
                R.string.action_remove_home,
                R.string.action_unfreeze_remove_home,
                R.string.action_add_pin_shortcut
            ).map { getString(it) }.toTypedArray()
        ) { dialog, which ->
            when (which) {
                0 -> {
                    setListFrozen(true, selectedList, false)
                    deselect()
                }

                1 -> {
                    setListFrozen(false, selectedList, false)
                    deselect()
                }

                2 -> singleTagDialog()

                3 -> {
                    exportToClipboard(selectedList)
                    deselect()
                }

                4 -> {
                    selectedList.forEach { removeCheckedApp(it.packageName, false) }
                    HailData.saveApps()
                    deselect()
                }

                5 -> {
                    val apps = selectedList.toList()
                    viewLifecycleOwner.lifecycleScope.launch {
                        HBatch.run(
                            requireActivity(), apps, R.string.action_unfreeze, label = { it.name }
                        ) {
                            // Remove from management first, then unfreeze, so the
                            // swipe-freeze service cannot interfere in between.
                            HailData.removeCheckedApp(it.packageName, false)
                            AppManager.setAppFrozen(it.packageName, false)
                        }
                        HailData.saveApps()
                        if (_binding != null) deselect()
                    }
                }

                6 -> {
                    dialog.dismiss()
                    createShortcuts()
                }
            }
        }.setNegativeButton(R.string.action_deselect) { _, _ ->
            deselect()
        }.setNeutralButton(R.string.action_select_all) { _, _ ->
            selectedList.addAll(pagerAdapter.currentList.filterNot { it in selectedList })
            updateCurrentList()
            updateBarTitle()
            onMultiSelect()
        }.show()
    }

    /**
     * Requests home screen shortcuts for the selected frozen apps.
     * Unfrozen apps already have their own launcher icons; apps without
     * a launcher activity cannot be launched from a shortcut either.
     */
    private fun createShortcuts() {
        val apps = selectedList.filter {
            it.applicationInfo != null && AppManager.isAppFrozen(it.packageName)
                    && it.hasLauncherActivity()
        }
        if (apps.isEmpty()) HUI.showToast(R.string.msg_shortcuts_none)
        else MaterialAlertDialogBuilder(activity).setTitle(R.string.action_add_pin_shortcut)
            .setMessage(getString(R.string.msg_add_shortcuts, apps.size.toString()))
            .setPositiveButton(android.R.string.ok) { _, _ -> requestShortcuts(apps) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    /**
     * [PackageManager.getLaunchIntentForPackage] returns null for frozen apps,
     * as their launcher components are disabled or invisible to a default query.
     */
    private fun AppInfo.hasLauncherActivity(): Boolean = app.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName),
        PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
                or PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
    ).isNotEmpty()

    private fun requestShortcuts(apps: List<AppInfo>) {
        var cancelled = false
        val progress = MaterialAlertDialogBuilder(activity).setTitle(R.string.action_add_pin_shortcut)
            .setMessage(getString(R.string.msg_adding_shortcuts, "0", apps.size.toString()))
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelled = true }.show()
        progress.setOnCancelListener { cancelled = true } // Back key press
        viewLifecycleOwner.lifecycleScope.launch {
            val requested = HShortcuts.requestBatchPinShortcuts(
                apps, { done, total ->
                    progress.setMessage(getString(R.string.msg_adding_shortcuts, done.toString(), total.toString()))
                },
                { awaitShortcutDialogClose() }
            ) { !cancelled && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
            if (_binding != null) {
                runCatching { progress.dismiss() }
                if (requested > 0) HUI.showToast(
                    getString(R.string.msg_shortcuts_requested, requested.toString())
                ) else HUI.showToast(R.string.operation_failed, getString(R.string.action_add_pin_shortcut))
                deselect()
            }
        }
    }

    /**
     * Waits until the launcher's confirmation dialog for the previous pin request is handled,
     * as a new request would instantly replace the still-showing one.
     * The dialog pauses Hail; when the user accepts or dismisses it, Hail becomes resumed again.
     * The timeout falls back for launchers that pause Hail differently or never close the dialog.
     */
    private suspend fun awaitShortcutDialogClose() {
        val settleMillis = 500L // Let the dialog appear (and pause Hail) first
        val closeTimeoutMillis = 30_000L
        val pollMillis = 200L
        delay(settleMillis)
        withTimeoutOrNull(closeTimeoutMillis) {
            while (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) delay(pollMillis)
        }
        delay(settleMillis) // Gap before the next dialog
    }

    private fun singleTagDialog() {
        val initialIndex =
            if (selectedList.all { it.tagId == selectedList.first().tagId }) HailData.tags.indexOfFirst { it.id == selectedList.first().tagId }
                .coerceAtLeast(0) else -1
        var selected = initialIndex
        MaterialAlertDialogBuilder(activity).setTitle(R.string.action_tag_set).setSingleChoiceItems(
            HailData.tags.map { it.name }.toTypedArray(), initialIndex
        ) { _, index ->
            selected = index
        }.setPositiveButton(android.R.string.ok) { _, _ ->
            if (selected >= 0) {
                selectedList.forEach { it.tagId = HailData.tags[selected].id }
                HailData.saveApps()
            }
            deselect()
        }.setNeutralButton(R.string.action_tag_add) { _, _ ->
            showTagDialog(selectedList)
        }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun launchApp(packageName: String) {
        if (AppManager.isAppFrozen(packageName) && AppManager.setAppFrozen(packageName, false)) {
            updateCurrentList()
        }
        app.packageManager.getLaunchIntentForPackage(packageName)?.let {
            HShortcuts.addDynamicShortcut(packageName)
            startActivity(it)
        } ?: HUI.showToast(R.string.activity_not_found)
    }

    private fun setListFrozen(
        frozen: Boolean, list: List<AppInfo> = HailData.checkedList, updateList: Boolean = true
    ) {
        val filtered =
            list.filter { it.packageName != BuildConfig.APPLICATION_ID && AppManager.isAppFrozen(it.packageName) != frozen }
        viewLifecycleOwner.lifecycleScope.launch {
            val result = HBatch.run(
                requireActivity(), filtered,
                if (frozen) R.string.action_freeze else R.string.action_unfreeze,
                label = { it.name }
            ) { AppManager.setAppFrozen(it.packageName, frozen) }
            if (_binding == null) return@launch
            if (result.succeeded == 0 && result.failed > 0) HUI.showToast(R.string.permission_denied)
            else HUI.showToast(
                if (frozen) R.string.msg_freeze else R.string.msg_unfreeze,
                if (result.succeeded == 1) result.lastSucceeded else result.succeeded.toString()
            )
            if (updateList) updateCurrentList()
            app.setAutoFreezeService()
        }
    }

    /**
     * Tag editor dialog.
     * [list] == null: edit the current tab's tag (long-press on the tab);
     * otherwise: create a new tag, then reopen the assignment dialog for [list].
     */
    private fun showTagDialog(list: List<AppInfo>? = null) {
        val tabLayout = tabs ?: return  // The view has been destroyed; return directly.
        val homeAdapter = adapter ?: return
        // The virtual frozen tab is fixed at the end; user tags map 1:1 to tab positions.
        val tabPosition = tabLayout.selectedTabPosition
        val tagIndex = tabPosition // Index into HailData.tags
        val currentTag = if (list == null) HailData.tags.getOrNull(tagIndex) else null
        if (list == null && currentTag == null) return

        val bgState = mutableStateOf(currentTag?.autoFreezeBackground ?: false)
        val bgDelayState = mutableStateOf(currentTag?.autoFreezeBackgroundDelay ?: 0f)
        val lockState = mutableStateOf(currentTag?.autoFreezeLock ?: false)
        val lockDelayState = mutableStateOf(currentTag?.autoFreezeLockDelay ?: 0f)

        // A ComposeView inside an AlertDialog never gets an input method session on some ROMs
        // ("Ignoring showSoftInput() as view is not served"), so the name input is a classic
        // EditText (the IME anchor) and only the switches/sliders below are Compose.
        val density = activity.resources.displayMetrics.density
        val nameEditText = EditText(activity).apply {
            hint = activity.getString(R.string.tag)
            isSingleLine = true
            setText(currentTag?.name)
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (8 * density).toInt(), (24 * density).toInt(), 0)
            addView(nameEditText)
            addView(
                ComposeView(activity).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        AppTheme {
                            TagEditor(bgState, bgDelayState, lockState, lockDelayState)
                        }
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (12 * density).toInt() }
            )
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(if (list == null) R.string.action_tag_edit else R.string.action_tag_add)
            .setView(content)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = nameEditText.text?.toString() ?: String()
                if (newName.isBlank()) return@setPositiveButton
                if (list == null) { // Edit tag
                    val defaultTab = currentTag!!.id == 0
                    val newId = if (defaultTab) 0 else newName.hashCode()
                    if (HailData.tags.any { it !== currentTag && (it.name == newName || it.id == newId) }) {
                        return@setPositiveButton
                    }
                    val oldId = currentTag.id
                    currentTag.name = newName
                    if (!defaultTab) {
                        HailData.checkedList.forEach { if (it.tagId == oldId) it.tagId = newId }
                        currentTag.id = newId
                    }
                    currentTag.autoFreezeBackground = bgState.value
                    currentTag.autoFreezeBackgroundDelay = bgDelayState.value
                    currentTag.autoFreezeLock = lockState.value
                    currentTag.autoFreezeLockDelay = lockDelayState.value
                    HailData.saveApps()
                    HailData.saveTags()
                    homeAdapter.notifyItemChanged(tabPosition)
                    tabLayout.getTabAt(tabPosition)?.text = newName
                    app.setAutoFreezeService()
                } else { // Add tag
                    val tagId = newName.hashCode()
                    if (HailData.tags.any { it.name == newName || it.id == tagId }) return@setPositiveButton
                    HailData.tags.add(
                        Tag(
                            newName, tagId, bgState.value, bgDelayState.value, lockState.value, lockDelayState.value
                        )
                    )
                    // New tag lands before the fixed frozen tab, which is always the last one.
                    homeAdapter.notifyItemInserted(homeAdapter.itemCount - 2)
                    HailData.saveTags()
                    app.setAutoFreezeService()
                    if (list == selectedList) singleTagDialog() else tagDialog(list.first())
                }
            }.apply {
                if (list == null && tagIndex > 0) {
                    setNeutralButton(R.string.action_tag_remove) { _, _ ->
                        val removedId = HailData.tags[tagIndex].id
                        HailData.checkedList.forEach { if (it.tagId == removedId) it.tagId = 0 }
                        HailData.tags.removeAt(tagIndex)
                        homeAdapter.notifyItemRemoved(tabPosition)
                        HailData.saveApps()
                        HailData.saveTags()
                        app.setAutoFreezeService()
                        updateCurrentList()
                    }
                }
            }.setNegativeButton(android.R.string.cancel, null).create()
        // Show the IME for the EditText once the dialog window is up.
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        dialog.show()
        nameEditText.requestFocus()
        nameEditText.postDelayed({
            if (!dialog.isShowing) return@postDelayed
            nameEditText.requestFocus()
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(nameEditText, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    @Composable
    private fun TagEditor(
        bg: MutableState<Boolean>,
        bgDelay: MutableState<Float>,
        lock: MutableState<Boolean>,
        lockDelay: MutableState<Float>,
    ) = Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.auto_freeze),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.auto_freeze_background),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(checked = bg.value, onCheckedChange = { bg.value = it })
        }
        if (bg.value) {
            Slider(
                value = bgDelay.value,
                onValueChange = { bgDelay.value = it },
                valueRange = 0f..600f,
                steps = 19
            )
            Text(
                text = if (bgDelay.value == 0f) stringResource(R.string.freeze_immediately)
                else stringResource(R.string.seconds_format, bgDelay.value.toInt()),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.auto_freeze_after_lock),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(checked = lock.value, onCheckedChange = { lock.value = it })
        }
        if (lock.value) {
            Slider(
                value = lockDelay.value,
                onValueChange = { lockDelay.value = it },
                valueRange = 0f..600f,
                steps = 19
            )
            Text(
                text = if (lockDelay.value == 0f) stringResource(R.string.freeze_immediately)
                else stringResource(R.string.seconds_format, lockDelay.value.toInt()),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    private fun exportToClipboard(list: List<AppInfo>) {
        if (list.isEmpty()) return
        HUI.copyText(if (list.size > 1) JSONArray().run {
            list.forEach { put(it.packageName) }
            toString()
        } else list[0].packageName)
        HUI.showToast(
            R.string.msg_exported, if (list.size > 1) list.size.toString() else list[0].name
        )
    }

    private fun importFromClipboard() = runCatching {
        val str = HUI.pasteText() ?: throw IllegalArgumentException()
        val json = if (str.contains('[')) JSONArray(
            str.substring(
                str.indexOf('[')..str.indexOf(']', str.indexOf('['))
            )
        )
        else JSONArray().put(str)
        var i = 0
        for (index in 0 until json.length()) {
            val pkg = json.getString(index)
            if (HPackages.getApplicationInfoOrNull(pkg) != null && !HailData.isChecked(pkg)) {
                HailData.addCheckedApp(pkg, tag?.id?.takeIf { it != HailData.FROZEN_TAB_ID } ?: 0, false)
                i++
            }
        }
        if (i > 0) {
            HailData.saveApps()
            updateCurrentList()
        }
        HUI.showToast(getString(R.string.msg_imported, i.toString()))
    }

    private suspend fun importFrozenApp() = withContext(Dispatchers.IO) {
        val tagId = tag?.id?.takeIf { it != HailData.FROZEN_TAB_ID } ?: 0
        HPackages.getInstalledApplications().map { it.packageName }
            .filter { AppManager.isAppFrozen(it) && !HailData.isChecked(it) }
            .onEach { HailData.addCheckedApp(it, tagId, false) }.size
    }

    private fun removeCheckedApp(packageName: String, saveApps: Boolean = true) {
        HailData.removeCheckedApp(packageName, saveApps)
        if (saveApps) updateCurrentList()
    }

    private fun MenuItem.updateIcon() = icon?.setTint(
        MaterialColors.getColor(
            activity.findViewById(R.id.toolbar),
            if (multiselect) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorOnSurface
        )
    )

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_multiselect -> {
                multiselect = !multiselect
                item.updateIcon()
                if (multiselect) {
                    updateBarTitle()
                    HUI.showToast(R.string.tap_to_select)
                } else deselect()
            }

            R.id.action_freeze_current -> setListFrozen(true, pagerAdapter.currentList.filterNot { it.whitelisted })

            R.id.action_unfreeze_current -> setListFrozen(false, pagerAdapter.currentList)
            R.id.action_freeze_all -> setListFrozen(true)
            R.id.action_unfreeze_all -> setListFrozen(false)
            R.id.action_freeze_non_whitelisted -> setListFrozen(true, HailData.checkedList.filterNot { it.whitelisted })

            R.id.action_import_clipboard -> importFromClipboard()
            R.id.action_import_frozen -> lifecycleScope.launch {
                val size = importFrozenApp()
                if (size > 0) {
                    HailData.saveApps()
                    updateCurrentList()
                }
                HUI.showToast(getString(R.string.msg_imported, size.toString()))
            }

            R.id.action_export_current -> exportToClipboard(pagerAdapter.currentList)
            R.id.action_export_all -> exportToClipboard(HailData.checkedList)
        }
        return false
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_home, menu)
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        if (HailData.nineKeySearch) {
            val editText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
            editText.inputType = InputType.TYPE_CLASS_PHONE
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            private var inited = false
            override fun onQueryTextChange(newText: String): Boolean {
                if (inited) {
                    query = newText
                    tabs?.run {
                        isVisible = query.isEmpty()
                    }
                    updateCurrentList()
                } else inited = true
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean = true
        })
        menu.findItem(R.id.action_multiselect).updateIcon()
    }

    override fun onDestroyView() {
        activity?.fab?.setOnClickListener(null)
        activity?.fab?.setOnLongClickListener(null)
        pagerAdapter.onDestroy()
        super.onDestroyView()
        _binding = null
    }
}