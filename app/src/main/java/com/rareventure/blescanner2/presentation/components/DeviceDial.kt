package com.rareventure.blescanner2.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.rareventure.blescanner2.presentation.data.ScannedDevice
import com.rareventure.blescanner2.presentation.utils.mapRssiToValue
import com.rareventure.blescanner2.presentation.utils.AppDefaults
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val FULL_ROTATION_DEGREES = 360f
private const val NORTH_ANGLE_DEGREES = -90f
private const val SLICE_GAP_RATIO = 0.92f
private const val INNER_STROKE_RATIO = 0.45f
private const val DIAL_RADIUS_DENOMINATOR = 2.2f
private val OUTER_STROKE_WIDTH = 8.dp
private val INNER_OUTER_GAP = 2.dp

@Composable
fun DeviceDial(
    devices: Map<String, ScannedDevice>,
    slotMap: Map<String, Int>,
    numDivisions: Int,
    onSliceTapped: (String) -> Unit,
    modifier: Modifier = Modifier,
    excludedSlots: Set<Int> = emptySet(),
    isScanning: Boolean = false, // Whether scanning is currently active
    currentTime: Long = System.currentTimeMillis() // Current time for brightness fade calculation
) {
    Canvas(
        modifier = modifier
            .aspectRatio(1.0f)
            .pointerInput(slotMap, numDivisions, excludedSlots) { // Keyed to slotMap, numDivisions, and excludedSlots
                detectTapGestures { offset ->
                    val slotToAddressMap = slotMap.entries.associateBy({ it.value }) { it.key }
                    val canvasSize = min(size.width, size.height)
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val radius = canvasSize / DIAL_RADIUS_DENOMINATOR

                    // Calculate rotation offset to match drawing rotation
                    val numExcluded = excludedSlots.size
                    val rotationOffset = if ((numDivisions + numExcluded) % 2 == 1) {
                        -(FULL_ROTATION_DEGREES / numDivisions) / 2f
                    } else {
                        0f
                    }

                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val distance = sqrt(dx * dx + dy * dy)

                    if (distance <= radius) {
                        var angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 90.0).toFloat()
                        if (angle < 0f) angle += FULL_ROTATION_DEGREES
                        // Adjust angle to account for rotation
                        angle = (angle - rotationOffset + FULL_ROTATION_DEGREES) % FULL_ROTATION_DEGREES
                        val sliceIndex = (angle / (FULL_ROTATION_DEGREES / numDivisions)).toInt()
                        slotToAddressMap[sliceIndex]?.let { address ->
                            onSliceTapped(address)
                        }
                    }
                }
            }
    ) {
        val slotToAddressMap = slotMap.entries.associateBy({ it.value }) { it.key }
        val center = Offset(size.width / 2, size.height / 2)
        val radius = min(size.width, size.height) / DIAL_RADIUS_DENOMINATOR
        val sweepAngle = FULL_ROTATION_DEGREES / numDivisions * SLICE_GAP_RATIO // Gap between slices
        val outerStrokeWidth = OUTER_STROKE_WIDTH.toPx()
        val innerStrokeWidth = radius * INNER_STROKE_RATIO
        val innerOuterGap = INNER_OUTER_GAP.toPx()
        val innerArcCenterRadius = radius - (outerStrokeWidth / 2f) - (innerStrokeWidth / 2f) - innerOuterGap

        // Calculate rotation offset: rotate when (actualDivisions + excludedSlots) is odd
        // This handles the XOR situation:
        // - Odd actual + Odd excluded = Even sum → no rotation (slot naturally centered)
        // - Even actual + Even excluded = Even sum → no rotation (slots straddle center)
        // - Odd actual + Even excluded = Odd sum → rotate (need to align excluded slots)
        // - Even actual + Odd excluded = Odd sum → rotate (need to center single excluded slot)
        val numExcluded = excludedSlots.size
        val rotationOffset =
            if (numDivisions % 2 == 0 && numExcluded % 2 == 1) {
                -(FULL_ROTATION_DEGREES / numDivisions) / 2f
            } else if (numDivisions % 2 == 1 && numExcluded % 2 == 0) {
                (FULL_ROTATION_DEGREES / numDivisions) / 2f
            } else {
                0f
            }

        // Full color visualization
        fun gradientFor(hue: Float, brightness: Float): Brush {
            val innerOuterRadius = innerArcCenterRadius + (innerStrokeWidth / 2f)
            val innerColor = Color.hsv(hue, 0.6f, brightness.coerceIn(0.3f, 1f))
            return Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to Color.Black,
                    0.5f to Color.Black.copy(alpha = 0.7f),
                    1.0f to innerColor
                ),
                center = center,
                radius = innerOuterRadius
            )
        }

        fun drawDeviceSlice(
            startAngle: Float,
            sweepAngle: Float,
            outerColor: Color,
            gradient: Brush
        ) {
            drawArc(
                color = outerColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = outerStrokeWidth),
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius)
            )
            drawArc(
                brush = gradient,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = innerStrokeWidth),
                size = Size(innerArcCenterRadius * 2, innerArcCenterRadius * 2),
                topLeft = Offset(center.x - innerArcCenterRadius, center.y - innerArcCenterRadius)
            )
        }

        for (i in 0 until numDivisions) {
            val startAngle = NORTH_ANGLE_DEGREES + i * (FULL_ROTATION_DEGREES / numDivisions) + rotationOffset
            val address = slotToAddressMap[i]
            val device = devices[address]

            if (device != null) {
                val baseBrightness = mapRssiToValue(device.rssi)
                val brightness = if (isScanning) baseBrightness else (baseBrightness * 0.85f)

                // Calculate outer ring brightness based on time since last seen
                // Start at maximum (1.0) and fade to minimum over DEVICE_STALE_TIMEOUT_MS
                val timeSinceLastSeen = (currentTime - device.lastSeen).coerceAtLeast(0L)
                val fadeProgress = (timeSinceLastSeen.toFloat() / AppDefaults.DEVICE_STALE_TIMEOUT_MS).coerceIn(0f, 1f)
                val outerBrightness = 1.0f - (fadeProgress * 0.7f) // Fade from 1.0 to 0.3

                val hue = (abs(device.address.hashCode()) % 360).toFloat()
                val outerColor = Color.hsv(hue, 1f, outerBrightness)

                val gradient = gradientFor(hue, brightness)
                drawDeviceSlice(startAngle, sweepAngle, outerColor, gradient)

            } else if (i !in excludedSlots) {
                drawArc(
                    color = Color.DarkGray.copy(alpha = 0.5f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = outerStrokeWidth),
                    size = Size(radius * 2, radius * 2),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )
            }
        }
    }
}

@Composable
fun BatteryIndicator(
    batteryLevel: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val canvasRadius = min(size.width, size.height) / 2f

        // Battery indicator around the center button area
        val batteryStartAngle = AppDefaults.BATTERY_START_ANGLE_DEGREES // Start at 12 o'clock
        val batteryMaxSweepAngle = AppDefaults.BATTERY_MAX_SWEEP_DEGREES // Full circle when battery is 100%
        val batteryStrokeWidth = AppDefaults.BATTERY_INDICATOR_STROKE_WIDTH.toPx() // Made thicker for visibility
        // Position outside the center button - larger radius to avoid overlap
        val batteryRadius = canvasRadius * AppDefaults.BATTERY_INDICATOR_RADIUS_FRACTION // Positioned between button and device arcs

        // Draw multi-color gradient battery indicator with moderate brightness
        val numSegments = AppDefaults.BATTERY_SEGMENTS // One degree per segment for smooth gradient
        val degreesPerSegment = batteryMaxSweepAngle / numSegments

        for (i in 0 until numSegments) {
            val segmentAngle = batteryStartAngle + (i * degreesPerSegment)
            val segmentProgress = i.toFloat() / numSegments

            // Only draw segments that are within the battery level
            if (segmentProgress <= batteryLevel) {
                // Create a gradient effect with moderate visibility
                // Use HSV color space with moderate saturation and brightness
                val hue = (segmentProgress * 120f) // 0° (red) to 120° (green)
                val segmentColor = Color.hsv(hue, 0.7f, 0.6f) // Increased brightness for visibility

                drawArc(
                    color = segmentColor,
                    startAngle = segmentAngle,
                    sweepAngle = degreesPerSegment,
                    useCenter = false,
                    style = Stroke(width = batteryStrokeWidth),
                    size = Size(batteryRadius * 2, batteryRadius * 2),
                    topLeft = Offset(center.x - batteryRadius, center.y - batteryRadius)
                )
            }
        }
    }
}
