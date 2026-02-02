package com.example.myapplication.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InfiniteScrollingCalendar(
    initialDate: LocalDate,
    selectedDate: LocalDate,
    workoutDates: List<LocalDate>,
    onDateSelected: (LocalDate) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var currentMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var dateList by remember { mutableStateOf(generateDateList(currentMonth)) }

    // Scroll to the initial date when the composable is first launched
    LaunchedEffect(Unit) {
        val initialIndex = dateList.indexOf(initialDate)
        if (initialIndex != -1) {
            listState.scrollToItem(initialIndex)
        }
    }

    LaunchedEffect(currentMonth) {
        dateList = generateDateList(currentMonth)
    }

    val currentMonthLabel by remember {
        derivedStateOf {
            currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).lowercase()
                .replaceFirstChar { it.titlecase() } + " " + currentMonth.year
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header with month and navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                coroutineScope.launch {
                    currentMonth = currentMonth.minusMonths(1)
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
            }
            Text(text = currentMonthLabel, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = {
                coroutineScope.launch {
                    currentMonth = currentMonth.plusMonths(1)
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Calendar days
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            itemsIndexed(dateList) { index, date ->
                val isSelected = date == selectedDate
                val isWorkoutDay = remember(workoutDates) { date in workoutDates }
                CalendarDay(
                    date = date,
                    isSelected = isSelected,
                    isWorkoutDay = isWorkoutDay,
                    onDateSelected = onDateSelected
                )
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    isSelected: Boolean,
    isWorkoutDay: Boolean,
    onDateSelected: (LocalDate) -> Unit
) {
    val isToday = date == LocalDate.now()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(4.dp)
            .clickable { onDateSelected(date) }
    ) {
        Text(
            text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.secondaryContainer
                        else -> Color.Transparent
                    },
                    shape = CircleShape
                )
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
            )
        }
        Spacer(modifier = Modifier.height(4.dp))

        if (isWorkoutDay && !isSelected) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
            )
        } else {
            Spacer(modifier = Modifier.size(6.dp))
        }
    }
}

private fun generateDateList(month: YearMonth): List<LocalDate> {
    val firstDay = month.atDay(1)
    val lastDay = month.atEndOfMonth()
    val dates = mutableListOf<LocalDate>()
    var currentDate = firstDay
    while (currentDate <= lastDay) {
        dates.add(currentDate)
        currentDate = currentDate.plusDays(1)
    }
    return dates
}
