package com.netspeed.indicator.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Registered *dynamically* by [com.netspeed.indicator.service.SpeedMeterService]
 * (ACTION_SCREEN_ON/OFF cannot be declared in the manifest — the system only
 * delivers them to runtime-registered receivers). Forwards the on/off edge to
 * the service so it can pause or resume sampling.
 */
class ScreenStateReceiver(
    private val onScreenChanged: (isScreenOn: Boolean) -> Unit,
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SCREEN_ON -> onScreenChanged(true)
            Intent.ACTION_SCREEN_OFF -> onScreenChanged(false)
        }
    }
}
