package org.torproject.android.ui.quicksettings

import android.service.quicksettings.TileService
import android.widget.Toast
import org.torproject.android.R
import org.torproject.android.service.OrbotConstants
import org.torproject.android.util.canStartForegroundServices
import org.torproject.android.util.sendIntentToService

class RefreshConnectionTileService : TileService() {

    override fun onClick() {
        // can't attempt to send NEWNYM if Tor can't run, make sure we are either a legit VpnService
        // or otherwise in power user mode...
        if (!applicationContext.canStartForegroundServices()) {
            Toast.makeText(
                applicationContext,
                R.string.request_notification_permission,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // attempt to send NEWNYM, if Tor is active, OrbotService will display a Toast
        applicationContext.sendIntentToService(OrbotConstants.LOCAL_ACTION_QUICK_SETTINGS_NEWNYM)
    }
}