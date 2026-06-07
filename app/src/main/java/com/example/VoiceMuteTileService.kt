package com.example

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class VoiceMuteTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val vm = VoiceViewModel.instance
        if (vm != null) {
            vm.toggleMute()
            updateTile()
        } else {
            Log.d("VoiceMuteTileService", "VoiceViewModel.instance is null during click")
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val vm = VoiceViewModel.instance
        if (vm == null) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "Voice Mute"
            tile.updateTile()
            return
        }
        val isMuted = vm.isMuted.value
        tile.state = if (isMuted) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isMuted) "Mic Muted" else "Mic Active"
        tile.updateTile()
    }
}
