package io.github.AlexIllinois2.snub.app

import android.content.pm.ApplicationInfo
import io.github.AlexIllinois2.snub.HailApp.Companion.app
import io.github.AlexIllinois2.snub.utils.HPackages

class AppInfo(
    val packageName: String,
    var pinned: Boolean = false,
    var whitelisted: Boolean = false,
    var tagId: Int = 0
) {
    enum class State { NOT_FOUND, UNFROZEN, FROZEN }

    val applicationInfo: ApplicationInfo? get() = HPackages.getApplicationInfoOrNull(packageName)
    val name get() = applicationInfo?.loadLabel(app.packageManager) ?: packageName
    val state
        get() = when {
            applicationInfo == null -> State.NOT_FOUND
            AppManager.isAppFrozen(packageName) -> State.FROZEN
            else -> State.UNFROZEN
        }

    override fun equals(other: Any?): Boolean = other is AppInfo && other.packageName == packageName
    override fun hashCode(): Int = packageName.hashCode()
}