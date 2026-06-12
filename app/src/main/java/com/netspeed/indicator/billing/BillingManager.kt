package com.netspeed.indicator.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Thin wrapper over Play Billing v7. Responsibilities:
 *  - connect the [BillingClient] and keep entitlement in sync,
 *  - expose live [entitlement] + formatted [priceSuite] / [priceTip] as flows,
 *  - launch the purchase flow for the suite unlock and the optional tip,
 *  - acknowledge the (non-consumable) unlock and consume the (consumable) tip.
 *
 * Entitlement is derived by the pure [Entitlement.from] and mirrored into
 * [EntitlementStore] so gating is correct offline / before this connects. No
 * backend — fine for a Rs.29 one-time unlock (see [EntitlementStore]).
 */
class BillingManager(
    context: Context,
    private val scope: CoroutineScope,
    private val store: EntitlementStore,
) {
    private val appContext = context.applicationContext

    private val _entitlement = MutableStateFlow(Entitlement.LOCKED)
    val entitlement: StateFlow<Entitlement> = _entitlement.asStateFlow()

    private val _priceSuite = MutableStateFlow<String?>(null)
    val priceSuite: StateFlow<String?> = _priceSuite.asStateFlow()

    private val _priceTip = MutableStateFlow<String?>(null)
    val priceTip: StateFlow<String?> = _priceTip.asStateFlow()

    private val productDetails = mutableMapOf<String, ProductDetails>()

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch { reconcile(purchases) }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "purchase cancelled by user")
        } else {
            Log.w(TAG, "purchase update failed: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    /** Connects and does the first product + purchase sync. Safe to call once. */
    fun start() {
        if (client.isReady) {
            scope.launch { queryProducts(); refreshPurchases() }
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { queryProducts(); refreshPurchases() }
                } else {
                    Log.w(TAG, "billing setup: ${result.responseCode} ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.i(TAG, "billing disconnected")
            }
        })
    }

    fun buySuite(activity: Activity) = launchFlow(activity, ProductIds.SUITE_UNLOCK)
    fun buyTip(activity: Activity) = launchFlow(activity, ProductIds.TIP_SMALL)

    /** Re-query owned purchases (e.g. a "Restore purchases" button). */
    fun restore() {
        scope.launch { refreshPurchases() }
    }

    /**
     * DEBUG ONLY — callers must guard with BuildConfig.DEBUG. Flips local
     * entitlement so the locked/unlocked UI can be verified without Play product
     * setup. Never wire this into a release build path.
     */
    fun debugSetUnlocked(unlocked: Boolean) {
        scope.launch {
            store.setSuiteUnlocked(unlocked)
            _entitlement.value = Entitlement(suiteUnlocked = unlocked)
        }
    }

    fun release() {
        runCatching { client.endConnection() }
    }

    // --- internals ------------------------------------------------------------

    private fun launchFlow(activity: Activity, productId: String) {
        val pd = productDetails[productId]
        if (pd == null) {
            Log.w(TAG, "no ProductDetails for $productId (not configured in Play Console yet?)")
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    private suspend fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ProductIds.ONE_TIME.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()
        val result = client.queryProductDetails(params)
        result.productDetailsList?.forEach { pd ->
            productDetails[pd.productId] = pd
            val price = pd.oneTimePurchaseOfferDetails?.formattedPrice
            when (pd.productId) {
                ProductIds.SUITE_UNLOCK -> _priceSuite.value = price
                ProductIds.TIP_SMALL -> _priceTip.value = price
            }
        }
    }

    private suspend fun refreshPurchases() {
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        )
        reconcile(result.purchasesList)
    }

    /**
     * Acknowledge the non-consumable unlock, consume the tip, then recompute and
     * persist entitlement. Idempotent — safe to call from both the live listener
     * and the launch-time refresh.
     */
    private suspend fun reconcile(purchases: List<Purchase>) {
        val owned = mutableSetOf<String>()
        for (p in purchases) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (ProductIds.TIP_SMALL in p.products) {
                // Consumable tip → consume so it can be bought again.
                runCatching {
                    client.consumePurchase(
                        ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build(),
                    )
                }
                continue
            }
            if (!p.isAcknowledged) {
                runCatching {
                    client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(p.purchaseToken).build(),
                    )
                }
            }
            owned.addAll(p.products)
        }
        val ent = Entitlement.from(owned)
        store.setSuiteUnlocked(ent.suiteUnlocked)
        _entitlement.value = ent
    }

    private companion object {
        const val TAG = "BillingManager"
    }
}
