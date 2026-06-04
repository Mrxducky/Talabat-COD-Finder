package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.model.CODMachine
import com.example.model.LocationPreset
import com.example.viewmodel.RiderState
import com.example.viewmodel.RiderViewModel
import com.google.android.gms.location.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Global tracking client variables to manage continuous Fused GPS lifecycle cleanly
private var fusedLocationClient: FusedLocationProviderClient? = null
private var locationCallback: LocationCallback? = null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: RiderViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var selectedMachineForNavigation by remember { mutableStateOf<CODMachine?>(null) }
    var wasPermissionRequestedOnStart by remember { mutableStateOf(false) }

    // Live state checks for permission & GPS system switches
    var isGpsProviderEnabled by remember { mutableStateOf(true) }
    var isLocationPermissionGranted by remember { mutableStateOf(true) }

    // Fused Location Tracker callback
    val startTrackingGps = {
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        }
        
        // Remove existing callback to prevent duplicate feeds
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }

        // Core high-accuracy 5s GPS location request configuration
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L // 5 seconds
        ).apply {
            setMinUpdateIntervalMillis(5000L)
            setMinUpdateDistanceMeters(0f)
        }.build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { loc ->
                    viewModel.updateLocation(loc.latitude, loc.longitude, true)
                }
            }
        }
        locationCallback = callback

        try {
            // First run check to pull the immediate last coordinates
            fusedLocationClient?.lastLocation?.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    viewModel.updateLocation(loc.latitude, loc.longitude, true)
                }
            }
            
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    // Dynamic Permission requesting launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            isLocationPermissionGranted = true
            Toast.makeText(context, "GPS permission authorized. Real-time logging configured.", Toast.LENGTH_SHORT).show()
            startTrackingGps()
        } else {
            isLocationPermissionGranted = false
            Toast.makeText(context, "Location permission rejected. Running manual navigation simulation mode.", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-sync locations and check system attributes cleanly
    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            val fineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            val coarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            val isGranted = (fineLoc == PackageManager.PERMISSION_GRANTED || coarseLoc == PackageManager.PERMISSION_GRANTED)
            isLocationPermissionGranted = isGranted

            if (isGranted) {
                startTrackingGps()
            } else if (!wasPermissionRequestedOnStart) {
                wasPermissionRequestedOnStart = true
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    // Continuous 5-second validation loop of system components
    LaunchedEffect(state.isLoggedIn) {
        while (state.isLoggedIn) {
            val fineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            val coarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            isLocationPermissionGranted = (fineLoc == PackageManager.PERMISSION_GRANTED || coarseLoc == PackageManager.PERMISSION_GRANTED)

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            isGpsProviderEnabled = try {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (e: Exception) {
                false
            }
            delay(5000L)
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
                                    text = "LIVE GPS",
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
            // Greetings Header Card with active indicators
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
                            // Stop GPS requests, shut listener to save battery
                            locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
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

            // GRACEFUL SYSTEM STATES BANNER (Handle Permission Denied or GPS switch turned off)
            AnimatedVisibility(visible = !isLocationPermissionGranted || !isGpsProviderEnabled) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alert",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "GPS System Discrepancy",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 14.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        val msg = when {
                            !isLocationPermissionGranted && !isGpsProviderEnabled -> 
                                "Location access permissions are revoked AND your physical GPS device sensor is disabled. Authorize permissions and toggle GPS to restore tracing."
                            !isLocationPermissionGranted -> 
                                "Location permission is required for accurate Talabat safe drops. Please tap below to authorize sensor access."
                            else -> 
                                "GPS Navigation satellite hardware is disabled. Slide down your notification tray and activate GPS/Location location parameters."
                        }
                        
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                            lineHeight = 16.sp
                        )
                        
                        if (!isLocationPermissionGranted) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Grant Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // High Tech Map Dashboard Card
            LiveMapDashboard(state, viewModel, locationPermissionLauncher, haptic) { clickedMachineId ->
                val match = viewModel.machines.find { it.merchantId == clickedMachineId }
                if (match != null) {
                    selectedMachineForNavigation = match
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Nearest Machine Focus
            NearestMachineCard(state) { machine ->
                selectedMachineForNavigation = machine
            }

            Spacer(modifier = Modifier.height(20.dp))

            // All Machines Directory List sorted closest to farthest
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
                        text = "Safe Drop Navigation Setup",
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

                    // 1. Google Maps Button (Primary Action)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dialog_google_maps_btn")
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

                    // 2. Waze Button
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
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
            // Radar branding visual logo
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .drawBehind {
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

            // Tab switcher
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

            if (loginTab == 0) {
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, borderColor),
                                    color = bgColor,
                                    modifier = Modifier
                                        .height(56.dp)
                                        .width(90.dp)
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
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text("Send Verification SMS", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                } else {
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
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                            )

                            // PROMINENT MOCK SMS BOX - MAKES SIMULATION EXTREMELY FOOLPROOF
                            Card(
                                colors = CardDefaults.cardColors(containerColor = primaryColor.copy(alpha = 0.12f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "TALABAT SMS SYSTEM:\nYour code is $mockOtp\n(or type master code: 1234)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                            }

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
                                                                if (otpInput == mockOtp || otpInput == "1234") {
                                                                    viewModel.completePhoneLogin(phoneNumber, userNameInput)
                                                                    Toast.makeText(context, "Access Granted! Welcome back.", Toast.LENGTH_SHORT).show()
                                                                } else {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    Toast.makeText(context, "Invalid OTP code. Please enter: $mockOtp", Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                            else -> if (otpInput.length < 4) otpInput += key
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = btnColor, contentColor = contentColor),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(0.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(44.dp)
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
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

            Text(
                text = "TALABAT COURIER UTILITY v3.12 • COOPERATIVE PARTNER QATAR\nDEVELOPMENT INQUIRIES OR FEEDBACK DIRECT TO USER PROFILE PORTAL",
                fontSize = 9.sp,
                color = textMuted.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )
        }
    }

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

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = bgColor,
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
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

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = bgColor,
                        border = BorderStroke(1.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
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
fun LiveMapDashboard(
    state: RiderState,
    viewModel: RiderViewModel,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onMachineClicked: (String) -> Unit
) {
    val context = LocalContext.current
    var isExpandedPresets by remember { mutableStateOf(false) }

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
                        text = "Rider Live Tracker",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = "Live geographic roadmap display",
                        fontSize = 11.sp,
                        color = textMuted
                    )
                }

                Row {
                    // Actual high accuracy GPS trigger
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val fineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                            val coarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (fineLoc == PackageManager.PERMISSION_GRANTED || coarseLoc == PackageManager.PERMISSION_GRANTED) {
                                if (fusedLocationClient == null) {
                                    fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                                }
                                val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()
                                val call = object : LocationCallback() {
                                    override fun onLocationResult(locationResult: LocationResult) {
                                        locationResult.lastLocation?.let { loc ->
                                            viewModel.updateLocation(loc.latitude, loc.longitude, true)
                                        }
                                    }
                                }
                                locationCallback = call
                                try {
                                    fusedLocationClient?.requestLocationUpdates(req, call, Looper.getMainLooper())
                                    Toast.makeText(context, "High accuracy GPS tracking synced! (Every 5s)", Toast.LENGTH_SHORT).show()
                                } catch (e: SecurityException) {}
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
                            isExpandedPresets = !isExpandedPresets
                        },
                        modifier = Modifier
                            .background(borderColor, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpandedPresets) Icons.Default.ExpandLess else Icons.Default.Map,
                            contentDescription = "Toggle Presets",
                            tint = textColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // LIVE GOOGLE MAP FRAME (Renders actual street layouts and user/machine icons)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, borderColor, RoundedCornerShape(20.dp))
                    .testTag("radar_map_canvas")
            ) {
                GoogleMapWebView(
                    state = state,
                    machines = viewModel.machines,
                    onMachineClicked = onMachineClicked
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Landmark presets for manual placement testing
            AnimatedVisibility(visible = isExpandedPresets) {
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
                                    containerColor = if (isDark) Color(0xFF0F172E) else Color(0xFFEDF2FE),
                                    labelColor = textColor
                                ),
                                modifier = Modifier.testTag("preset_chip_${preset.label.take(5)}")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.isRealGpsActive) "🟢 ACTIVE SATELLITE FEED" else "📊 COMPASS DEMO GRID",
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
fun GoogleMapWebView(
    state: RiderState,
    machines: List<CODMachine>,
    onMachineClicked: (String) -> Unit
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Trigger initial placement
                    evaluateJavascript("updateUserLocation(${state.simulatedLatitude}, ${state.simulatedLongitude})", null)
                }
            }
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onMachineClicked(merchantId: String) {
                    onMachineClicked(merchantId)
                }
            }, "AndroidApp")
        }
    }

    // Trigger update on lat/lng shifts
    LaunchedEffect(state.simulatedLatitude, state.simulatedLongitude) {
        webView.evaluateJavascript("updateUserLocation(${state.simulatedLatitude}, ${state.simulatedLongitude})", null)
    }

    // Dynamic initial page loading setup
    LaunchedEffect(Unit) {
        val markersJson = machines.map { m ->
            """
            {
                name: "${m.name.replace("\"", "\\\"")}",
                branch: "${m.branch.replace("\"", "\\\"")}",
                merchantId: "${m.merchantId}",
                lat: ${m.latitude},
                lng: ${m.longitude},
                isNearest: ${state.nearestMachine?.first?.merchantId == m.merchantId}
            }
            """.trimIndent()
        }.joinToString(",")

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    body { margin: 0; padding: 0; background-color: #070B19; }
                    #map { width: 100vw; height: 100vh; }
                    .pulsing-blue-marker {
                        width: 14px;
                        height: 14px;
                        border-radius: 50%;
                        background: #3B82F6;
                        border: 2px solid white;
                        box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.4);
                        animation: pulse 1.5s infinite;
                    }
                    @keyframes pulse {
                        0% { box-shadow: 0 0 0 0px rgba(59, 130, 246, 0.6); }
                        100% { box-shadow: 0 0 0 10px rgba(59, 130, 246, 0); }
                    }
                    .leaflet-popup-content-wrapper {
                        background: #1C2541;
                        color: white;
                        border-radius: 12px;
                        font-family: system-ui, -apple-system, sans-serif;
                    }
                    .leaflet-popup-tip {
                        background: #1C2541;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map', { zoomControl: false }).setView([25.2926, 51.5235], 13);
                    
                    // Voyager Style Tile layer - gives an awesome, modern Google Maps aesthetic
                    L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                        maxZoom: 19,
                        attribution: '&copy; OpenStreetMap'
                    }).addTo(map);

                    var userMarker = null;
                    var machineMarkers = {};
                    var machinesData = [$markersJson];

                    machinesData.forEach(function(m) {
                        var color = m.isNearest ? '#FF5252' : '#06B6D4';
                        var markerHtml = '<div style="background: ' + color + '; width: 14px; height: 14px; border-radius: 50%; border: 2px solid white; box-shadow: 0 1px 3px rgba(0,0,0,0.4);"></div>';
                        var customIcon = L.divIcon({
                            html: markerHtml,
                            className: 'custom-machine-marker',
                            iconSize: [16, 16],
                            iconAnchor: [8, 8]
                        });

                        var marker = L.marker([m.lat, m.lng], { icon: customIcon }).addTo(map);
                        marker.bindPopup("<b style='font-size:13px; color: #fff;'>" + m.name + "</b><br><span style='font-size:11px;color:#aaa;'>" + m.branch + "</span><br><span style='font-size:10px;color:#888;'>ID: " + m.merchantId + "</span>");
                        
                        marker.on('click', function() {
                            if (window.AndroidApp) {
                                window.AndroidApp.onMachineClicked(m.merchantId);
                            }
                        });

                        machineMarkers[m.merchantId] = marker;
                    });

                    function updateUserLocation(lat, lng) {
                        var latLng = [lat, lng];
                        if (!userMarker) {
                            var blueIcon = L.divIcon({
                                className: 'pulsing-blue-marker',
                                iconSize: [14, 14],
                                iconAnchor: [7, 7]
                            });
                            userMarker = L.marker(latLng, { icon: blueIcon }).addTo(map);
                        } else {
                            userMarker.setLatLng(latLng);
                        }
                        map.panTo(latLng);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://localhost", html, "text/html", "UTF-8", null)
    }

    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
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
                    // DISPLAY IN BOTH METERS AND KILOMETERS AS REQUESTED
                    Column {
                        Text(
                            text = "ESTIMATED DISTANCE",
                            fontSize = 10.sp,
                            color = textMuted,
                            fontWeight = FontWeight.Bold
                        )
                        
                        val distanceInMeters = (distanceKm * 1000).roundToInt()
                        val text = if (distanceKm < 1.0) {
                            "$distanceInMeters meters"
                        } else {
                            String.format("%.2f km (%d meters)", distanceKm, distanceInMeters)
                        }

                        Text(
                            text = text,
                            fontSize = 20.sp,
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
                text = "Safe Drop Machines Qatar (Sorted nearest to farthest)",
                fontSize = 15.sp,
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
                            // SHOW SENSORY FEEDS OF METERS AND KILOMETERS
                            val distMeters = (dist * 1000).roundToInt()
                            val distLabel = if (dist < 1.0) {
                                "$distMeters m"
                            } else {
                                String.format("%.2f km", dist)
                            }

                            Text(
                                text = distLabel,
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
