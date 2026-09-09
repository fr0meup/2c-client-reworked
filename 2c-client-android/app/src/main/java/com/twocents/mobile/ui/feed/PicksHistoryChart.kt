package com.twocents.mobile.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Cache paths by size/data. Horizontal intent captures inspection until release;
 * vertical intent stays with the feed, even when the touch starts on this graph. */
@Composable
internal fun PicksHistoryChart(history: List<Double>) {
    val prices = remember(history) { history.filter { it.isFinite() } }
    var selected by remember(prices) { mutableIntStateOf(-1) }
    if (prices.size < 2) {
        Box(Modifier.fillMaxWidth().height(88.dp).background(Color.White.copy(alpha = .015f)), contentAlignment = Alignment.Center) {
            Text("Probability history not available", style = PickTextStyle, color = Color.White.copy(alpha = .3f), fontSize = 11.sp)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (selected >= 0) "Past chance of Yes" else "Chance of Yes", style = PickTextStyle, color = Color.White.copy(alpha = .4f), fontSize = 10.sp)
            Text("${prices[if (selected >= 0) selected else prices.lastIndex].roundToInt()}%", style = PickTextStyle, color = PickGold, fontSize = 11.sp)
        }
        Box(Modifier.fillMaxWidth().height(112.dp)
            .semantics { contentDescription = "Probability history. Latest chance of Yes: ${prices.last().roundToInt()} percent. Touch and slide to inspect." }
            .pointerInput(prices) {
                fun select(x: Float) {
                    val inset = 6.dp.toPx()
                    val usable = (size.width - inset * 2).coerceAtLeast(1f)
                    selected = (((x - inset).coerceIn(0f, usable) / usable) * prices.lastIndex).roundToInt()
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var inspecting = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val active = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!inspecting) {
                                if (!active.pressed || active.isConsumed) break
                                val delta = active.position - down.position
                                val x = kotlin.math.abs(delta.x)
                                val y = kotlin.math.abs(delta.y)
                                // Decide once at normal touch slop; no long press needed.
                                if (maxOf(x, y) < viewConfiguration.touchSlop) continue
                                if (y >= x) break
                                inspecting = true
                            }
                            event.changes.forEach { it.consume() }
                            if (!active.pressed) break
                            select(active.position.x)
                        }
                    } finally {
                        selected = -1
                    }
                }
            }
            .drawWithCache {
                val low = prices.min()
                val high = prices.max()
                val range = high - low
                val inset = 6.dp.toPx()
                val width = (size.width - inset * 2).coerceAtLeast(1f)
                val height = (size.height - inset * 2).coerceAtLeast(1f)
                val points = prices.mapIndexed { i, price ->
                    Offset(inset + i.toFloat() / prices.lastIndex * width,
                        inset + (if (range == 0.0) .5f else ((high - price) / range).toFloat()) * height)
                }
                val line = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1..points.lastIndex) {
                        val a = points[i - 1]; val b = points[i]; val mid = (a.x + b.x) / 2
                        cubicTo(mid, a.y, mid, b.y, b.x, b.y)
                    }
                }
                val area = Path().apply { addPath(line); lineTo(points.last().x, size.height); lineTo(points.first().x, size.height); close() }
                val fill = Brush.verticalGradient(listOf(PickGold.copy(alpha = .20f), Color.Transparent))
                onDrawBehind {
                    repeat(3) { index ->
                        val y = inset + height * index / 2
                        drawLine(Color.White.copy(alpha = .045f), Offset(inset, y), Offset(size.width - inset, y), .5.dp.toPx())
                    }
                    drawPath(area, fill)
                    drawPath(line, PickGold, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
                    points.getOrNull(selected)?.let { point ->
                        drawLine(PickGold.copy(alpha = .3f), Offset(point.x, inset), Offset(point.x, size.height), 1.dp.toPx())
                        drawCircle(PickGold, 3.dp.toPx(), point)
                    }
                }
            })
    }
}
