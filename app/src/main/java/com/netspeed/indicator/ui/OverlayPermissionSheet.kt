package com.netspeed.indicator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sliding bottom sheet asking for the overlay permission — shown BEFORE the bare
 * system "Display over other apps" screen so the user reads what it unlocks and
 * that nothing leaves the device. Gives a clear primary CTA (Allow) plus a
 * graceful "no": use the status-bar icon instead, which needs no overlay. Swipe
 * down / scrim / back = "not now" (handled by the caller, which then surfaces a
 * persistent re-enable banner so the app is never left blank).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayPermissionSheet(
    onAllow: () -> Unit,
    onUseStatusBar: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp)
                .navigationBarsPadding(),
        ) {
            OverlayIllustration()
            Spacer(Modifier.height(20.dp))
            Text(
                "Let the speed bubble float on top",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "NetSpeed needs the “Display over other apps” permission so your live " +
                    "speed can float over anything — games, video, any app. It never " +
                    "leaves your phone; no internet is used.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Allow", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Opens Android settings — turn NetSpeed on, then tap back.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onUseStatusBar,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("Use the status-bar icon instead", fontSize = 15.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "No permission needed — a small live icon by the clock.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * A tiny diorama: a generic "other app" behind, the NetSpeed speed bubble
 * floating on top-right — the exact thing the permission enables.
 */
@Composable
private fun OverlayIllustration() {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF20304B), Color(0xFF141C2B))),
            ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(0.62f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.10f)),
            )
            Spacer(Modifier.height(12.dp))
            repeat(3) { i ->
                Box(
                    Modifier
                        .fillMaxWidth(if (i == 2) 0.4f else 0.7f)
                        .height(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                )
                Spacer(Modifier.height(9.dp))
            }
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-16).dp, y = 16.dp)
                .shadow(10.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(Color(0xF2101218))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent),
            )
            Spacer(Modifier.width(8.dp))
            Text("↓ 8.3", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text("MB/s", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
        }
    }
}
