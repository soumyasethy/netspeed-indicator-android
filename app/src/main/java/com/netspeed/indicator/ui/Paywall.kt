package com.netspeed.indicator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netspeed.indicator.BuildConfig

/**
 * The suite-unlock paywall. Shown when a user taps a premium feature while locked.
 * Anchors a struck-through "regular" price against the early-bird price (charm
 * pricing + credible anchor lift conversion), lists what the one-time unlock grants,
 * and offers an optional Rs.5 tip and a restore-purchases path.
 *
 * Prices come live from Play ([priceSuite] / [priceTip]); when Play products aren't
 * configured yet (or offline) it falls back to the planned launch prices so the
 * sheet is never blank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallSheet(
    priceSuite: String?,
    priceTip: String?,
    onUnlock: () -> Unit,
    onTip: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    debugUnlock: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val suitePrice = priceSuite ?: "₹29"
    val tipPrice = priceTip ?: "₹5"

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(10.dp))
                Text(
                    "Unlock the full suite",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Price anchor: regular (struck) → early-bird.
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "₹99",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textDecoration = TextDecoration.LineThrough,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    suitePrice,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "early-bird · lifetime",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PerkRow("Floating speed bubble over any app")
                PerkRow("All 14 live themes + 6 colour skins")
                PerkRow("All 5 home-screen widgets")
                PerkRow("Custom colour picker")
                PerkRow("Every future lazycode app, included")
            }

            Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
                Text("Unlock everything — $suitePrice")
            }

            Text(
                "One-time payment. No subscription, no ads, ever.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onTip) { Text("Leave a $tipPrice tip 💛") }
                TextButton(onClick = onRestore) { Text("Restore") }
            }

            if (BuildConfig.DEBUG && debugUnlock != null) {
                TextButton(onClick = debugUnlock, modifier = Modifier.fillMaxWidth()) {
                    Text("(debug) simulate unlock", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PerkRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
