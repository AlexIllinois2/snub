package io.github.AlexIllinois2.snub.services

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import io.github.AlexIllinois2.snub.R
import io.github.AlexIllinois2.snub.app.HailApi
import io.github.AlexIllinois2.snub.app.HailData
import io.github.AlexIllinois2.snub.utils.HTarget

@RequiresApi(Build.VERSION_CODES.N)
class QSTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(
            when (HailData.tileAction) {
                HailData.ACTION_FREEZE_ALL -> HailApi.ACTION_FREEZE_ALL
                HailData.ACTION_FREEZE_NON_WHITELISTED -> HailApi.ACTION_FREEZE_NON_WHITELISTED
                HailData.ACTION_LOCK -> HailApi.ACTION_LOCK
                HailData.ACTION_LOCK_FREEZE -> HailApi.ACTION_LOCK_FREEZE
                else -> HailApi.ACTION_UNFREEZE_ALL
            }
        ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (HTarget.U) startActivityAndCollapse(
            PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
        )
        else {
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    override fun onTileAdded() {
        updateTile()
    }

    private fun updateTile() {
        qsTile.icon = Icon.createWithResource(
            this, when (HailData.tileAction) {
                HailData.ACTION_UNFREEZE_ALL -> R.drawable.ic_round_unfrozen
                HailData.ACTION_LOCK, HailData.ACTION_LOCK_FREEZE -> R.drawable.ic_outline_lock
                else -> R.drawable.ic_round_frozen
            }
        )
        qsTile.label = resources.getStringArray(R.array.tile_action_entries)[
            HailData.TILE_ACTION_VALUES.indexOf(HailData.tileAction).coerceAtLeast(0)
        ]
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.updateTile()
    }
}