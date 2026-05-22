package com.zegrt.rupee.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onCloseDay: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onPrev) { Text("‹") }
            Text(state.monthLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onNext) { Text("›") }
        }
        Text(state.totalLabel, style = MaterialTheme.typography.bodyMedium)

        // Swipe-month navigation. Wraps the weekday header + grid only —
        // not the title row, not the bottom sheet — so the chevrons and the
        // sheet's own drag-to-dismiss keep working. Threshold of 60dp is
        // ~half a calendar cell on a typical phone; below that we treat
        // the gesture as a tap that missed and reset without changing
        // months. iOS / Google Calendar both feel close to this threshold.
        val density = LocalDensity.current
        val swipeThresholdPx = with(density) { 60.dp.toPx() }
        val dragOffset = remember { mutableFloatStateOf(0f) }

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragOffset.floatValue = 0f },
                    onDragEnd = {
                        val total = dragOffset.floatValue
                        dragOffset.floatValue = 0f
                        when {
                            total > swipeThresholdPx -> onPrev()
                            total < -swipeThresholdPx -> onNext()
                        }
                    },
                    onDragCancel = { dragOffset.floatValue = 0f },
                    onHorizontalDrag = { _, delta -> dragOffset.floatValue += delta },
                )
            },
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                WEEKDAYS.forEach { d ->
                    Text(
                        d,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            state.days.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        DayCell(
                            cell = cell,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp),
                            onClick = { onSelectDate(cell.date) },
                        )
                    }
                }
            }
        }
    }

    if (state.selectedDate != null) {
        DayDetailSheet(state = state, onClose = onCloseDay)
    }
}

@Composable
private fun DayCell(
    cell: CalendarDayCell,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val containerColor = when {
        cell.isSelected -> MaterialTheme.colorScheme.primaryContainer
        cell.isToday -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = if (cell.inMonth) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(
                width = if (cell.isToday) 1.dp else 0.5.dp,
                color = if (cell.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
            )
            cell.totalLabel?.let {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (cell.inMonth) MaterialTheme.colorScheme.primary else contentColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(state: CalendarUiState, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                state.selectedDayLabel.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (state.selectedTransactions.isEmpty()) {
                Text("No transactions on this day.", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.selectedTransactions.forEach { row ->
                    TxnRow(row)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TxnRow(row: CalendarTxnRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.merchant, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (row.isSuggested) {
                    Spacer(modifier = Modifier.height(0.dp))
                    Text(
                        "  • Suggested",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (row.subline.isNotBlank()) {
                Text(row.subline, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(row.amountLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

