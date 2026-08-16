package com.hinnka.mycamera.ui.camera

import android.hardware.camera2.CameraMetadata
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinnka.mycamera.camera.CameraState
import com.hinnka.mycamera.camera.CameraUtils
import com.hinnka.mycamera.ui.components.PhysicalButton

internal val CameraParameterValuesOverlayHeight = 24.dp

private val CameraParameterItemMaxWidth = 56.dp
private val CameraParameterItemMinGap = 4.dp

@Composable
fun CameraParameterBar(
    state: CameraState,
    selectedParameter: CameraParameter?,
    onParameterClick: (CameraParameter) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // 自适应宽度：6 个按钮保证最小间距，窄屏自动收窄，宽屏不超过 56dp
        val itemWidth = minOf(
            CameraParameterItemMaxWidth,
            (maxWidth - CameraParameterItemMinGap * 5) / 6
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ParameterItem(
                label = "AE",
                isSelected = selectedParameter == CameraParameter.EXPOSURE_COMPENSATION,
                isEnabled = state.isAutoExposure, // Only available in auto exposure mode
                onClick = { onParameterClick(CameraParameter.EXPOSURE_COMPENSATION) },
                modifier = Modifier.width(itemWidth)
            )
            ParameterItem(
                label = "Tv",
                isSelected = selectedParameter == CameraParameter.SHUTTER_SPEED,
                isEnabled = true,
                onClick = { onParameterClick(CameraParameter.SHUTTER_SPEED) },
                modifier = Modifier.width(itemWidth)
            )
            ParameterItem(
                label = "F",
                isSelected = selectedParameter == CameraParameter.VARIABLE_APERTURE,
                isEnabled = state.supportsVariableAperture && !state.videoRecordingState.isRecording,
                onClick = { onParameterClick(CameraParameter.VARIABLE_APERTURE) },
                modifier = Modifier.width(itemWidth)
            )
            ParameterItem(
                label = "ISO",
                isSelected = selectedParameter == CameraParameter.ISO,
                isEnabled = true,
                onClick = { onParameterClick(CameraParameter.ISO) },
                modifier = Modifier.width(itemWidth)
            )
            ParameterItem(
                label = "AF",
                isSelected = selectedParameter == CameraParameter.FOCUS,
                isEnabled = true,
                onClick = { onParameterClick(CameraParameter.FOCUS) },
                modifier = Modifier.width(itemWidth)
            )
            ParameterItem(
                label = "AWB",
                isSelected = selectedParameter == CameraParameter.WHITE_BALANCE,
                isEnabled = state.canAdjustWhiteBalance || state.actualAwbTemperature != null,
                onClick = { onParameterClick(CameraParameter.WHITE_BALANCE) },
                modifier = Modifier.width(itemWidth)
            )
        }
    }
}

@Composable
fun ParameterItem(
    label: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    vertical: Boolean = false,
    modifier: Modifier = Modifier
) {
    PhysicalButton(
        modifier = Modifier
            .width(if (vertical) 32.dp else 56.dp)
            .then(modifier)
            .height(if (vertical) 56.dp else 32.dp),
        onClick = onClick,
        enabled = isEnabled,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                modifier = if (vertical) Modifier.rotate(90f) else Modifier,
                color = if (isSelected) {
                    Color(0xFFFFD700)
                } else {
                    Color.White.copy(alpha = if (isEnabled) 1f else 0.5f)
                },
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun CameraParameterValuesOverlay(
    state: CameraState,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(CameraParameterValuesOverlayHeight)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        val showLabels = maxWidth >= 340.dp
        val valueFontSize = if (showLabels) 10.sp else 9.sp
        val values = listOf(
            CameraParameterValue(
                label = "AE",
                value = CameraUtils.formatExposureCompensation(
                    state.exposureCompensation,
                    state.getExposureCompensationStep()
                )
            ),
            CameraParameterValue(
                label = "Tv",
                value = formatShutterSpeedValue(state.shutterSpeed),
                valueColor = if (state.isPreviewExposureLimited()) Color.Red else Color.White
            ),
            CameraParameterValue(
                label = "F",
                value = formatVariableApertureValue(state)
            ),
            CameraParameterValue(label = "ISO", value = state.iso.toString()),
            CameraParameterValue(label = "AF", value = formatFocusDistance(state.focusDistance)),
            CameraParameterValue(label = "AWB", value = formatWhiteBalanceValue(state))
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            values.forEach { item ->
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showLabels) {
                        Text(
                            text = item.label,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = ViewfinderTextShadow
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(
                        text = item.value,
                        color = item.valueColor,
                        fontSize = valueFontSize,
                        fontWeight = FontWeight.Medium,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = ViewfinderTextShadow
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

private data class CameraParameterValue(
    val label: String,
    val value: String,
    val valueColor: Color = Color.White
)

internal fun formatShutterSpeedValue(value: Long): String {
    return if (value <= 0L) "0" else CameraUtils.formatShutterSpeed(value)
}

internal fun formatFocusDistance(value: Float): String {
    return if (value <= 0.01f) "∞"
    else {
        val meters = 1.0f / value
        if (meters >= 1.0f) String.format("%.1fm", meters)
        else String.format("%dcm", (meters * 100).toInt())
    }
}

internal fun formatWhiteBalanceValue(state: CameraState): String {
    return when (state.awbMode) {
        CameraMetadata.CONTROL_AWB_MODE_OFF -> "${state.awbTemperature}K"
        CameraMetadata.CONTROL_AWB_MODE_AUTO -> state.actualAwbTemperature?.let { "${it}K" } ?: "AUTO"
        else -> state.actualAwbTemperature?.let { "${it}K" } ?: "UNK"
    }
}

internal fun formatVariableApertureValue(state: CameraState): String {
    return if (state.isVariableApertureAuto) {
        state.physicalAperture
            .takeIf { it > 0f }
            ?.let { formatApertureFNumber(it) }
            ?: "AUTO"
    } else {
        formatApertureFNumber(state.variableApertureFNumber)
    }
}
