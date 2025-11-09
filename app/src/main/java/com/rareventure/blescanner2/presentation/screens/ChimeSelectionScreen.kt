package com.rareventure.blescanner2.presentation.screens

import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.rareventure.blescanner2.R
import com.rareventure.blescanner2.presentation.utils.AppDefaults

enum class ChimeType { TONE, RINGTONE, SYSTEM_SOUND }

data class ChimeOption(val name: String, val id: Int, val type: ChimeType)

// ID ranges:
// 0-999: ToneGenerator tones (use native tone IDs)
// 1000-1999: Ringtone types (TYPE_NOTIFICATION=1000, TYPE_ALARM=1001, TYPE_RINGTONE=1002)
// 2000+: System sounds (SOUND_EFFECT_CLICK=2000, etc.)

// Store the R.string resource ID along with the system ID
data class ChimeOptionResource(val stringResId: Int, val systemId: Int, val type: ChimeType)

val CHIME_OPTION_RESOURCES = listOf(
    // System sounds
    ChimeOptionResource(R.string.chime_click, 2000 + AudioManager.FX_KEY_CLICK, ChimeType.SYSTEM_SOUND),
    ChimeOptionResource(R.string.chime_focus, 2000 + AudioManager.FX_FOCUS_NAVIGATION_UP, ChimeType.SYSTEM_SOUND),

    // ToneGenerator sounds
    ChimeOptionResource(R.string.chime_call_guard, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_network_lite, ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_beep, ToneGenerator.TONE_PROP_BEEP, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_ack, ToneGenerator.TONE_PROP_ACK, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_nack, ToneGenerator.TONE_PROP_NACK, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_dtmf_0, ToneGenerator.TONE_DTMF_0, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_dtmf_1, ToneGenerator.TONE_DTMF_1, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_dtmf_2, ToneGenerator.TONE_DTMF_2, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_dtmf_3, ToneGenerator.TONE_DTMF_3, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_dtmf_4, ToneGenerator.TONE_DTMF_4, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_dtmf_5, ToneGenerator.TONE_DTMF_5, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_dtmf_6, ToneGenerator.TONE_DTMF_6, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_dtmf_7, ToneGenerator.TONE_DTMF_7, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_dtmf_8, ToneGenerator.TONE_DTMF_8, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_dtmf_9, ToneGenerator.TONE_DTMF_9, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_dtmf_star, ToneGenerator.TONE_DTMF_S, ChimeType.TONE),
    ChimeOptionResource(R.string.chime_dtmf_pound, ToneGenerator.TONE_DTMF_P, ChimeType.TONE)
)

// Keep CHIME_OPTIONS for backward compatibility (deprecated)
@Deprecated("Use CHIME_OPTION_RESOURCES instead")
val CHIME_OPTIONS = listOf(
    ChimeOption("Click", 2000 + AudioManager.FX_KEY_CLICK, ChimeType.SYSTEM_SOUND),
    ChimeOption("Focus", 2000 + AudioManager.FX_FOCUS_NAVIGATION_UP, ChimeType.SYSTEM_SOUND),
    ChimeOption("Call Guard", ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, ChimeType.TONE),
    ChimeOption("Network Lite", ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, ChimeType.TONE),
    ChimeOption("Beep", ToneGenerator.TONE_PROP_BEEP, ChimeType.TONE),
    ChimeOption("Ack", ToneGenerator.TONE_PROP_ACK, ChimeType.TONE),
    ChimeOption("Nack", ToneGenerator.TONE_PROP_NACK, ChimeType.TONE),
    ChimeOption("DTMF 0", ToneGenerator.TONE_DTMF_0, ChimeType.TONE),
    ChimeOption("DTMF 1", ToneGenerator.TONE_DTMF_1, ChimeType.TONE),
    ChimeOption("DTMF 2", ToneGenerator.TONE_DTMF_2, ChimeType.TONE),
    ChimeOption("DTMF 3", ToneGenerator.TONE_DTMF_3, ChimeType.TONE),
    ChimeOption("DTMF 4", ToneGenerator.TONE_DTMF_4, ChimeType.TONE),
    ChimeOption("DTMF 5", ToneGenerator.TONE_DTMF_5, ChimeType.TONE),
    ChimeOption("DTMF 6", ToneGenerator.TONE_DTMF_6, ChimeType.TONE),
    ChimeOption("DTMF 7", ToneGenerator.TONE_DTMF_7, ChimeType.TONE),
    ChimeOption("DTMF 8", ToneGenerator.TONE_DTMF_8, ChimeType.TONE),
    ChimeOption("DTMF 9", ToneGenerator.TONE_DTMF_9, ChimeType.TONE),
    ChimeOption("DTMF Star", ToneGenerator.TONE_DTMF_S, ChimeType.TONE),
    ChimeOption("DTMF Pound", ToneGenerator.TONE_DTMF_P, ChimeType.TONE)
)

@Composable
fun ChimeSelectionScreen(
    selectedTone: Int,
    onToneSelected: (Int) -> Unit,
    onBackClick: () -> Unit,
    onPlayTone: (Int) -> Unit,
    scrollState: ScalingLazyListState,
    isChimeEnabled: Boolean,
    onChimeEnabledChange: (Boolean) -> Unit
) {
    val title = stringResource(R.string.chime)

    // Auto-scroll to the selected item
    LaunchedEffect(Unit) {
        val selectedIndex = when {
            selectedTone == AppDefaults.CHIME_OFF_ID -> 1 // Off option
            else -> {
                // Find the selected tone in CHIME_OPTION_RESOURCES
                val optionIndex = CHIME_OPTION_RESOURCES.indexOfFirst { it.systemId == selectedTone }
                if (optionIndex >= 0) optionIndex + 2 else 0 // +2 for title and Off items
            }
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
                    text = stringResource(R.string.select_chime_format, title),
                    style = MaterialTheme.typography.title3
                )
            }

            // Off option first
            item {
                val isOffSelected = selectedTone == AppDefaults.CHIME_OFF_ID

                ToggleChip(
                    checked = isOffSelected,
                    onCheckedChange = {
                        onToneSelected(AppDefaults.CHIME_OFF_ID)
                    },
                    label = { Text(stringResource(R.string.off)) },
                    modifier = Modifier.fillMaxWidth(),
                    toggleControl = {
                        RadioButton(selected = isOffSelected)
                    }
                )
            }

            items(CHIME_OPTION_RESOURCES.size) { index ->
                val option = CHIME_OPTION_RESOURCES[index]
                val isSelected = selectedTone == option.systemId

                ToggleChip(
                    checked = isSelected,
                    onCheckedChange = {
                        onToneSelected(option.systemId)
                        onPlayTone(option.systemId)
                    },
                    label = { Text(stringResource(option.stringResId)) },
                    modifier = Modifier.fillMaxWidth(),
                    toggleControl = {
                        RadioButton(selected = isSelected)
                    }
                )
            }
        }
    }
}
