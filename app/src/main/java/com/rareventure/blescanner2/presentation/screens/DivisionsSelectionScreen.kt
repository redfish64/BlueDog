package com.rareventure.blescanner2.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.rareventure.blescanner2.R
import com.rareventure.blescanner2.presentation.utils.AppDefaults

const val MIN_DIVISIONS = 10
const val MAX_DIVISIONS = 50


@Composable
fun DivisionsSelectionScreen(
    selectedUsableSlots: Int,
    onSlotsSelected: (Int) -> Unit,
    onBackClick: () -> Unit,
    scrollState: ScalingLazyListState
) {
    // Auto-scroll to the selected division value
    LaunchedEffect(Unit) {
        // Divisions range from 10 to 50
        // Item 0 is the title, items 1-41 are the division values (10, 11, ..., 50)
        val selectedIndex = if (selectedUsableSlots in MIN_DIVISIONS..MAX_DIVISIONS) {
            (selectedUsableSlots - MIN_DIVISIONS) + 1 // +1 for title item
        } else {
            0 // Default to title if out of range
        }
        scrollState.scrollToItem(selectedIndex)
    }

    Scaffold(
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDefaults.DEFAULT_VERTICAL_ITEM_SPACING),
            contentPadding = AppDefaults.DEFAULT_SCREEN_PADDING
        ) {
            item {
                Text(
                    text = stringResource(R.string.select_radar_divisions),
                    style = MaterialTheme.typography.title3
                )
            }

            // Generate slots from 10 to 50
            items(MAX_DIVISIONS - MIN_DIVISIONS + 1) { index ->
                val slotCount = index + MIN_DIVISIONS // 10, 11, 12, ..., 50
                val isSelected = selectedUsableSlots == slotCount

                ToggleChip(
                    checked = isSelected,
                    onCheckedChange = {
                        onSlotsSelected(slotCount)
                        onBackClick()
                    },
                    label = { Text("$slotCount") },
                    modifier = Modifier.fillMaxWidth(),
                    toggleControl = {
                        RadioButton(selected = isSelected)
                    }
                )
            }
        }
    }
}
