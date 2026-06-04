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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
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

private var activeLocationListener: LocationListener? = null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: RiderViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var selectedMachineForNavigation by remember { mutableStateOf<CODMachine?>(null) }

    // Request permissions launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            Toast.makeText(context, "Location permission granted. Syncing GPS...", Toast.LENGTH_SHORT).show()
            setupContinuousGpsTracking(context, viewModel)
        } else {
            Toast.makeText(context, "Location access denied. Using precision simulator landmark controls.", Toast.LENGTH_LONG).show()
        }
    }

    // AUTO SYNC LOCATION ON STARTUP FOR WAZE REAL TIME EXPECTATION
    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            val fineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            val coarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (fineLoc == PackageManager.PERMISSION_GRANTED || coarseLoc == PackageManager.PERMISSION_GRANTED) {
                setupContinuousGpsTracking(context, viewModel)
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    if (!state.isLoggedIn) {
        LoginScreen(viewModel = viewModel, state = state)
        return
    }

    val isDark = state.isDarkMode
    val surfaceColor = if (isDark) Color(0xFF1C2541) else Color(0xFFFFFFFF)
    val bgColor = if (isDark) Color(0xFF0C132B) else Color(0xFFEFF5FF)
    val primaryColor = if (isDark) Color(0xFFFF5252) else Color(0xFF3B82F6)
    val secondaryColor = if (isDark) Color(0xFF00F2FE) else Color(0xFF06B6D4)
    val borderColor = if (isDark) Color(0xFF3A4E7A) else Color(0xFFD6E4FF)
    val textColor = if (isDark) Color.White else Color(0xFF0C132B)
    val textMuted = if (isDark) Color.LightGray else Color(0xFF475569)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "COD RIDER QATAR",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = bgColor
                ),
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleTheme()
                            }
                        ) {
                            Icon(
                                imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme",
                                tint = if (isDark) Color(0xFFFFB300) else Color(0xFF3B82F6),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(surfaceColor, CircleShape)
                                .border(1.dp, borderColor, CircleShape)
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
                    }
                }
            )
        },
        containerColor = bgColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Greetings Header Card showing profile details
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .shadow(4.dp, RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(primaryColor, secondaryColor)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (state.loggedInUser.ifEmpty { "R" }).take(1).uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Assalamu Alaikum,",
                            fontSize = 11.sp,
                            color = textMuted
                        )
                        Text(
                            text = state.loggedInUser,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        if (state.loggedInPhone.isNotEmpty()) {
                            Text(
                                text = state.loggedInPhone,
                                fontSize = 10.sp,
                                color = textMuted.copy(alpha = 0.8f),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Logout Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.logout()
                            Toast.makeText(context, "Logged out successfully.", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

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
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${machine.name} (${machine.branch})",
                        fontSize = 13.sp,
                        color = textMuted
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
                        color = textMuted,
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
                            containerColor = primaryColor,
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
                        border = BorderStroke(1.dp, borderColor),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = textMuted
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
                    Text("Cancel", color = primaryColor)
                }
            },
            containerColor = surfaceColor,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: RiderViewModel, state: RiderState) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isDark = state.isDarkMode

    // Local colors for LoginScreen
    val surfaceColor = if (isDark) Color(0xFF1C2541) else Color(0xFFFFFFFF)
    val bgColor = if (isDark) Color(0xFF0C132B) else Color(0xFFEFF5FF)
    val primaryColor = if (isDark) Color(0xFFFF5252) else Color(0xFF3B82F6)
    val secondaryColor = if (isDark) Color(0xFF00F2FE) else Color(0xFF06B6D4)
    val borderColor = if (isDark) Color(0xFF3A4E7A) else Color(0xFFD6E4FF)
    val textColor = if (isDark) Color.White else Color(0xFF0C132B)
    val textMuted = if (isDark) Color.LightGray else Color(0xFF475569)

    var loginTab by remember { mutableStateOf(0) } // 0: Phone, 1: Google
    var phoneNumber by remember { mutableStateOf("") }
    var userNameInput by remember { mutableStateOf("") }
    var otpMode by remember { mutableStateOf(false) }
    var otpInput by remember { mutableStateOf("") }
    var mockOtp by remember { mutableStateOf("") }
    var showGoogleAccountChooser by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = bgColor,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "TALABAT DEPOSIT REGISTER",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = textColor
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleTheme()
                        }
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = if (isDark) Color(0xFFFFB300) else Color(0xFF3B82F6),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = bgColor)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant Visual Radar Badge/Logo on login screen
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .drawBehind {
                        // Custom radar drawing inside login
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.15f),
                            radius = size.minDimension / 2
                        )
                        drawCircle(
                            color = secondaryColor.copy(alpha = 0.25f),
                            radius = size.minDimension / 3,
                            style = Stroke(width = 3f)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            Brush.linearGradient(colors = listOf(primaryColor, secondaryColor)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                // Pulsing dot simulation
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.White, CircleShape)
                        .align(Alignment.TopEnd)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "COD Deposit Tracker",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Qatar Safe Drop Machines Locator & Proximity Watch",
                fontSize = 12.sp,
                color = textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Professional Tab Row switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) Color(0xFF0F172E) else Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        loginTab = 0
                        otpMode = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (loginTab == 0) surfaceColor else Color.Transparent,
                        contentColor = if (loginTab == 0) textColor else textMuted
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mobile Number", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        loginTab = 1
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (loginTab == 1) surfaceColor else Color.Transparent,
                        contentColor = if (loginTab == 1) textColor else textMuted
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Google", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Display selected login content
            if (loginTab == 0) {
                // Phone authentication card
                if (!otpMode) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = surfaceColor),
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Enter Your Courier Profile Info",
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            OutlinedTextField(
                                value = userNameInput,
                                onValueChange = { userNameInput = it },
                                label = { Text("Full Name (Optional)", color = textMuted) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor,
                                    focusedBorderColor = primaryColor,
                                    unfocusedBorderColor = borderColor,
                                    focusedContainerColor = bgColor,
                                    unfocusedContainerColor = bgColor
                                ),
                                placeholder = { Text("e.g. Abrehan Khan", color = textMuted.copy(alpha = 0.5f)) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic Country Code Selector
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, borderColor),
                                    color = bgColor,
                                    modifier = Modifier.height(56.dp).width(90.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("🇶🇦 +974", fontWeight = FontWeight.Bold, color = textColor, fontSize = 14.sp)
                                    }
                                }

                                OutlinedTextField(
                                    value = phoneNumber,
                                    onValueChange = { newValue ->
                                        if (newValue.all { it.isDigit() } && newValue.length <= 10) {
                                            phoneNumber = newValue
                                        }
                                    },
                                    label = { Text("Mobile Phone Number", color = textMuted) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = textColor,
                                        unfocusedTextColor = textColor,
                                        focusedBorderColor = primaryColor,
                                        unfocusedBorderColor = borderColor,
                                        focusedContainerColor = bgColor,
                                        unfocusedContainerColor = bgColor
                                    ),
                                    placeholder = { Text("e.g. 5XXXXXXX", color = textMuted.copy(alpha = 0.5f)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (phoneNumber.length < 7) {
                                        Toast.makeText(context, "Please enter a valid Qatar phone number.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        mockOtp = viewModel.startPhoneVerification(phoneNumber)
                                        otpMode = true
                                        Toast.makeText(context, "Verification SMS Sent. Emulator OTP code is: $mockOtp", Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Text("Send Verification SMS", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                } else {
                    // OTP mode view
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = surfaceColor),
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Enter 4-Digit Security Code",
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                fontSize = 16.sp
                            )
                            Text(
                                "Sent via SMS to Qatar number +974 $phoneNumber",
                                color = textMuted,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )

                            // Clean OTP Code display
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(bottom = 20.dp)
                            ) {
                                for (i in 0..3) {
                                    val digit = if (otpInput.length > i) otpInput[i].toString() else ""
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = bgColor,
                                        border = BorderStroke(
                                            2.dp,
                                            if (otpInput.length == i) primaryColor else borderColor
                                        ),
                                        modifier = Modifier.size(50.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = digit,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor
                                            )
                                        }
                                    }
                                }
                            }

                            // Glove Friendly Numeric Keypad drawn directly on screen
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bgColor, RoundedCornerShape(16.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val keys = listOf(
                                        listOf("1", "2", "3"),
                                        listOf("4", "5", "6"),
                                        listOf("7", "8", "9"),
                                        listOf("Clear", "0", "Submit")
                                    )
                                    for (keyRow in keys) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            for (key in keyRow) {
                                                val isAction = key == "Clear" || key == "Submit"
                                                val btnColor = if (isAction) {
                                                    if (key == "Submit") primaryColor else Color.Gray.copy(alpha = 0.2f)
                                                } else {
                                                    surfaceColor
                                                }
                                                val contentColor = if (key == "Submit") Color.White else textColor

                                                Button(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        when (key) {
                                                            "Clear" -> if (otpInput.isNotEmpty()) otpInput = otpInput.dropLast(1)
                                                            "Submit" -> {
                                                                if (otpInput == mockOtp) {
                                                                    viewModel.completePhoneLogin(phoneNumber, userNameInput)
                                                                    Toast.makeText(context, "Access Granted! Welcome back.", Toast.LENGTH_SHORT).show()
                                                                } else {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    Toast.makeText(context, "Invalid OTP code. Please check SMS or try code: $mockOtp", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                            else -> if (otpInput.length < 4) otpInput += key
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = btnColor, contentColor = contentColor),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(0.dp),
                                                    modifier = Modifier.weight(1f).height(44.dp)
                                                ) {
                                                    Text(key, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            TextButton(
                                onClick = {
                                    otpMode = false
                                    otpInput = ""
                                }
                            ) {
                                Text("Back to Edit Profile", color = primaryColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Google Account Selector tab
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Secure Google Identity Portal",
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "One-click safe authentication using talabat registered Google credentials.",
                            color = textMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        // Google Sign In Button
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showGoogleAccountChooser = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color.White else Color(0xFF0F172E),
                                contentColor = if (isDark) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBox,
                                    contentDescription = "G",
                                    tint = if (isDark) Color(0xFFEA4335) else Color(0xFF34A853),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Sign in with Google Account", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Footer compliance text
            Text(
                text = "TALABAT COURIER UTILITY v3.12 • COOPERATIVE PARTNER QATAR\nDEVELOPMENT INQUIRIES OR FEEDBACK DIRECT TO USER PROFILE PORTAL",
                fontSize = 9.sp,
                color = textMuted.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )
        }
    }

    // Google Account Chooser simulation dialog
    if (showGoogleAccountChooser) {
        AlertDialog(
            onDismissRequest = { showGoogleAccountChooser = false },
            title = {
                Text("Choose an Account", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "to continue to COD Rider Qatar",
                        fontSize = 12.sp,
                        color = textMuted,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 1. User specified Email
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = bgColor,
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier.fillMaxWidth().clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.completeGoogleLogin("abrehankhan@gmail.com", "Abrehan Khan")
                            showGoogleAccountChooser = false
                            Toast.makeText(context, "Signed in successfully as Abrehan Khan!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(primaryColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("A", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Abrehan Khan", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
                                Text("abrehankhan@gmail.com", fontSize = 11.sp, color = textMuted)
                            }
                        }
                    }

                    // 2. Mock Guest Courier Account
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = bgColor,
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier.fillMaxWidth().clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.completeGoogleLogin("talabat_rider_doha@gmail.com", "Talabat Rider")
                            showGoogleAccountChooser = false
                            Toast.makeText(context, "Signed in as Talabat Rider!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(secondaryColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("T", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Talabat Rider (Doha)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
                                Text("talabat_rider_doha@gmail.com", fontSize = 11.sp, color = textMuted)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGoogleAccountChooser = false }) {
                    Text("Cancel", color = primaryColor)
                }
            },
            containerColor = surfaceColor,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun BalanceAlertCard(state: RiderState, viewModel: RiderViewModel) {
    val limit = viewModel.getLimitForType(state.riderType)
    val warning = viewModel.getWarningLevel(state.currentCOD, state.riderType)
    val progress = (state.currentCOD / limit).coerceIn(0.0, 1.0).toFloat()

    val isDark = state.isDarkMode
    val surfaceColor = if (isDark) Color(0xFF1C2541) else Color(0xFFFFFFFF)
    val textColor = if (isDark) Color.White else Color(0xFF0C132B)
    val textMuted = if (isDark) Color.LightGray else Color(0xFF475569)

    val progressColor = Color(warning.colorHex)

    val cardColor by animateColorAsState(
        targetValue = progressColor.copy(alpha = 0.15f),
        animationSpec = twinPulseSpec()
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
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
                            surfaceColor,
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
                    color = textMuted,
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
                    color = textColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "/ QAR ${limit.roundToInt()}",
                    fontSize = 16.sp,
                    color = textMuted.copy(alpha = 0.7f),
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
                trackColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
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
    val isDark = state.isDarkMode
    val surfaceColor = if (isDark) Color(0xFF1C2541) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFFFF5252) else Color(0xFF3B82F6)
    val borderColor = if (isDark) Color(0xFF3A4E7A) else Color(0xFFD6E4FF)
    val textColor = if (isDark) Color.White else Color(0xFF0C132B)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Bike Rider Selector
        val isBike = state.riderType == "Bike"
        val bikeBg = if (isBike) primaryColor else surfaceColor
        val bikeBorder = if (isBike) primaryColor.copy(alpha = 0.8f) else borderColor
        val bikeContentColor = if (isBike) Color.White else textColor

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.updateRiderType("Bike")
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = bikeBg, contentColor = bikeContentColor),
            border = BorderStroke(1.5.dp, bikeBorder),
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
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
        val carBg = if (isCar) primaryColor else surfaceColor
        val carBorder = if (isCar) primaryColor.copy(alpha = 0.8f) else borderColor
        val carContentColor = if (isCar) Color.White else textColor

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.updateRiderType("Car")
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = carBg, contentColor = carContentColor),
            border = BorderStroke(1.5.dp, carBorder),
            contentPadding = PaddingValues(vertical = 12.dp),
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
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
    val isDark = state.isDarkMode
    val surfaceColor = if (isDark) Color(0xFF1C2541) else Color(0xFFFFFFFF)
    val bgColor = if (isDark) Color(0xFF0C132B) else Color(0xFFEFF5FF)
    val primaryColor = if (isDark) Color(0xFFFF5252) else Color(0xFF3B82F6)
    val borderColor = if (isDark) Color(0xFF3A4E7A) else Color(0xFFD6E4FF)
    val textColor = if (isDark) Color.White else Color(0xFF0C132B)
    val textMuted = if (isDark) Color.LightGray else Color(0xFF475569)

    var textValue by remember(state.currentCOD) {
        mutableStateOf(if (state.currentCOD == 0.0) "" else state.currentCOD.roundToInt().toString())
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Update Cash Status",
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isDigit() }
                    textValue = filtered
                    val doubleVal = filtered.toDoubleOrNull() ?: 0.0
                    viewModel.updateCOD(doubleVal)
                },
                placeholder = {
                    Text("Enter total balance amount in QAR", color = textMuted, fontSize = 14.sp)
                },
                trailingIcon = {
                    if (textValue.isNotEmpty()) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.clearCOD()
                            textValue = ""
                        }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = textMuted)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                maxLines = 1,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = bgColor,
                    unfocusedContainerColor = bgColor,
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = borderColor
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cod_input_field")
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                            containerColor = if (isDark) Color(0xFF334155) else Color(0xFFD6E4FF),
                            contentColor = if (isDark) Color.White else Color(0xFF1E3A8A)
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
                        containerColor = primaryColor.copy(alpha = 0.15f),
                        contentColor = primaryColor
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)),
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

    val isDark = state.isDarkMode
    val surfaceColor = if (isDark) Color(0xFF1C2541) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFFFF5252) else Color(0xFF3B82F6)
    val borderColor = if (isDark) Color(0xFF3A4E7A) else Color(0xFFD6E4FF)
    val textColor = if (isDark) Color.White else Color(0xFF0C132B)
    val textMuted = if (isDark) Color.LightGray else Color(0xFF475569)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, borderColor),
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
                        color = textColor
                    )
                    Text(
                        text = "High-precision regional tactical radar",
                        fontSize = 11.sp,
                        color = textMuted
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
                                setupContinuousGpsTracking(context, viewModel)
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
                                if (state.isRealGpsActive) Color(0xFF10B981).copy(alpha = 0.2f) else borderColor,
                                CircleShape
                            )
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Sync Real GPS",
                            tint = if (state.isRealGpsActive) Color(0xFF10B981) else textColor,
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
                            .background(borderColor, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.Map,
                            contentDescription = "Toggle Presets",
                            tint = textColor,
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
                    .border(2.dp, borderColor, RoundedCornerShape(20.dp))
                    .testTag("radar_map_canvas")
            ) {
                InteractiveQatarRadarCanvas(state, viewModel)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Preset locations
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Text(
                        text = "Simulate Rider Landmark Coordinates",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
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
                                        Text(preset.description, fontSize = 9.sp, color = textMuted)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primaryColor,
                                    selectedLabelColor = Color.White,
                                    containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFEDF2FE),
                                    labelColor = textColor
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
                    text = if (state.isRealGpsActive) "🟢 ACTIVE SATELLITE LOCK" else "📊 GPS COMPASS SIMULATION",
                    fontSize = 10.sp,
                    color = if (state.isRealGpsActive) Color(0xFF10B981) else Color(0xFFFFB300)
                )

                Text(
                    text = String.format("%.4f° N, %.4f° E", state.simulatedLatitude, state.simulatedLongitude),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = textMuted
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

    val isDark = state.isDarkMode
    val canvasBgColor = if (isDark) Color(0xFF070B19) else Color(0xFFE2ECFE)
    val gridColor = if (isDark) Color(0xFF1D294E).copy(alpha = 0.4f) else Color(0xFFBACCE6).copy(alpha = 0.5f)
    val primaryColor = if (isDark) Color(0xFFFF5252) else Color(0xFF3B82F6)
    val secondaryColor = if (isDark) Color(0xFF00F2FE) else Color(0xFF06B6D4)
    val userCursorColor = if (isDark) Color(0xFFFFB300) else Color(0xFF1E3A8A)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBgColor)
    ) {
        val minLat = 25.10
        val maxLat = 25.48
        val minLng = 51.35
        val maxLng = 51.68

        val mapWidth = size.width
        val mapHeight = size.height

        fun getOffset(latitude: Double, longitude: Double): Offset {
            val yRatio = (maxLat - latitude) / (maxLat - minLat).toFloat()
            val xRatio = (longitude - minLng) / (maxLng - minLng).toFloat()
            return Offset(
                x = (xRatio * mapWidth).toFloat(),
                y = (yRatio * mapHeight).toFloat()
            )
        }

        // concentric radar ranges around Central Doha
        val centerDoha = getOffset(25.2854, 51.5310)

        for (dist in listOf(20f, 60f, 110f, 170f, 240f)) {
            drawCircle(
                color = gridColor,
                radius = dist * 2f,
                center = centerDoha,
                style = Stroke(width = 1f)
            )
        }

        // coordinate grid lines
        for (x_line in (0..4)) {
            val x_pos = (x_line / 4f) * mapWidth
            drawLine(
                color = gridColor.copy(alpha = 0.2f),
                start = Offset(x_pos, 0f),
                end = Offset(x_pos, mapHeight),
                strokeWidth = 1f
            )
        }
        for (y_line in (0..4)) {
            val y_pos = (y_line / 4f) * mapHeight
            drawLine(
                color = gridColor.copy(alpha = 0.2f),
                start = Offset(0f, y_pos),
                end = Offset(mapWidth, y_pos),
                strokeWidth = 1f
            )
        }

        // Coastal outline of Qatar
        val coastPoints = listOf(
            25.10 to 51.64,
            25.15 to 51.62,
            25.17 to 51.61,
            25.19 to 51.61,
            25.23 to 51.57,
            25.26 to 51.54,
            25.29 to 51.53,
            25.33 to 51.51,
            25.36 to 51.54,
            25.38 to 51.56,
            25.40 to 51.54,
            25.43 to 51.53,
            25.46 to 51.51,
            25.48 to 51.49
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

        drawPath(
            path = coastPath,
            color = gridColor.copy(alpha = 0.3f),
            style = Stroke(width = 10f, join = StrokeJoin.Round, cap = StrokeCap.Round)
        )

        // Radar Sweep beam
        val beamRad = maxOf(mapWidth, mapHeight)
        val endSweepX = (centerDoha.x + beamRad * kotlin.math.cos(Math.toRadians(scanAngle.toDouble()))).toFloat()
        val endSweepY = (centerDoha.y + beamRad * kotlin.math.sin(Math.toRadians(scanAngle.toDouble()))).toFloat()

        drawLine(
            color = primaryColor.copy(alpha = 0.08f),
            start = centerDoha,
            end = Offset(endSweepX, endSweepY),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Link User to the nearest safe drop machine
        val userOffset = getOffset(state.simulatedLatitude, state.simulatedLongitude)

        state.nearestMachine?.let { (nearest, _) ->
            val nearestOffset = getOffset(nearest.latitude, nearest.longitude)
            drawLine(
                color = primaryColor.copy(alpha = 0.5f),
                start = userOffset,
                end = nearestOffset,
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f), 0f)
            )
        }

        // Draw all deposit machines on canvas
        viewModel.machines.forEach { machine ->
            val machineOffset = getOffset(machine.latitude, machine.longitude)
            val isNearest = state.nearestMachine?.first?.name == machine.name

            val pinColor = if (isNearest) primaryColor else secondaryColor

            if (isNearest) {
                drawCircle(
                    color = pinColor.copy(alpha = pulseAlpha * 0.4f),
                    radius = (10f + pulseRadius * 25f) * 1.5f,
                    center = machineOffset
                )
            }

            drawCircle(
                color = pinColor,
                radius = 7.dp.toPx(),
                center = machineOffset
            )
            drawCircle(
                color = canvasBgColor,
                radius = 3.dp.toPx(),
                center = machineOffset
            )
        }

        // Draw self rider indicator
        drawCircle(
            color = userCursorColor.copy(alpha = pulseAlpha * 0.5f),
            radius = (12f + pulseRadius * 30f) * 1.5f,
            center = userOffset
        )
        drawCircle(
            color = userCursorColor,
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

    val isDark = state.isDarkMode
    val surfaceColor = if (isDark) Color(0xFF1C2541) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFFFF5252) else Color(0xFF3B82F6)
    val borderColor = if (isDark) Color(0xFF3A4E7A) else Color(0xFFD6E4FF)
    val textColor = if (isDark) Color.White else Color(0xFF0C132B)
    val textMuted = if (isDark) Color.LightGray else Color(0xFF475569)

    if (nearestPair != null) {
        val (machine, distanceKm) = nearestPair

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            border = BorderStroke(1.5.dp, primaryColor.copy(alpha = 0.5f)),
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
                            .background(primaryColor.copy(alpha = 0.15f), CircleShape)
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NearMe,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "NEAREST SAFE DROP MACHINE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = machine.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = "Branch: ${machine.branch}",
                            fontSize = 13.sp,
                            color = textMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDark) Color(0xFF0F172A) else Color(0xFFEDF2FE),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Text(
                                text = "MERCHANT ID: ${machine.merchantId}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFFFFB300) else Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = borderColor, thickness = 1.dp)

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
                            color = textMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format("%.2f km", distanceKm),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = primaryColor
                        )
                    }

                    Button(
                        onClick = {
                            onNavigate(machine)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
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
    val isDark = state.isDarkMode
    val surfaceColor = if (isDark) Color(0xFF1C2541) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFFFF5252) else Color(0xFF3B82F6)
    val secondaryColor = if (isDark) Color(0xFF00F2FE) else Color(0xFF06B6D4)
    val borderColor = if (isDark) Color(0xFF3A4E7A) else Color(0xFFD6E4FF)
    val textColor = if (isDark) Color.White else Color(0xFF0C132B)
    val textMuted = if (isDark) Color.LightGray else Color(0xFF475569)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Safe Drop Machines Qatar",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
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
                            color = if (isNearest) primaryColor.copy(alpha = 0.15f) else (if (isDark) Color(0xFF0F172A) else Color(0xFFEDF2FE)),
                            border = BorderStroke(
                                1.dp,
                                if (isNearest) primaryColor.copy(alpha = 0.5f) else borderColor
                            ),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (isNearest) primaryColor else secondaryColor,
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
                                    color = textColor
                                )
                                if (isNearest) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "NEAREST",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor,
                                        modifier = Modifier
                                            .background(primaryColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Branch: ${machine.branch}",
                                fontSize = 12.sp,
                                color = textMuted
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "MERCHANT ID: ${machine.merchantId}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = secondaryColor
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = String.format("%.1f km", dist),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNearest) primaryColor else textColor
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
                        HorizontalDivider(color = borderColor, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

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

private fun setupContinuousGpsTracking(context: Context, viewModel: RiderViewModel) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    try {
        val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val hasNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                viewModel.updateLocation(loc.latitude, loc.longitude, true)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        activeLocationListener?.let { locationManager.removeUpdates(it) }
        activeLocationListener = listener

        if (hasGps) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L,
                1f,
                listener
            )
            val lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastLoc != null) {
                viewModel.updateLocation(lastLoc.latitude, lastLoc.longitude, true)
            }
        } else if (hasNetwork) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                2000L,
                1f,
                listener
            )
            val lastLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastLoc != null) {
                viewModel.updateLocation(lastLoc.latitude, lastLoc.longitude, true)
            }
        }
    } catch (e: SecurityException) {
        Toast.makeText(context, "Location authorization bypassed with local telemetry simulator.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
         // Silently ignore or fallback
    }
}

@Composable
fun twinPulseSpec(): InfiniteRepeatableSpec<Color> {
    return infiniteRepeatable(
        animation = tween(1200, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse
    )
}
