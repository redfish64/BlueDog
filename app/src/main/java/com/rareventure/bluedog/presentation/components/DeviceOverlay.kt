package com.rareventure.bluedog.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.rareventure.bluedog.R
import com.rareventure.bluedog.presentation.data.ScannedDevice
import com.rareventure.bluedog.presentation.utils.AppDefaults
import kotlin.math.abs

@Composable
fun DeviceOverlay(
    device: ScannedDevice,
    onBlock: () -> Unit,
    onDismiss: () -> Unit,
    currentTime: Long,
    isScanning: Boolean = false // Whether scanning is currently active
) {
    val hue = (abs(device.address.hashCode()) % 360).toFloat()
    val deviceColor = Color.hsv(hue, 1f, 1f)

    val elapsedMs = currentTime - device.lastSeen
    val elapsedSeconds = elapsedMs / 1000.0
    val minutes = (elapsedSeconds / 60).toInt()
    val seconds = (elapsedSeconds % 60).toInt()
    val tenths = ((elapsedSeconds % 1) * 10).toInt()
    val timeAgo = String.format("%02d:%02d.%d", minutes, seconds, tenths)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                onClick = onDismiss,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .background(
                    color = MaterialTheme.colors.surface,
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 4.dp,
                    color = deviceColor,
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 0.dp, end = 6.dp, top = 12.dp, bottom = 70.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = device.deviceName ?: stringResource(R.string.unknown),
                    style = MaterialTheme.typography.title3,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = device.address,
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                Text(
                    text = stringResource(R.string.rssi_format, device.rssi),
                    style = MaterialTheme.typography.caption1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                if (isScanning) {
                    Text(
                        text = stringResource(R.string.seen_ago_format, timeAgo),
                        style = MaterialTheme.typography.caption1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colors.surface.copy(alpha = 0.95f)
                    )
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = onBlock,
                        modifier = Modifier.size(ButtonDefaults.DefaultButtonSize),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)
                    ) {
                        Text(
                            text = AppDefaults.BLOCK_BUTTON_EMOJI,
                            fontSize = 24.sp
                        )
                    }
                }
            }
        }
    }
}
