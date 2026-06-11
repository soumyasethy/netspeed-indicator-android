package com.netspeed.indicator.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * A self-contained HSV colour picker (no third-party libraries) with a
 * saturation/value square, a hue slider, an optional alpha slider, and a live
 * hex field. Hand-typing a hex code updates the wheels and vice-versa.
 *
 * @param allowAlpha true for the icon background (transparency matters); false
 *        for the glyph colour (always opaque).
 */
@Composable
fun ColorPickerDialog(
    initial: Int,
    allowAlpha: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    // Seed from the live theme accent (not a fixed blue) when starting from transparent.
    val themeSeed = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val seed = if (AndroidColor.alpha(initial) == 0)
        android.graphics.Color.argb(255, (themeSeed.red*255).toInt(), (themeSeed.green*255).toInt(), (themeSeed.blue*255).toInt())
    else initial
    val hsv = remember { FloatArray(3).also { AndroidColor.colorToHSV(seed, it) } }
    var hue by remember { mutableStateOf(hsv[0]) }
    var sat by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }
    var alpha by remember { mutableIntStateOf(if (allowAlpha) AndroidColor.alpha(seed) else 255) }

    val color = AndroidColor.HSVToColor(alpha, floatArrayOf(hue, sat, value))
    var hexField by remember { mutableStateOf(toHex(color, allowAlpha)) }

    // Keep the hex field in sync whenever the wheels move.
    fun syncHex() { hexField = toHex(AndroidColor.HSVToColor(alpha, floatArrayOf(hue, sat, value)), allowAlpha) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Custom colour", style = MaterialTheme.typography.titleMedium)

                // Saturation / value square.
                SatValSquare(hue = hue, sat = sat, value = value) { s, v ->
                    sat = s; value = v; syncHex()
                }
                // Hue slider.
                HueSlider(hue = hue) { h -> hue = h; syncHex() }
                if (allowAlpha) {
                    AlphaSlider(alpha = alpha, baseColor = AndroidColor.HSVToColor(255, floatArrayOf(hue, sat, value))) {
                        alpha = it; syncHex()
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(color)),
                    )
                    OutlinedTextField(
                        value = hexField,
                        onValueChange = { raw ->
                            hexField = raw
                            parseHex(raw)?.let { parsed ->
                                val h = FloatArray(3); AndroidColor.colorToHSV(parsed, h)
                                hue = h[0]; sat = h[1]; value = h[2]
                                if (allowAlpha) alpha = AndroidColor.alpha(parsed)
                            }
                        },
                        label = { Text("Hex") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = { onConfirm(color) }) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
private fun SatValSquare(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val hueColor = Color(AndroidColor.HSVToColor(255, floatArrayOf(hue, 1f, 1f)))
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                fun emit(o: Offset) {
                    onChange((o.x / size.width).coerceIn(0f, 1f), 1f - (o.y / size.height).coerceIn(0f, 1f))
                }
                detectTapGestures { emit(it) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onChange((change.position.x / size.width).coerceIn(0f, 1f),
                        1f - (change.position.y / size.height).coerceIn(0f, 1f))
                }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            // selector ring
            val cx = sat * size.width
            val cy = (1f - value) * size.height
            drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()))
        }
    }
}

@Composable
private fun HueSlider(hue: Float, onChange: (Float) -> Unit) {
    val hueColors = (0..360 step 30).map { Color(AndroidColor.HSVToColor(255, floatArrayOf(it.toFloat(), 1f, 1f))) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .pointerInput(Unit) {
                detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f) * 360f) }
            }
            .pointerInput(Unit) {
                detectDragGestures { c, _ -> onChange((c.position.x / size.width).coerceIn(0f, 1f) * 360f) }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            drawRect(Brush.horizontalGradient(hueColors))
            val x = hue / 360f * size.width
            drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, size.height / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
        }
    }
}

@Composable
private fun AlphaSlider(alpha: Int, baseColor: Int, onChange: (Int) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .pointerInput(Unit) {
                detectTapGestures { onChange(((it.x / size.width).coerceIn(0f, 1f) * 255).toInt()) }
            }
            .pointerInput(Unit) {
                detectDragGestures { c, _ -> onChange(((c.position.x / size.width).coerceIn(0f, 1f) * 255).toInt()) }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(baseColor))))
            val x = alpha / 255f * size.width
            drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, size.height / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
        }
    }
}

private fun toHex(color: Int, withAlpha: Boolean): String =
    if (withAlpha) "#%08X".format(color) else "#%06X".format(0xFFFFFF and color)

/** Accepts #RGB, #RRGGBB, #AARRGGBB (with or without leading #). */
private fun parseHex(raw: String): Int? {
    val s = raw.trim().removePrefix("#")
    return try {
        when (s.length) {
            6 -> (0xFF000000.toInt()) or s.toInt(16)
            8 -> s.toLong(16).toInt()
            3 -> {
                val r = s[0]; val g = s[1]; val b = s[2]
                ("$r$r$g$g$b$b").toInt(16) or 0xFF000000.toInt()
            }
            else -> null
        }
    } catch (e: NumberFormatException) {
        null
    }
}
