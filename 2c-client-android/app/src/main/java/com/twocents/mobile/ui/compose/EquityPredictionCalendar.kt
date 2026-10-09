package com.twocents.mobile.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.common.validPredictionDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Inline weekday calendar, styled like advanced search but with future-only selection. */
@Composable
internal fun EquityPredictionCalendar(selected: LocalDate?, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    var month by remember(selected) { mutableStateOf(YearMonth.from(selected ?: today.plusDays(1))) }
    val view = LocalView.current
    val offset = month.atDay(1).dayOfWeek.value % 7
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clickable(enabled = month > YearMonth.from(today)) { month = month.minusMonths(1) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ChevronLeft, "Previous month", tint = Color.White.copy(alpha = .5f), modifier = Modifier.size(19.dp))
            }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = Color.White, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Box(Modifier.size(36.dp).clickable { month = month.plusMonths(1) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ChevronRight, "Next month", tint = Color.White.copy(alpha = .5f), modifier = Modifier.size(19.dp))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, color = Color.White.copy(alpha = .3f), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        repeat((offset + month.lengthOfMonth() + 6) / 7) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val number = row * 7 + column - offset + 1
                    val date = number.takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
                    val enabled = date != null && validPredictionDate(date, today)
                    Box(Modifier.weight(1f).height(38.dp), contentAlignment = Alignment.Center) {
                        if (date != null) Box(Modifier.size(34.dp).clip(CircleShape)
                            .background(if (date == selected) Color(0xFFC8A44D) else Color.Transparent)
                            .clickable(enabled = enabled) { AppHaptics.toggle(view); onSelect(date) }, contentAlignment = Alignment.Center) {
                            Text(number.toString(), color = if (date == selected) Color(0xFF17130A) else Color.White.copy(alpha = if (enabled) .85f else .18f),
                                fontSize = 12.sp, fontWeight = if (date == selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}
