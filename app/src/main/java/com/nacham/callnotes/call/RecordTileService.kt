package com.nacham.callnotes.call

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nacham.callnotes.audio.RecorderService

/** One-tap manual recording from the Quick Settings shade. */
class RecordTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        if (tile.state == Tile.STATE_ACTIVE) {
            RecorderService.stop(this)
            setState(Tile.STATE_INACTIVE)
        } else {
            RecorderService.start(this, CallSource.MANUAL.name, "Manual recording")
            setState(Tile.STATE_ACTIVE)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        // Reflect current state best-effort; RecorderService is the source of truth.
        qsTile?.let { if (it.state == Tile.STATE_UNAVAILABLE) setState(Tile.STATE_INACTIVE) }
    }

    private fun setState(state: Int) {
        qsTile?.apply { this.state = state; updateTile() }
    }
}
