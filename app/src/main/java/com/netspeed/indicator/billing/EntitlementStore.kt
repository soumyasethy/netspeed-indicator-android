package com.netspeed.indicator.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Separate DataStore from settings: entitlement is security-adjacent state. */
private val Context.entitlementStore: DataStore<Preferences> by
    preferencesDataStore(name = "netspeed_entitlement")

/**
 * Local cache of the user's [Entitlement], so premium gating is correct instantly
 * on launch — before [BillingManager] has connected and re-queried Play. The
 * cache is best-effort: [BillingManager] is the source of truth and overwrites it
 * on every refresh. (Server-side validation is intentionally out of scope for a
 * Rs.29 one-time unlock; it arrives with the LLM app, which needs a backend anyway.)
 */
class EntitlementStore(private val context: Context) {

    val entitlement: Flow<Entitlement> = context.entitlementStore.data.map { p ->
        Entitlement(suiteUnlocked = p[KEY_SUITE] ?: false)
    }

    suspend fun setSuiteUnlocked(unlocked: Boolean) {
        context.entitlementStore.edit { it[KEY_SUITE] = unlocked }
    }

    private companion object {
        val KEY_SUITE = booleanPreferencesKey("suite_unlocked")
    }
}
