package com.jimz.jimztune.util

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.jimz.jimztune.widget.LyricsWidgetProvider

/** Ubin Pengaturan Cepat: ketuk untuk putar / jeda tanpa membuka aplikasi. */
class PlayPauseTile : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            label = "Aoto"
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        try {
            sendBroadcast(
                Intent(this, LyricsWidgetProvider::class.java).setAction(LyricsWidgetProvider.ACTION_PLAY_PAUSE)
            )
        } catch (_: Exception) {
        }
    }
}
