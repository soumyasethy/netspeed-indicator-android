package com.netspeed.indicator.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.netspeed.indicator.data.SettingsRepository
import com.netspeed.indicator.service.SpeedMeterService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-arms the indicator after a reboot, but only if the user had it enabled.
 * Reboot also resets TrafficStats counters, which is fine: the service takes a
 * fresh baseline on its first tick.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        // goAsync lets us read DataStore off the main thread without dropping the broadcast.
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = SettingsRepository(appContext).settings.first().enabled
                if (enabled) {
                    // startForegroundService is legal from a BOOT_COMPLETED receiver;
                    // the service calls startForeground() inside its start window.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        SpeedMeterService.start(appContext)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
