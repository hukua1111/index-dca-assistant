package com.indexdca.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

val ColorGreen = Color(0xFF1B8A5A)
val ColorRed = Color(0xFFD5443C)
val ColorOrange = Color(0xFFD97F2E)
val ColorBlue = Color(0xFF2E6FD5)
val ColorGray = Color(0xFF8A8A8A)

/** 收益率颜色：中式习惯，涨红跌绿 */
fun profitColor(v: Double?): Color = when {
    v == null -> ColorGray
    v >= 0 -> ColorRed
    else -> ColorGreen
}

fun signalColor(signal: Signal): Color = when (signal) {
    Signal.ACCUMULATE -> ColorGreen
    Signal.NORMAL -> ColorBlue
    Signal.CAUTION -> ColorOrange
    Signal.EXIT -> ColorRed
    Signal.UNKNOWN -> ColorGray
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) {
            darkColorScheme(primary = Color(0xFF7BD3A8))
        } else {
            lightColorScheme(primary = ColorGreen)
        },
        content = content
    )
}

@Composable
fun SignalBadge(signal: Signal, modifier: Modifier = Modifier) {
    val color = signalColor(signal)
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            signal.label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** 极简折线图（净值走势） */
@Composable
fun LineChart(points: List<NavPoint>, modifier: Modifier = Modifier, color: Color = ColorBlue) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val ys = points.map { it.y }
        val minY = ys.min()
        val maxY = ys.max()
        val range = (maxY - minY).takeIf { it > 0 } ?: 1.0
        val w = size.width
        val h = size.height
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = w * i / (points.size - 1)
            val y = h * (1f - ((p.y - minY) / range).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
    }
}
