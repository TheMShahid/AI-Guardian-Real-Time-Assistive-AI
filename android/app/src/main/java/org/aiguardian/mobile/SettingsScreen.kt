
package org.aiguardian.mobile

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    interfaceLanguage: String,
    speechLanguage: String,
    speechRate: Float,
    voiceAnnouncements: Boolean,
    speechLanguageStatus: String,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onInterfaceLanguageChange: (String) -> Unit,
    onSpeechLanguageChange: (String) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onVoiceAnnouncementsChange: (Boolean) -> Unit,
    distanceCalibration: DistanceCalibration?,
    strideLengthMeters: Float?,
    onSaveDistanceCalibration: (DistanceCalibration, Float?) -> Unit,
    onClearDistanceCalibration: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "PERSONALIZE YOUR EXPERIENCE",
                color = Cyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Settings",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Configure your AI Guardian experience",
                        color = Muted,
                        fontSize = 13.sp
                    )
                }

                TextButton(onClick = onBack) {
                    Text(
                        text = "Back",
                        color = Cyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Appearance
        SettingsSection(
            title = "APPEARANCE",
            subtitle = "Choose how AI Guardian looks"
        )

        SettingsDropdown(
            title = "App theme",
            description = "System Default follows your device appearance",
            selected = themeMode,
            options = listOf(
                "System Default",
                "Light",
                "Dark"
            ),
            onSelected = onThemeModeChange
        )

        // Language preferences
        SettingsSection(
            title = "LANGUAGE PREFERENCES",
            subtitle = "Choose how AI Guardian communicates with you"
        )

        SettingsDropdown(
            title = "Interface language",
            description = "Language used throughout the application",
            selected = interfaceLanguage,
            options = listOf(
                "English",
                "\u0627\u0631\u062F\u0648",
                "Pashto"
            ),
            onSelected = onInterfaceLanguageChange
        )

        SettingsDropdown(
            title = "Speech language",
            description = "Language used for spoken assistance",
            selected = speechLanguage,
            options = listOf(
                "English (US)",
                "Urdu (Pakistan)",
                "Pashto (Afghanistan)"
            ),
            onSelected = onSpeechLanguageChange
        )


        // Voice engine status
        val engineMessage = speechLanguageStatus
        val engineUnavailable = engineMessage.contains("unavailable", ignoreCase = true) ||
            engineMessage.contains("not ready", ignoreCase = true) ||
            engineMessage.contains("failed", ignoreCase = true)

        val fallbackActive = engineMessage.contains("fallback", ignoreCase = true) ||
            engineMessage.contains("missing", ignoreCase = true) ||
            engineMessage.contains("not supported", ignoreCase = true)

        val engineReady = engineMessage.contains("selected successfully", ignoreCase = true)

        val statusLabel = when {
            engineUnavailable -> "UNAVAILABLE"
            fallbackActive -> "FALLBACK ACTIVE"
            engineReady -> "READY"
            else -> "INITIALIZING"
        }

        val statusColor = when {
            engineUnavailable || fallbackActive -> Color(0xFFE0A34A)
            engineReady -> Cyan
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "VOICE ENGINE STATUS",
                        color = Cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = statusColor.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 6.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = statusLabel,
                                color = statusColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.7.sp
                            )
                        }
                    }
                }

                Text(
                    text = engineMessage,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }

        // Voice assistant
        SettingsSection(
            title = "VOICE ASSISTANT",
            subtitle = "Control spoken feedback and speech behaviour"
        )

        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "Voice announcements",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = if (voiceAnnouncements) {
                                "Announcements enabled · Spoken feedback is active"
                            } else {
                                "Announcements muted · Visual detection continues"
                            },
                            color = if (voiceAnnouncements) Cyan else Muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }

                    Switch(
                        checked = voiceAnnouncements,
                        onCheckedChange = onVoiceAnnouncementsChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Cyan,
                            uncheckedThumbColor = Muted,
                            uncheckedTrackColor = PanelLight
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Speech speed",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = String.format(
                                java.util.Locale.US,
                                "%.1fx",
                                speechRate
                            ),
                            color = Cyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = when {
                                speechRate < 0.85f -> "SLOW SPEECH"
                                speechRate > 1.15f -> "FAST SPEECH"
                                else -> "NORMAL SPEECH"
                            },
                            color = Cyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 7.dp
                            )
                        )
                    }

                    Slider(
                        value = speechRate,
                        onValueChange = onSpeechRateChange,
                        valueRange = 0.6f..1.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = Cyan,
                            activeTrackColor = Cyan,
                            inactiveTrackColor = PanelLight
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "0.6× · Slow",
                            color = Muted,
                            fontSize = 11.sp
                        )

                        Text(
                            text = "1.5× · Fast",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Distance estimation calibration
        SettingsSection(
            title = "DISTANCE ESTIMATION",
            subtitle = "Optional camera calibration for approximate distance and step ranges"
        )

        DistanceCalibrationSettings(
            calibration = distanceCalibration,
            strideLengthMeters = strideLengthMeters,
            onSave = onSaveDistanceCalibration,
            onClear = onClearDistanceCalibration
        )

        // OCR information
        SettingsSection(
            title = "TEXT RECOGNITION",
            subtitle = "Future capability"
        )

        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OCR & Text Reading",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "PLANNED",
                            color = Warning,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 5.dp
                            )
                        )
                    }
                }

                Text(
                    text = "Read printed text, signs and documents aloud. OCR support for Urdu and Pashto is planned for a future release.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // Safety notice
        SettingsCard(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SAFETY NOTICE",
                    color = Warning,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "AI Guardian currently provides experimental visual awareness. Do not rely on it for road crossing, obstacle avoidance or collision prevention. Always use established mobility techniques and appropriate human assistance.",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    lineHeight = 19.sp
                )
            }
        }

        Text(
            text = "AI GUARDIAN  •  ACCESSIBILITY THROUGH AI",
            color = Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = title,
            color = Cyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )

        Text(
            text = subtitle,
            color = Muted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SettingsCard(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(19.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsDropdown(
    title: String,
    description: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    val expandedState = remember {
        mutableStateOf(false)
    }

    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = description,
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        expandedState.value = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selected,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = if (expandedState.value) "⌃" else "⌄",
                            color = Cyan,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                DropdownMenu(
                    expanded = expandedState.value,
                    onDismissRequest = {
                        expandedState.value = false
                    }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option,
                                    fontWeight = if (option == selected) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    }
                                )
                            },
                            trailingIcon = {
                                if (option == selected) {
                                    Text(
                                        text = "✓",
                                        color = Cyan,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            onClick = {
                                onSelected(option)
                                expandedState.value = false
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun DistanceCalibrationSettings(
    calibration: DistanceCalibration?,
    strideLengthMeters: Float?,
    onSave: (DistanceCalibration, Float?) -> Unit,
    onClear: () -> Unit
) {
    val referenceLabel = remember(calibration) {
        mutableStateOf(calibration?.referenceLabel ?: "person")
    }
    val knownHeight = remember(calibration) {
        mutableStateOf(calibration?.referenceHeightMeters?.toString() ?: "1.70")
    }
    val measuredDistance = remember(calibration) {
        mutableStateOf(calibration?.referenceDistanceMeters?.toString() ?: "2.00")
    }
    val normalizedBoxHeight = remember(calibration) {
        mutableStateOf(
            calibration?.let {
                (it.focalRatio * it.referenceHeightMeters /
                    it.referenceDistanceMeters).toString()
            } ?: "0.50"
        )
    }
    val stride = remember(strideLengthMeters) {
        mutableStateOf(strideLengthMeters?.toString() ?: "0.70")
    }
    val error = remember { mutableStateOf<String?>(null) }

    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (calibration == null) "NOT CALIBRATED" else "CALIBRATION SAVED",
                color = if (calibration == null) Warning else Cyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "For a first experimental calibration, keep the phone camera at the same zoom and resolution. Ask a helper to stand still at a measured distance. Enter the person's approximate physical height, measured camera-to-person distance, and the detected bounding-box height divided by the full camera-frame height.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            CalibrationInput("Reference object label (person, chair, backpack)", referenceLabel.value) {
                referenceLabel.value = it
            }
            CalibrationInput("Known object height in metres", knownHeight.value) {
                knownHeight.value = it
            }
            CalibrationInput("Measured camera distance in metres", measuredDistance.value) {
                measuredDistance.value = it
            }
            CalibrationInput("Bounding-box height / frame height (0.015–1.0)", normalizedBoxHeight.value) {
                normalizedBoxHeight.value = it
            }
            CalibrationInput("Your average step length in metres (optional)", stride.value) {
                stride.value = it
            }

            if (error.value != null) {
                Text(error.value!!, color = Warning, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    val height = knownHeight.value.toFloatOrNull()
                    val distance = measuredDistance.value.toFloatOrNull()
                    val boxRatio = normalizedBoxHeight.value.toFloatOrNull()
                    val strideValue = stride.value.toFloatOrNull()
                    if (referenceLabel.value.isBlank() ||
                        height == null || distance == null || boxRatio == null ||
                        (stride.value.isNotBlank() && strideValue == null) ||
                        (strideValue != null && strideValue !in 0.20f..1.50f)
                    ) {
                        error.value = "Enter valid positive measurements. Step length must be 0.20–1.50 m."
                        return@Button
                    }
                    val created = VisualDistanceEstimator.calibrate(
                        referenceLabel = referenceLabel.value,
                        normalizedBoxHeight = boxRatio,
                        knownHeightMeters = height,
                        measuredDistanceMeters = distance,
                        nowEpochMs = System.currentTimeMillis()
                    )
                    if (created == null) {
                        error.value = "Calibration values are outside the supported range."
                    } else {
                        onSave(created, strideValue)
                        error.value = null
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save distance calibration")
            }

            if (calibration != null) {
                OutlinedButton(
                    onClick = {
                        onClear()
                        error.value = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear calibration")
                }
            }

            Text(
                text = "Distance is an approximate range based on typical object sizes, not a depth sensor or collision warning. Step counts are approximate and depend on your gait. Recalibrate if camera zoom or resolution changes.",
                color = Warning,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun CalibrationInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}
