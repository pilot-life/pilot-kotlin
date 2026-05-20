package life.pilot.partner.ui.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Search + date-range filter strip rendered above an [EventList].
 *
 * Mirrors the layout pilot-frontend uses in its event search panel:
 * full-width search field on top, two date chips below ("Starts after",
 * "Ends before") that open Material 3 date pickers when tapped.
 *
 * Filter state is hoisted — pass [filters] in, receive updates via
 * [onFiltersChange]. The hosting ViewModel decides what to do with the
 * change (refetch on `startsAfter`, in-memory filter on `query` /
 * `endsBefore`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSearchFilterBar(
    filters: EventListFilters,
    onFiltersChange: (EventListFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(EventSearchFilterBarTestTags.Root),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = filters.query,
            onValueChange = { onFiltersChange(filters.copy(query = it)) },
            placeholder = { Text("Search events…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (filters.query.isNotEmpty()) {
                    IconButton(
                        onClick = { onFiltersChange(filters.copy(query = "")) },
                        modifier = Modifier.testTag(EventSearchFilterBarTestTags.ClearQuery),
                    ) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EventSearchFilterBarTestTags.SearchField),
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DateChip(
                label = filters.startsAfter?.let { "Starts after ${formatChipDate(it)}" }
                    ?: "Starts after…",
                isSet = filters.startsAfter != null,
                onClick = { showStartPicker = true },
                onClear = { onFiltersChange(filters.copy(startsAfter = null)) },
                testTagRoot = EventSearchFilterBarTestTags.StartsAfterChip,
            )
            DateChip(
                label = filters.endsBefore?.let { "Ends before ${formatChipDate(it)}" }
                    ?: "Ends before…",
                isSet = filters.endsBefore != null,
                onClick = { showEndPicker = true },
                onClear = { onFiltersChange(filters.copy(endsBefore = null)) },
                testTagRoot = EventSearchFilterBarTestTags.EndsBeforeChip,
            )
            SortChip(
                current = filters.sortBy,
                onPick = { onFiltersChange(filters.copy(sortBy = it)) },
            )
        }
    }

    if (showStartPicker) {
        DatePickerSheet(
            initial = filters.startsAfter,
            onDismiss = { showStartPicker = false },
            onPick = { picked ->
                onFiltersChange(filters.copy(startsAfter = picked))
                showStartPicker = false
            },
        )
    }
    if (showEndPicker) {
        DatePickerSheet(
            initial = filters.endsBefore,
            onDismiss = { showEndPicker = false },
            onPick = { picked ->
                onFiltersChange(filters.copy(endsBefore = picked))
                showEndPicker = false
            },
        )
    }
}

@Composable
private fun DateChip(
    label: String,
    isSet: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
    testTagRoot: String,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
        trailingIcon = if (isSet) {
            {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.testTag("$testTagRoot.clear"),
                ) {
                    Icon(Icons.Outlined.Clear, contentDescription = "Clear")
                }
            }
        } else null,
        colors = if (isSet) {
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else AssistChipDefaults.assistChipColors(),
        modifier = Modifier.testTag(testTagRoot),
    )
}

@Composable
private fun SortChip(
    current: EventSortBy,
    onPick: (EventSortBy) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text("Sort: ${current.label}") },
            leadingIcon = { Icon(Icons.Outlined.Sort, contentDescription = null) },
            modifier = Modifier.testTag(EventSearchFilterBarTestTags.SortChip),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.testTag(EventSearchFilterBarTestTags.SortMenu),
        ) {
            EventSortBy.entries.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.label) },
                    onClick = {
                        onPick(opt)
                        expanded = false
                    },
                    leadingIcon = if (opt == current) {
                        { Icon(Icons.Outlined.Sort, contentDescription = null) }
                    } else null,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDayIn(TimeZone.currentSystemDefault())?.toEpochMilliseconds(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date
                        onPick(picked)
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}

private fun formatChipDate(d: LocalDate): String {
    val month = when (d.monthNumber) {
        1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"; 5 -> "May"; 6 -> "Jun"
        7 -> "Jul"; 8 -> "Aug"; 9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; 12 -> "Dec"
        else -> d.month.name.take(3)
    }
    return "$month ${d.dayOfMonth}, ${d.year}"
}

object EventSearchFilterBarTestTags {
    const val Root = "EventSearchFilterBar.root"
    const val SearchField = "EventSearchFilterBar.search"
    const val ClearQuery = "EventSearchFilterBar.clear"
    const val StartsAfterChip = "EventSearchFilterBar.startsAfter"
    const val EndsBeforeChip = "EventSearchFilterBar.endsBefore"
    const val SortChip = "EventSearchFilterBar.sort"
    const val SortMenu = "EventSearchFilterBar.sort.menu"
}
