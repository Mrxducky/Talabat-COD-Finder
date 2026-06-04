package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.CODMachine
import com.example.model.LocationPreset
import com.example.viewmodel.RiderState
import com.example.viewmodel.RiderViewModel
import com.example.viewmodel.WarningLevel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: RiderViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var selectedMachineForNavigation by remember { mutableStateOf<com.example.model.CODMachine?>(null) }

    // Request permissions launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            Toast.makeText(context, "Location permission granted. Syncing GPS...", Toast.LENGTH_SHORT).show()
            requestGpsLocation(context, viewModel)
        } else {
            Toast.makeText(context, "Camera/Location access denied. Using high-fidelity simulator.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsBike,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "COD RIDER QATAR",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF0F1728)
                ),
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .background(Color(0xFF1E293B), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF10B981), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ONLINE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                },
                modifier = Modifier.shadow(4.dp)
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header Info & Quick Status Summary
            BalanceAlertCard(state, viewModel)

            Spacer(modifier = Modifier.height(20.dp))

            // Limit configuration & Inputs
            RiderModeSelector(state, viewModel, haptic)

            Spacer(modifier = Modifier.height(20.dp))

            CODInputSection(state, viewModel, haptic)

            Spacer(modifier = Modifier.height(20.dp))

            // High Tech Map Dashboard Card
            RadarMapDashboard(state, viewModel, locationPermissionLauncher, haptic)

            Spacer(modifier = Modifier.height(20.dp))

            // Nearest Machine Focus
            NearestMachineCard(state) { machine ->
                selectedMachineForNavigation = machine
            }

            Spacer(modifier = Modifier.height(20.dp))

            // All Machines Directory List
            DropMachinesList(state) { machine ->
                selectedMachineForNavigation = machine
            }
        }
    }

    if (selectedMachineForNavigation != null) {
        val machine = selectedMachineForNavigation!!
        AlertDialog(
            onDismissRequest = { selectedMachineForNavigation = null },
            title = {
                Column {
                    Text(
                        text = "High-Precision Turn Navigation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${machine.name} (${machine.branch})",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Choose your preferred routing application below to begin step-by-step turn guidance from your current location directly to this drop spot.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 1. Waze Button
                    Button(
                        onClick = {
                            launchWaze(context, machine.latitude, machine.longitude, machine.mapUrl)
                            selectedMachineForNavigation = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF33D1FF), // Waze blue
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "Waze",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Navigate with Waze", fontWeight = FontWeight.Bold)
                        }
                    }

                    // 2. Google Maps Button
                    Button(
                        onClick = {
                            launchGoogleMaps(context, machine.latitude, machine.longitude, machine.mapUrl)
                            selectedMachineForNavigation = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444), // Crimson primary accent
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Google Maps",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Navigate with Google Maps", fontWeight = FontWeight.Bold)
                        }
                    }

                    // 3. Original Merchant Link Button
                    OutlinedButton(
                        onClick = {
                            launchMerchantUrl(context, machine.mapUrl)
                            selectedMachineForNavigation = null
                        },
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.LightGray
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Original Merchant Link",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Original Merchant Link", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedMachineForNavigation = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun BalanceAlertCard(state: RiderState, viewModel: RiderViewModel) {
    val limit = viewModel.getLimitForType(state.riderType)
    val warning = viewModel.getWarningLevel(state.currentCOD, state.riderType)
    val progress = (state.currentCOD / limit).coerceIn(0.0, 1.0).toFloat()

    val cardColor by animateColorAsState(
        targetValue = Color(warning.colorHex).copy(alpha = 0.15f),
        animationSpec = twinPulseSpec()
    )

    val progressColor by animateColorAsState(
        targetValue = Color(warning.colorHex),
        animationSpec = tween(durationMillis = 300)
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.5.dp, progressColor.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .testTag("balance_alert_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            cardColor
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Cash-on-Hand",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = when (warning) {
                        WarningLevel.SAFE -> Icons.Default.CheckCircle
                        WarningLevel.WARNING -> Icons.Default.Warning
                        WarningLevel.CRITICAL -> Icons.Default.ReportProblem
                    },
                    contentDescription = null,
                    tint = progressColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "QAR ${state.currentCOD.roundToInt()}",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "/ QAR ${limit.roundToInt()}",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = progressColor,
                trackColor = Color(0xFF334155)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = progressColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, progressColor.copy(alpha = 0.4f)),
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    text = warning.message,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = progressColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun RiderModeSelector(state: RiderState, viewModel: RiderViewModel, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Bike Rider Selector
        val isBike = state.riderType == "Bike"
        val bikeBg = if (isBike) Color(0xFFEF4444) else Color(0xFF1E293B)
        val bikeBorder = if (isBike) Color(0xFFF87171) else Color(0xFF334155)
        val bikeContentColor = if (isBike) Color.White else Color.LightGray

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.updateRiderType("Bike")
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = bikeBg, contentColor = bikeContentColor),
            border = BorderStroke(1.5.dp, bikeBorder),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .testTag("bike_rider_selector")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.TwoWheeler,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Bike Rider",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Limit: 900 QAR",
                        fontSize = 10.sp,
                        color = bikeContentColor.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Car Rider Selector
        val isCar = state.riderType == "Car"
        val carBg = if (isCar) Color(0xFFEF4444) else Color(0xFF1E293B)
        val carBorder = if (isCar) Color(0xFFF87171) else Color(0xFF334155)
        val carContentColor = if (isCar) Color.White else Color.LightGray

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.updateRiderType("Car")
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = carBg, contentColor = carContentColor),
            border = BorderStroke(1.5.dp, carBorder),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .testTag("car_rider_selector")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Car Rider",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Limit: 2300 QAR",
                        fontSize = 10.sp,
                        color = carContentColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun CODInputSection(state: RiderState, viewModel: RiderViewModel, haptic: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    var textValue by remember(state.currentCOD) {
        mutableStateOf(if (state.currentCOD == 0.0) "" else state.currentCOD.roundToInt().toString())
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Update Cash Status",
                color = Color.LightGray,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    // Permit only numbers
                    val filtered = newValue.filter { it.isDigit() }
                    textValue = filtered
                    val doubleVal = filtered.toDoubleOrNull() ?: 0.0
                    viewModel.updateCOD(doubleVal)
                },
                placeholder = {
                    Text("Enter total balance amount in QAR", color = Color.Gray, fontSize = 14.sp)
                },
                trailingIcon = {
                    if (textValue.isNotEmpty()) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.clearCOD()
                            textValue = ""
                        }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = Color.LightGray)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                maxLines = 1,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A),
                    focusedBorderColor = Color(0xFFEF4444),
                    unfocusedBorderColor = Color(0xFF334155)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cod_input_field")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Gloves Friendly Quick Add row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(50, 100, 500).forEach { increment ->
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.incrementCOD(increment.toDouble())
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF334155),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_${increment}_btn")
                    ) {
                        Text(
                            text = "+$increment QAR",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.clearCOD()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9494).copy(alpha = 0.15f),
                        contentColor = Color(0xFFEF4444)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("clear_cod_btn")
                ) {
                    Text(
                        text = "Reset",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun RadarMapDashboard(
    state: RiderState,
    viewModel: RiderViewModel,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Rider Locator Map",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "High-precision regional tactical radar",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                Row {
                    // Actual GPS request button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val fineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                            val coarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (fineLoc == PackageManager.PERMISSION_GRANTED || coarseLoc == PackageManager.PERMISSION_GRANTED) {
                                requestGpsLocation(context, viewModel)
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .background(
                                if (state.isRealGpsActive) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF334155),
                                CircleShape
                            )
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Sync Real GPS",
                            tint = if (state.isRealGpsActive) Color(0xFF10B981) else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isExpanded = !isExpanded
                        },
                        modifier = Modifier
                            .background(Color(0xFF334155), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.Map,
                            contentDescription = "Toggle Presets",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Map Frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
                    .testTag("radar_map_canvas")
            ) {
                InteractiveQatarRadarCanvas(state, viewModel)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Preset locations - always expandable to guarantee manual navigation inside emulator
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Text(
                        text = "Simulate Rider Landmark Coordinates",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.locationPresets.forEach { preset ->
                            val isSelected = !state.isRealGpsActive &&
                                    Math.abs(state.simulatedLatitude - preset.latitude) < 0.001 &&
                                    Math.abs(state.simulatedLongitude - preset.longitude) < 0.001

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.updateLocation(preset.latitude, preset.longitude, false)
                                },
                                label = {
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text(preset.label, fontWeight = FontWeight.Bold)
                                        Text(preset.description, fontSize = 9.sp, color = Color.Gray)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFEF4444),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = Color.LightGray
                                ),
                                modifier = Modifier.testTag("preset_chip_${preset.label.take(5)}")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Coordinates Display Meter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.isRealGpsActive) "🟢 ACTIVE SATELLITE LOCK" else "📊 HIGH FIDELITY SIMULATED MODE",
                    fontSize = 10.sp,
                    color = if (state.isRealGpsActive) Color(0xFF10B981) else Color(0xFFF59E0B)
                )

                Text(
                    text = String.format("%.4f° N, %.4f° E", state.simulatedLatitude, state.simulatedLongitude),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun InteractiveQatarRadarCanvas(state: RiderState, viewModel: RiderViewModel) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val scanAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanAngle"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
    ) {
        // Map Projection bounds
        val minLat = 25.10
        val maxLat = 25.48
        val minLng = 51.35
        val maxLng = 51.68

        val mapWidth = size.width
        val mapHeight = size.height

        // Helper mapper lambda
        fun getOffset(latitude: Double, longitude: Double): Offset {
            val yRatio = (maxLat - latitude) / (maxLat - minLat).toFloat()
            val xRatio = (longitude - minLng) / (maxLng - minLng).toFloat()
            return Offset(
                x = (xRatio * mapWidth).toFloat(),
                y = (yRatio * mapHeight).toFloat()
            )
        }

        // 1. Draw Grid lines and circles (concentric radar ranges around Center Doha)
        val centerDoha = getOffset(25.2854, 51.5310)

        // Draw tactical radar range lines
        for (dist in listOf(20f, 60f, 110f, 170f, 240f)) {
            drawCircle(
                color = Color(0xFF1E293B).copy(alpha = 0.4f),
                radius = dist * 2f,
                center = centerDoha,
                style = Stroke(width = 1f)
            )
        }

        // Simple coordinate grid text/lines
        for (x_line in (0..4)) {
            val x_pos = (x_line / 4f) * mapWidth
            drawLine(
                color = Color(0xFF1E293B).copy(alpha = 0.15f),
                start = Offset(x_pos, 0f),
                end = Offset(x_pos, mapHeight),
                strokeWidth = 1f
            )
        }
        for (y_line in (0..4)) {
            val y_pos = (y_line / 4f) * mapHeight
            drawLine(
                color = Color(0xFF1E293B).copy(alpha = 0.15f),
                start = Offset(0f, y_pos),
                end = Offset(mapWidth, y_pos),
                strokeWidth = 1f
            )
        }

        // 2. Draw Costa of Qatar (Stylized coastal lines mapping the Gulf shoreline)
        val coastPoints = listOf(
            25.10 to 51.64, // Mesaieed region
            25.15 to 51.62, // Wakra south
            25.17 to 51.61, // Wakra
            25.19 to 51.61, // Wakra North
            25.23 to 51.57, // Doha Bay South
            25.26 to 51.54, // Ras Abu Aboud
            25.29 to 51.53, // Corniche Inner Curve
            25.33 to 51.51, // West Bay Diplomatic Point
            25.36 to 51.54, // Katara / Pearl Base
            25.38 to 51.56, // Pearl Qatar
            25.40 to 51.54, // Lusail South
            25.43 to 51.53, // Lusail Marina
            25.46 to 51.51, // Lusail Stadium North
            25.48 to 51.49  // Towards Al Khor
        )

        val coastPath = Path()
        coastPoints.forEachIndexed { index, pair ->
            val offset = getOffset(pair.first, pair.second)
            if (index == 0) {
                coastPath.moveTo(offset.x, offset.y)
            } else {
                coastPath.lineTo(offset.x, offset.y)
            }
        }

        // Draw coastal land shadows
        drawPath(
            path = coastPath,
            color = Color(0xFF1E293B).copy(alpha = 0.15f),
            style = Stroke(width = 12f, join = StrokeJoin.Round, cap = StrokeCap.Round)
        )
        drawPath(
            path = coastPath,
            color = Color(0xFF38BDF8).copy(alpha = 0.1f),
            style = Stroke(width = 4f, join = StrokeJoin.Round)
        )

        // 3. Draw radar sweep beam
        val beamRad = maxOf(mapWidth, mapHeight)
        val endSweepX = (centerDoha.x + beamRad * kotlin.math.cos(Math.toRadians(scanAngle.toDouble()))).toFloat()
        val endSweepY = (centerDoha.y + beamRad * kotlin.math.sin(Math.toRadians(scanAngle.toDouble()))).toFloat()

        drawLine(
            color = Color(0xFFEF4444).copy(alpha = 0.08f),
            start = centerDoha,
            end = Offset(endSweepX, endSweepY),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 4. Draw links from Rider to all Drop Machines (Dotted tactical arrows)
        val userOffset = getOffset(state.simulatedLatitude, state.simulatedLongitude)

        state.nearestMachine?.let { (nearest, _) ->
            val nearestOffset = getOffset(nearest.latitude, nearest.longitude)
            // Draw a high-contrast path line highlighting target path
            drawLine(
                color = Color(0xFFEF4444).copy(alpha = 0.4f),
                start = userOffset,
                end = nearestOffset,
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f), 0f)
            )
        }

        // 5. Draw all Machines
        viewModel.machines.forEach { machine ->
            val machineOffset = getOffset(machine.latitude, machine.longitude)
            val isNearest = state.nearestMachine?.first?.name == machine.name

            val pinColor = if (isNearest) Color(0xFFEF4444) else Color(0xFF38BDF8)

            // Dynamic pulsing halo for nearest safe drop point
            if (isNearest) {
                drawCircle(
                    color = pinColor.copy(alpha = pulseAlpha * 0.4f),
                    radius = (10f + pulseRadius * 25f) * 1.5f,
                    center = machineOffset
                )
            }

            // Draw machine point container bounding box
            drawCircle(
                color = pinColor,
                radius = 7.dp.toPx(),
                center = machineOffset
            )
            drawCircle(
                color = Color(0xFF0F172A),
                radius = 3.dp.toPx(),
                center = machineOffset
            )
        }

        // 6. Draw Rider Indicator point (Self gold cursor)
        drawCircle(
            color = Color(0xFFF59E0B).copy(alpha = pulseAlpha * 0.5f),
            radius = (12f + pulseRadius * 30f) * 1.5f,
            center = userOffset
        )

        drawCircle(
            color = Color(0xFFF59E0B),
            radius = 6.dp.toPx(),
            center = userOffset
        )

        drawCircle(
            color = Color.White,
            radius = 3.dp.toPx(),
            center = userOffset
        )
    }
}

@Composable
fun NearestMachineCard(state: RiderState, onNavigate: (CODMachine) -> Unit) {
    val nearestPair = state.nearestMachine

    if (nearestPair != null) {
        val (machine, distanceKm) = nearestPair

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(24.dp))
                .testTag("nearest_machine_card")
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFEF4444).copy(alpha = 0.15f), CircleShape)
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NearMe,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "NEAREST SAFE DROP MACHINE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = machine.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Branch: ${machine.branch}",
                            fontSize = 13.sp,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Text(
                                text = "MERCHANT ID: ${machine.merchantId}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = Color(0xFF334155), thickness = 1.dp)

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ESTIMATED DISTANCE",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format("%.2f km", distanceKm),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFEF4444)
                        )
                    }

                    Button(
                        onClick = {
                            onNavigate(machine)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("navigate_nearest_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Navigate", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DropMachinesList(state: RiderState, onNavigate: (CODMachine) -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Safe Drop Machines Qatar",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            state.machinesWithDistance.forEachIndexed { index, (machine, dist) ->
                val isNearest = index == 0

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onNavigate(machine)
                            }
                            .padding(vertical = 12.dp)
                            .testTag("machine_row_${machine.name.take(5)}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isNearest) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF0F172A),
                            border = BorderStroke(
                                1.dp,
                                if (isNearest) Color(0xFFEF4444).copy(alpha = 0.5f) else Color(0xFF334155)
                            ),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (isNearest) Color(0xFFEF4444) else Color(0xFF38BDF8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = machine.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                if (isNearest) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "NEAREST",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444),
                                        modifier = Modifier
                                            .background(Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Branch: ${machine.branch}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "MERCHANT ID: ${machine.merchantId}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = String.format("%.1f km", dist),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNearest) Color(0xFFEF4444) else Color.White
                            )

                            Icon(
                                imageVector = Icons.Default.ArrowOutward,
                                contentDescription = "Open Maps",
                                tint = Color.Gray,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 4.dp)
                            )
                        }
                    }

                    if (index < state.machinesWithDistance.size - 1) {
                        Divider(color = Color(0xFF334155), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// Launches navigation link on Waze directly with fallback options
private fun launchWaze(context: Context, latitude: Double, longitude: Double, fallbackUrl: String) {
    try {
        val wazeUri = "waze://?ll=$latitude,$longitude&navigate=yes"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(wazeUri))
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val webWaze = "https://waze.com/ul?ll=$latitude,$longitude&navigate=yes"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webWaze))
            context.startActivity(intent)
        } catch (ex: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl))
            context.startActivity(intent)
        }
    }
}

// Launches navigation link on Google Maps directly with standard backup option
private fun launchGoogleMaps(context: Context, latitude: Double, longitude: Double, fallbackUrl: String) {
    try {
        val gmapsUri = "google.navigation:q=$latitude,$longitude"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(gmapsUri)).apply {
            setPackage("com.google.android.apps.maps")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val webGmaps = "https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webGmaps))
            context.startActivity(intent)
        } catch (ex: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl))
            context.startActivity(intent)
        }
    }
}

private fun launchMerchantUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open map link.", Toast.LENGTH_SHORT).show()
    }
}

// Request actual GPS locations
private fun requestGpsLocation(context: Context, viewModel: RiderViewModel) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    try {
        val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val hasNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                viewModel.updateLocation(loc.latitude, loc.longitude, true)
                // Stop updates immediately to prevent draining
                locationManager.removeUpdates(this)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        if (hasGps) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                listener
            )
            // Immediately fill last known location as fallback
            val lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastLoc != null) {
                viewModel.updateLocation(lastLoc.latitude, lastLoc.longitude, true)
            }
        } else if (hasNetwork) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                0L,
                0f,
                listener
            )
            val lastLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastLoc != null) {
                viewModel.updateLocation(lastLoc.latitude, lastLoc.longitude, true)
            }
        } else {
            Toast.makeText(context, "GPS providers disabled. Please adjust system settings.", Toast.LENGTH_SHORT).show()
        }
    } catch (e: SecurityException) {
        Toast.makeText(context, "Location permissions missing or bypassed.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Real GPS Unavailable. Simulator Mode active.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun twinPulseSpec(): InfiniteRepeatableSpec<Color> {
    return infiniteRepeatable(
        animation = tween(1200, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse
    )
}
