package com.netspeed.indicator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netspeed.indicator.billing.Entitlement
import com.netspeed.indicator.billing.FeatureGate
import com.netspeed.indicator.data.ColorSkin

/**
 * Skin picker as palette preview cards: each card shows the skin's actual hero
 * gradient, foreground sample ("8.4" in the skin's own typeface) and accent dot
 * — what the WHOLE app will look like, not a name to guess from.
 */
@Composable
fun SkinPreviewRow(
    selected: ColorSkin,
    unlocked: Boolean,
    onSelect: (ColorSkin) -> Unit,
    onLocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Skin",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 20.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            items(ColorSkin.entries.size) { i ->
                val skin = ColorSkin.entries[i]
                val locked = !FeatureGate.skinAllowed(skin.ordinal, Entitlement(unlocked))
                SkinCard(
                    skin = skin,
                    selected = skin == selected,
                    locked = locked,
                    onPick = { if (locked) onLocked() else onSelect(skin) },
                )
            }
        }
    }
}

@Composable
private fun SkinCard(skin: ColorSkin, selected: Boolean, locked: Boolean, onPick: () -> Unit) {
    // TIER has no fixed palette (it follows live speed) — show the brand trio.
    val gradient = if (skin.heroColors.size >= 2) skin.heroColors
    else listOf(Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFFEC4899))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.size(width = 92.dp, height = 90.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 92.dp, height = 62.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(gradient))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                ),
        ) {
            Text(
                "8.4",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontFamily = if (skin.mono) FontFamily.Monospace else FontFamily.Default,
                color = skin.heroFg,
                modifier = Modifier.align(Alignment.Center),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(skin.accent)
                    .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
            )
            if (locked) {
                Text(
                    "🔒",
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp),
                )
            }
        }
        Text(
            skin.label,
            fontSize = 10.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else 0.65f),
        )
    }
}
