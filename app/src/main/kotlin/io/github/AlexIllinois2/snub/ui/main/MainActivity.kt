package io.github.AlexIllinois2.snub.ui.main

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.MenuCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import io.github.AlexIllinois2.snub.R
import io.github.AlexIllinois2.snub.app.HailData
import io.github.AlexIllinois2.snub.databinding.ActivityMainBinding
import io.github.AlexIllinois2.snub.extensions.*
import io.github.AlexIllinois2.snub.ui.apps.AppsFragment
import io.github.AlexIllinois2.snub.utils.HCrash
import io.github.AlexIllinois2.snub.utils.HShell
import io.github.AlexIllinois2.snub.utils.HTarget
import io.github.AlexIllinois2.snub.utils.HUI
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {
    lateinit var fab: ExtendedFloatingActionButton
    lateinit var appbar: AppBarLayout
    private lateinit var binding: ActivityMainBinding

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { requestRootPermission() }

    /** Lets the user pick where to write the recorded crash log; nothing is ever uploaded. */
    private val saveCrashLogLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val uri = result.data?.data ?: return@registerForActivityResult
            val content = pendingCrashLogContent ?: return@registerForActivityResult
            val saved = runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                true
            }.getOrDefault(false)
            if (saved) {
                HCrash.clear()
                HUI.showToast(R.string.msg_crash_saved)
            } else HUI.showToast(R.string.msg_crash_save_failed)
        }

    private var pendingCrashLogs: List<File> = emptyList()
    private var pendingCrashLogContent: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = initView()
        // Some OEM frameworks (Funtouch 3 / API 23) keep an XML-declared host below CREATED until the
        // activity dispatches its own created state, and its NavController throws before that.
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navHostFragment.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                setupNavigation(navHostFragment)
            }
        })
        // Check and request every needed permission once per app process start.
        if (!permissionsRequested) {
            permissionsRequested = true
            requestNeededPermissions()
        }
        // Offer any crash log recorded by a previous process for local saving.
        val crashLogs = HCrash.pendingLogs()
        if (crashLogs.isNotEmpty()) showCrashLogDialog(crashLogs)
        if (!HailData.biometricLogin || BiometricManager.from(this)
                .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS
        ) return
        binding.root.isVisible = false
        val biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    HUI.showToast(errString)
                    finishAndRemoveTask()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    binding.root.isVisible = true
                }
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.action_biometric))
            .setSubtitle(getString(R.string.msg_biometric)).setNegativeButtonText(getString(android.R.string.cancel))
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun initView() = ActivityMainBinding.inflate(layoutInflater).apply {
        setContentView(root)
        setSupportActionBar(appBarMain.toolbar)
        fab = appBarMain.fab
        appbar = appBarMain.appBarLayout

        val isRtl = isRtl
        val isLandscape = isLandscape
        appBarMain.appBarLayout.applyDefaultInsetter {
            paddingRelative(isRtl, start = !isLandscape, end = true, top = true)
        }
        bottomNav?.applyDefaultInsetter { paddingRelative(isRtl, start = true, end = true, bottom = true) }
        navRail?.applyDefaultInsetter { paddingRelative(isRtl, start = true, top = true, bottom = true) }
        fab.applyDefaultInsetter { marginRelative(isRtl, end = true, bottom = isLandscape) }
    }

    private fun setupNavigation(navHostFragment: NavHostFragment) {
        val navController = navHostFragment.navController
        navController.addOnDestinationChangedListener(this@MainActivity)
        val appBarConfiguration = AppBarConfiguration.Builder(
            R.id.nav_home, R.id.nav_apps, R.id.nav_settings, R.id.nav_about
        ).build()
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNav?.setupWithNavController(navController)
        binding.navRail?.setupWithNavController(navController)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.let { MenuCompat.setGroupDividerEnabled(it, true) }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onRestart() {
        super.onRestart()
        // Mark on returning to the foreground, so the first entry to the apps page refreshes the list
        AppsFragment.cameToForeground = true
    }

    /** Requests the notification permission first, then checks root. */
    private fun requestNeededPermissions() {
        if (HTarget.T && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else requestRootPermission()
    }

    private fun requestRootPermission() {
        lifecycleScope.launch {
            // Fires the su grant dialog once; runs off the main thread as su may block.
            if (!withContext(Dispatchers.IO) { HShell.checkSU }) HUI.showToast(R.string.permission_denied)
        }
    }

    /**
     * Asks whether to save the recorded crash log to a user-chosen local file.
     * Declining keeps the log on disk but never offers it again.
     */
    private fun showCrashLogDialog(logs: List<File>) {
        pendingCrashLogs = logs
        // Read the content up front: the files get renamed once the dialog is dismissed.
        pendingCrashLogContent = logs.joinToString("\n\n--------\n\n") { runCatching { it.readText() }.getOrDefault("") }
        val time = SimpleDateFormat.getDateTimeInstance().format(Date(logs.first().lastModified()))
        MaterialAlertDialogBuilder(this).setTitle(R.string.title_crash_log)
            .setMessage(getString(R.string.msg_crash_save, time))
            .setPositiveButton(R.string.action_save) { _, _ ->
                saveCrashLogLauncher.launch(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TITLE,
                            "snub-crash-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".log"
                        )
                    }
                )
            }.setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { logs.forEach { HCrash.markOffered(it) } }.show()
    }

    /* override fun onStop() {
        super.onStop()
        if (HailData.biometricLogin) finishAndRemoveTask()
    } */

    override fun onDestinationChanged(
        controller: NavController, destination: NavDestination, arguments: Bundle?
    ) {
        fab.tag = destination.id == R.id.nav_home
        if (fab.tag == true) fab.show() else fab.hide()
    }

    companion object {
        private var permissionsRequested = false
    }
}