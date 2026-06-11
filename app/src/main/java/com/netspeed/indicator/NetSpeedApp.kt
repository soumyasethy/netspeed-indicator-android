package com.netspeed.indicator

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Minimal Application holder. Exposes an [appScope] that outlives any Activity so
 * settings writes (DataStore) always complete even if the user changes a setting
 * and immediately closes the app — using the Activity's lifecycleScope would
 * cancel an in-flight write and silently lose the change.
 */
class NetSpeedApp : Application() {
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
