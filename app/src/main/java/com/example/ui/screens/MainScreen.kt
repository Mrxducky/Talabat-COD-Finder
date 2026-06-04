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
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.viewmodel.RiderState
import com.example.viewmodel.RiderViewModel
import com.google.android.gms.location.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private var fusedLocationClient: FusedLocationProviderClient? = null
private var locationCallback: LocationCallback? = null

enum class ScreenType {
    MAP,
    SETTINGS,
    NAVIGATION_SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: RiderViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var currentScreen by remember { mutableStateOf(ScreenType.MAP) }
    var selectedMachineForNavigation by remember { mutableStateOf<CODMachine?>(null) }
    var isSimulatingNavigation by remember { mutableStateOf(false) }

    // Live state checks for permission & GPS system switches
    var isGpsProviderEnabled by remember { mutableStateOf(true) }
    var isLocationPermissionGranted by remember { mutableStateOf(true) }

    val startTrackingGps = {
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        }
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).apply {
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
            fusedLocationClient?.lastLocation?.addOnSuccessListener { loc ->
                if (loc != null) {
                    viewModel.updateLocation(loc.latitude, loc.longitude, true)
                }
            }
            fusedLocationClient?.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            isLocationPermissionGranted = true
            startTrackingGps()
            Toast.makeText(context, "GPS permission authorized.", Toast.LENGTH_SHORT).show()
        } else {
            isLocationPermissionGranted = false
            Toast.makeText(context, "Location permission rejected. Simulation active.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            isLocationPermissionGranted = isGranted
            if (isGranted) startTrackingGps()
            else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    if (!state.isLoggedIn) {
        LoginScreen(viewModel = viewModel, state = state)
        return
    }

    // Themes
    val isDark = state.isDarkMode
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val primaryColor = Color(0xFFFF5722) // Genuine Biker orange accent
    val textColor = if (isDark) Color.White else Color(0xFF111827)
    val textMuted = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF),
                modifier = Modifier.width(300.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Banner with Curved Orange Brush Gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFFF5722), Color(0xFFFF7043))
                                ),
                                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                            )
                            .padding(24.dp)
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Hi, Abrehan Khan",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Abdul Khan ALN 👋",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Grid layout of 2x2 cards
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Inbox
                            Card(
                                onClick = { Toast.makeText(context, "Inbox: 99+ notifications", Toast.LENGTH_SHORT).show() },
                                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF374151) else Color(0xFFF3F4F6)),
                                modifier = Modifier.weight(1f).height(74.dp)
                            ) {
                                Box(modifier = Modifier.padding(10.dp)) {
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Icon(Icons.Default.Mail, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                                            Box(modifier = Modifier.background(Color(0xFFEF4444), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                Text("+99", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Inbox", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                                    }
                                }
                            }
                            // Schedule
                            Card(
                                onClick = { Toast.makeText(context, "Schedule Grid loaded", Toast.LENGTH_SHORT).show() },
                                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF374151) else Color(0xFFF3F4F6)),
                                modifier = Modifier.weight(1f).height(74.dp)
                            ) {
                                Box(modifier = Modifier.padding(10.dp)) {
                                    Column {
                                        Icon(Icons.Default.DateRange, contentDescription = null, tint = textColor, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Schedule", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Wallet
                            Card(
                                onClick = { Toast.makeText(context, "Wallet summary", Toast.LENGTH_SHORT).show() },
                                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF374151) else Color(0xFFF3F4F6)),
                                modifier = Modifier.weight(1f).height(74.dp)
                            ) {
                                Box(modifier = Modifier.padding(10.dp)) {
                                    Column {
                                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = textColor, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Wallet", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                                    }
                                }
                            }
                            // Payments
                            Card(
                                onClick = { Toast.makeText(context, "Payment processing logs", Toast.LENGTH_SHORT).show() },
                                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF374151) else Color(0xFFF3F4F6)),
                                modifier = Modifier.weight(1f).height(74.dp)
                            ) {
                                Box(modifier = Modifier.padding(10.dp)) {
                                    Column {
                                        Icon(Icons.Default.Payment, contentDescription = null, tint = textColor, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Payments", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = borderColor, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Menu rows
                    Column(modifier = Modifier.weight(1f)) {
                        DrawerMenuItem(Icons.Default.Person, "My profile", textColor) {
                            scope.launch { drawerState.close() }
                            Toast.makeText(context, "Profile setup", Toast.LENGTH_SHORT).show()
                        }
                        DrawerMenuItem(Icons.Default.Star, "Opportunities", textColor) {
                            scope.launch { drawerState.close() }
                            Toast.makeText(context, "Opportunities", Toast.LENGTH_SHORT).show()
                        }
                        DrawerMenuItem(Icons.Default.Info, "Resources", textColor, hasChevron = true) {
                            Toast.makeText(context, "Resources", Toast.LENGTH_SHORT).show()
                        }
                        DrawerMenuItem(Icons.Default.Settings, "Settings and privacy", textColor) {
                            scope.launch { drawerState.close() }
                            currentScreen = ScreenType.SETTINGS
                        }
                    }

                    // ID Badge profile block
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                        color = if (isDark) Color(0xFF111827) else Color(0xFFF9FAFB),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(primaryColor), contentAlignment = Alignment.Center) {
                                Text("A", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Abrehan Khan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                                Text("ID: 887192 • Doha Courier", fontSize = 10.sp, color = textMuted)
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Badge, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                ScreenType.MAP -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 1. Full Screen Interactive Map Webview
                        GoogleMapWebView(
                            state = state,
                            machines = viewModel.machines,
                            onMachineClicked = { merchantId ->
                                val machine = viewModel.machines.find { it.merchantId == merchantId }
                                if (machine != null) {
                                    viewModel.selectMachine(machine)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        )

                        // 2. Head-Up Live Navigation HUD (Original GPS Progress Simulator)
                        AnimatedVisibility(
                            visible = isSimulatingNavigation,
                            enter = slideInVertically { -it } + fadeIn(),
                            exit = slideOutVertically { -it } + fadeOut()
                        ) {
                            val dest = state.selectedMachine ?: state.nearestMachine?.first
                            Surface(
                                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp).shadow(8.dp, RoundedCornerShape(12.dp)),
                                color = Color(0xFF10B981),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Turn left in 150m onto Salwa Road", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                            Text("Heading: North-East • Target: ${dest?.name ?: "COD drop"}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                        }
                                        IconButton(onClick = { isSimulatingNavigation = false }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close navigation", tint = Color.White)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Speed: 45 km/h", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("ETA: 3 mins", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Distance: 1.1 km", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            isSimulatingNavigation = false
                                            viewModel.selectMachine(null)
                                            Toast.makeText(context, "Arrived at Drop spot! COD Deposit Completed Successfully.", Toast.LENGTH_LONG).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF10B981)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Confirm Safe Drop / Cash Deposit Now", fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }

                        // Top Layer Floating UI Row
                        if (!isSimulatingNavigation) {
                            Row(
                                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.shadow(4.dp, CircleShape).background(surfaceColor, CircleShape).size(44.dp)
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "DrawerMenu", tint = textColor)
                                }

                                Surface(
                                    modifier = Modifier.shadow(4.dp, RoundedCornerShape(24.dp)).clickable { viewModel.toggleOnlineStatus() },
                                    color = surfaceColor,
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(1.dp, if (state.isOnline) Color(0xFF10B981) else Color.Gray)
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Status", fontSize = 9.sp, color = textMuted, fontWeight = FontWeight.SemiBold)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(if (state.isOnline) "Online" else "Offline", fontSize = 14.sp, fontWeight = FontWeight.Black, color = textColor)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(modifier = Modifier.size(8.dp).background(if (state.isOnline) Color(0xFF10B981) else Color.Gray, CircleShape))
                                            }
                                        }
                                    }
                                }

                                Box {
                                    IconButton(
                                        onClick = { Toast.makeText(context, "Courier emergency line is Active", Toast.LENGTH_SHORT).show() },
                                        modifier = Modifier.shadow(4.dp, CircleShape).background(surfaceColor, CircleShape).size(44.dp)
                                    ) {
                                        Icon(Icons.Default.Headset, contentDescription = "Help hotline", tint = textColor)
                                    }
                                    Box(modifier = Modifier.padding(3.dp).align(Alignment.TopEnd).size(10.dp).background(Color.Red, CircleShape))
                                }
                            }
                        }

                        // local search bar directly grouped inside the map (hides standard maps search!)
                        var searchQuery by remember { mutableStateOf("") }
                        val filteredMachines = remember(searchQuery) {
                            if (searchQuery.isBlank()) emptyList()
                            else viewModel.machines.filter {
                                it.name.contains(searchQuery, ignoreCase = true) || it.branch.contains(searchQuery, ignoreCase = true)
                            }.take(5)
                        }

                        if (!isSimulatingNavigation) {
                            Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 90.dp).padding(horizontal = 16.dp)) {
                                Surface(modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(12.dp)), color = surfaceColor, shape = RoundedCornerShape(12.dp)) {
                                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = textMuted)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            textStyle = androidx.compose.ui.text.TextStyle(color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                                            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                                            decorationBox = { inner ->
                                                if (searchQuery.isEmpty()) Text("Search safe drop places...", color = textMuted, fontSize = 13.sp)
                                                inner()
                                            }
                                        )
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Close, contentDescription = null, tint = textMuted, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = searchQuery.isNotEmpty() && filteredMachines.isNotEmpty()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).shadow(6.dp, RoundedCornerShape(12.dp)).border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                                        color = surfaceColor,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column {
                                            filteredMachines.forEach { m ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().clickable {
                                                        searchQuery = ""
                                                        viewModel.selectMachine(m)
                                                        viewModel.updateLocation(m.latitude, m.longitude, false)
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Column {
                                                        Text(m.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                                                        Text("Branch: ${m.branch}", fontSize = 11.sp, color = textMuted)
                                                    }
                                                }
                                                HorizontalDivider(color = borderColor, thickness = 0.5.dp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Rightside Floating Controls above Bottom Card (Compass, MyLocation real GPS jump, Orange Route icon)
                        if (!isSimulatingNavigation) {
                            Column(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 270.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                IconButton(
                                    onClick = { Toast.makeText(context, "Compass aligned true North", Toast.LENGTH_SHORT).show() },
                                    modifier = Modifier.shadow(4.dp, CircleShape).background(surfaceColor, CircleShape).size(44.dp)
                                ) {
                                    Icon(Icons.Default.Explore, contentDescription = "Compass", tint = primaryColor)
                                }

                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (isLocationPermissionGranted) {
                                            startTrackingGps()
                                            Toast.makeText(context, "Centered to Real GPS Satellite feedback!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.updateLocation(25.2926, 51.5235, false)
                                            Toast.makeText(context, "Jumping to Doha simulated tracker coordinates", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.shadow(4.dp, CircleShape).background(surfaceColor, CircleShape).size(44.dp)
                                ) {
                                    Icon(Icons.Default.MyLocation, contentDescription = "GPS Lock", tint = if (state.isRealGpsActive) Color(0xFF10B981) else textColor)
                                }

                                IconButton(
                                    onClick = {
                                        val m = state.selectedMachine ?: state.nearestMachine?.first
                                        if (m != null) selectedMachineForNavigation = m
                                    },
                                    modifier = Modifier.shadow(6.dp, CircleShape).background(primaryColor, CircleShape).size(52.dp)
                                ) {
                                    Icon(Icons.Default.Navigation, contentDescription = "Route Setup", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                        }

                        // Bottom Details Sheet Card
                        val activeMachine = state.selectedMachine ?: state.nearestMachine?.first
                        if (activeMachine != null && !isSimulatingNavigation) {
                            val distanceKm = state.nearestMachine?.second ?: 1.1
                            val distLabel = if (distanceKm < 1.0) "${(distanceKm * 1000).roundToInt()} meters" else String.format("%.2f km", distanceKm)

                            Card(
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).shadow(12.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                                    Box(modifier = Modifier.width(36.dp).height(4.dp).background(borderColor, RoundedCornerShape(2.dp)).align(Alignment.CenterHorizontally))
                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(activeMachine.name, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = textColor)
                                            Text("Arrive soon • $distLabel • ID: ${activeMachine.merchantId}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("Zone 56, Street 964, Qatar Grand Mall, Doha", fontSize = 12.sp, color = textMuted)
                                        }
                                        IconButton(
                                            onClick = { Toast.makeText(context, "Secure chat opened", Toast.LENGTH_SHORT).show() },
                                            modifier = Modifier.background(primaryColor.copy(alpha = 0.15f), CircleShape).size(42.dp)
                                        ) {
                                            Icon(Icons.Default.Chat, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Tab selectors
                                    Row(modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(8.dp)).padding(3.dp)) {
                                        Button(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (state.navigationType == "InApp") {
                                                    isSimulatingNavigation = true
                                                } else {
                                                    selectedMachineForNavigation = activeMachine
                                                }
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = textColor),
                                            modifier = Modifier.weight(1f).height(40.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Bolt, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Start now", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Button(
                                            onClick = { Toast.makeText(context, "No scheduled booking slots", Toast.LENGTH_SHORT).show() },
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.1f), contentColor = primaryColor),
                                            modifier = Modifier.weight(1f).height(40.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.DateRange, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Sessions", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { Toast.makeText(context, "Scanning current active promotions...", Toast.LENGTH_SHORT).show() },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF374151) else Color(0xFFF3F4F6)),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("See sessions or tap pins to explore opportunities", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                ScreenType.SETTINGS -> {
                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = { Text("Settings and privacy", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor) },
                                navigationIcon = {
                                    IconButton(onClick = { currentScreen = ScreenType.MAP }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = surfaceColor)
                            )
                        },
                        containerColor = bgColor
                    ) { inner ->
                        Column(modifier = Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Check Notifications Card row
                            Card(
                                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                                border = BorderStroke(1.dp, borderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Notifications, contentDescription = null, tint = primaryColor, modifier = Modifier.size(34.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Check notifications", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
                                        Text("Make sure you don't miss any notifications from the Rider app", fontSize = 11.sp, color = textMuted, lineHeight = 15.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { Toast.makeText(context, "Notifications status OK", Toast.LENGTH_SHORT).show() },
                                            colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB), contentColor = textColor),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
                                        ) {
                                            Text("Check now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Text("Your preferences", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor, modifier = Modifier.padding(top = 8.dp))

                            // Chat language
                            PreferenceRow("Chat language", "English", Icons.Default.Chat, textColor) {
                                Toast.makeText(context, "Language: English", Toast.LENGTH_SHORT).show()
                            }
                            // Navigation preference option
                            PreferenceRow("Navigation", "Mapbox & external", Icons.Default.Navigation, textColor) {
                                currentScreen = ScreenType.NAVIGATION_SETTINGS
                            }
                            // Device Settings
                            PreferenceRow("Device Settings", "Storage, logs", Icons.Default.Smartphone, textColor) {
                                Toast.makeText(context, "Opening device parameters...", Toast.LENGTH_SHORT).show()
                            }
                            // Biometric sign in toggle
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, borderColor, RoundedCornerShape(12.dp)).clickable { viewModel.toggleBiometricSignIn() },
                                color = surfaceColor
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Biometric sign in", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
                                        Text("Use your face scan or fingerprint for fast and secure sign-in", fontSize = 11.sp, color = textMuted)
                                    }
                                    Switch(checked = state.biometricSignIn, onCheckedChange = { viewModel.toggleBiometricSignIn() }, colors = SwitchDefaults.colors(checkedThumbColor = primaryColor))
                                }
                            }
                        }
                    }
                }

                ScreenType.NAVIGATION_SETTINGS -> {
                    Scaffold(
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = { Text("Navigation", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor) },
                                navigationIcon = {
                                    IconButton(onClick = { currentScreen = ScreenType.SETTINGS }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = surfaceColor)
                            )
                        },
                        containerColor = bgColor
                    ) { inner ->
                        Column(modifier = Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Talabat navigation radio button
                            NavigationSelectorCard(
                                title = "Talabat Rider navigation",
                                subtitle = "Powered by Mapbox",
                                description = "Use in-app navigation to stay in the app",
                                isSelected = state.navigationType == "InApp",
                                surfaceColor = surfaceColor,
                                borderColor = borderColor,
                                textColor = textColor,
                                textMuted = textMuted,
                                checkedColor = primaryColor
                            ) {
                                viewModel.updateNavigationType("InApp")
                            }

                            // Sub-switches indented for InApp Mapbox features
                            AnimatedVisibility(visible = state.navigationType == "InApp") {
                                Column(modifier = Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SubToggleRow("Avoid Highways", state.avoidHighways, textMuted, textColor, primaryColor) { viewModel.toggleAvoidHighways() }
                                    SubToggleRow("Avoid Tolls", state.avoidTolls, textMuted, textColor, primaryColor) { viewModel.toggleAvoidTolls() }
                                }
                            }

                            // External navigation radio option
                            NavigationSelectorCard(
                                title = "External navigation",
                                subtitle = "Device Default App",
                                description = "Use your device's default navigation app",
                                isSelected = state.navigationType == "External",
                                surfaceColor = surfaceColor,
                                borderColor = borderColor,
                                textColor = textColor,
                                textMuted = textMuted,
                                checkedColor = primaryColor
                            ) {
                                viewModel.updateNavigationType("External")
                            }

                            HorizontalDivider(color = borderColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                            // Mapbox metrics toggle row
                            Row(
                                modifier = Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(12.dp)).background(surfaceColor, RoundedCornerShape(12.dp)).border(1.dp, borderColor, RoundedCornerShape(12.dp)).padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Mapbox metrics", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
                                    Text("Improve map and routing information", fontSize = 11.sp, color = textMuted)
                                }
                                Switch(checked = state.mapboxMetrics, onCheckedChange = { viewModel.toggleMapboxMetrics() }, colors = SwitchDefaults.colors(checkedThumbColor = primaryColor))
                            }
                        }
                    }
                }
            }
        }
    }

    // Routing Popup launcher Dialog
    if (selectedMachineForNavigation != null) {
        val machine = selectedMachineForNavigation!!
        AlertDialog(
            onDismissRequest = { selectedMachineForNavigation = null },
            title = {
                Column {
                    Text("Safe Drop Navigation Setup", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${machine.name} (${machine.branch})", fontSize = 12.sp, color = textMuted)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose your preferred routing application below to begin step-by-step turn guidance.", fontSize = 13.sp, color = textMuted)
                    Button(
                        onClick = {
                            launchGoogleMaps(context, machine.latitude, machine.longitude, machine.mapUrl)
                            selectedMachineForNavigation = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Navigate with Google Maps", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            launchWaze(context, machine.latitude, machine.longitude, machine.mapUrl)
                            selectedMachineForNavigation = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33D1FF), contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Navigate with Waze", fontWeight = FontWeight.Bold)
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
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun DrawerMenuItem(icon: ImageVector, label: String, textColor: Color, hasChevron: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textColor, modifier = Modifier.weight(1f))
        if (hasChevron) Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = textColor.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
    }
}

@Composable
fun PreferenceRow(title: String, status: String, icon: ImageVector, textColor: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() },
        color = Color.Transparent
    ) {
        Row(modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor, modifier = Modifier.weight(1f))
            Text(status, fontSize = 13.sp, color = Color(0xFFFF5722), fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = textColor.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun NavigationSelectorCard(title: String, subtitle: String, description: String, isSelected: Boolean, surfaceColor: Color, borderColor: Color, textColor: Color, textMuted: Color, checkedColor: Color, onSelect: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(12.dp)).border(1.dp, if (isSelected) checkedColor.copy(alpha = 0.6f) else borderColor, RoundedCornerShape(12.dp)).clickable { onSelect() },
        color = surfaceColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = isSelected, onClick = { onSelect() }, colors = RadioButtonDefaults.colors(selectedColor = checkedColor))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = textColor)
                    if (subtitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("($subtitle)", fontSize = 11.sp, color = checkedColor, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, fontSize = 11.sp, color = textMuted)
            }
        }
    }
}

@Composable
fun SubToggleRow(label: String, checked: Boolean, textMuted: Color, textColor: Color, activeColor: Color, onChecked: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            Text("*Requires Talabat Rider navigation", fontSize = 9.sp, color = textMuted)
        }
        Switch(checked = checked, onCheckedChange = { onChecked() }, colors = SwitchDefaults.colors(checkedThumbColor = activeColor))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: RiderViewModel, state: RiderState) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isDark = state.isDarkMode
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val primaryColor = Color(0xFFFF5722)
    val textColor = if (isDark) Color.White else Color(0xFF111827)
    val textMuted = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)

    var phoneNumber by remember { mutableStateOf("") }
    var userDetails by remember { mutableStateOf("") }

    Scaffold(
        containerColor = bgColor,
        topBar = { CenterAlignedTopAppBar(title = { Text("TALABAT DEPOSIT PORTAL", fontWeight = FontWeight.Black, fontSize = 15.sp, color = textColor) }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = surfaceColor)) }
    ) { inner ->
        Column(modifier = Modifier.padding(inner).fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(primaryColor), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SportsMotorsports, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text("Registered QAR Rider Access", fontSize = 18.sp, fontWeight = FontWeight.Black, color = textColor)
            Text("Enter credentials for active Qatar COD deposit log tracking", fontSize = 12.sp, color = textMuted, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = userDetails,
                onValueChange = { userDetails = it },
                label = { Text("Driver Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, focusedLabelColor = primaryColor)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("WhatsApp registered phone") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, focusedLabelColor = primaryColor)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.completePhoneLogin(phoneNumber.ifBlank { "9746682" }, userDetails.ifBlank { "Abrehan Khan" })
                    Toast.makeText(context, "Authenticated successfully as Abrehan Khan!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Login to Live Delivery Map", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = textMuted.copy(alpha = 0.3f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.completeGoogleLogin("abrehankhan@gmail.com", "Abrehan Khan")
                    Toast.makeText(context, "Direct Signed in automatically as Abrehan Khan!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color.White else Color(0xFF1F2937), contentColor = if (isDark) Color.Black else Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBox, contentDescription = null, tint = primaryColor)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Seamless Google Access", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun GoogleMapWebView(state: RiderState, machines: List<CODMachine>, onMachineClicked: (String) -> Unit) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
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

    LaunchedEffect(state.simulatedLatitude, state.simulatedLongitude) {
        webView.evaluateJavascript("updateUserLocation(${state.simulatedLatitude}, ${state.simulatedLongitude})", null)
    }

    val activeSelected = state.selectedMachine ?: state.nearestMachine?.first
    LaunchedEffect(activeSelected?.merchantId) {
        if (activeSelected != null) {
            webView.evaluateJavascript("updateTargetAndRoute(${activeSelected.latitude}, ${activeSelected.longitude}, '${activeSelected.merchantId}')", null)
        } else {
            webView.evaluateJavascript("updateTargetAndRoute(0.0, 0.0, '')", null)
        }
    }

    LaunchedEffect(Unit) {
        val markersJson = machines.map { m ->
            """
            {
                name: "${m.name.replace("\"", "\\\"")}",
                branch: "${m.branch.replace("\"", "\\\"")}",
                merchantId: "${m.merchantId}",
                lat: ${m.latitude},
                lng: ${m.longitude}
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
                    body { margin: 0; padding: 0; }
                    #map { width: 100vw; height: 100vh; }
                    .user-position-container {
                        position: relative;
                        width: 40px;
                        height: 40px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    .pulsing-blue-marker-dot {
                        width: 14px;
                        height: 14px;
                        border-radius: 50%;
                        background: #3B82F6;
                        border: 3px solid white;
                        box-shadow: 0 0 10px rgba(59, 130, 246, 0.8), 0 0 0 4px rgba(59, 130, 246, 0.3);
                        z-index: 10;
                    }
                    .direction-cone {
                        position: absolute;
                        width: 0;
                        height: 0;
                        border-left: 12px solid transparent;
                        border-right: 12px solid transparent;
                        border-bottom: 30px solid rgba(59, 130, 246, 0.4);
                        border-radius: 50% 50% 0 0;
                        transform-origin: 50% 100%;
                        transform: translateY(-13px) rotate(45deg);
                        pointer-events: none;
                        z-index: 5;
                    }
                    .machine-target-marker {
                        width: 26px;
                        height: 26px;
                        background: white;
                        border: 3.5px solid #111;
                        border-radius: 50%;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        box-shadow: 0 3px 6px rgba(0,0,0,0.3);
                        cursor: pointer;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map', { zoomControl: false }).setView([25.2926, 51.5235], 13);
                    L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                        maxZoom: 19,
                        attribution: '&copy; OpenStreetMap'
                    }).addTo(map);

                    var userMarker = null;
                    var activeTarget = null;
                    var routeLine = null;
                    var machinesData = [$markersJson];

                    machinesData.forEach(function(m) {
                        var flagIconHtml = '<div class="machine-target-marker"><svg width="12" height="12" viewBox="0 0 24 24" fill="black"><path d="M14 6l-.4-2H5v17h2v-7h5.6l.4 2h7V6h-5.6z"/></svg></div>';
                        var customIcon = L.divIcon({
                            html: flagIconHtml,
                            className: 'custom-flag-marker',
                            iconSize: [26, 26],
                            iconAnchor: [13, 13]
                        });
                        var marker = L.marker([m.lat, m.lng], { icon: customIcon }).addTo(map);
                        marker.on('click', function() {
                            if (window.AndroidApp) {
                                window.AndroidApp.onMachineClicked(m.merchantId);
                            }
                        });
                    });

                    function updateUserLocation(lat, lng) {
                        var latLng = [lat, lng];
                        if (!userMarker) {
                            var blueIcon = L.divIcon({
                                className: 'user-position-containerIcon',
                                html: '<div class="user-position-container"><div class="direction-cone"></div><div class="pulsing-blue-marker-dot"></div></div>',
                                iconSize: [40, 40],
                                iconAnchor: [20, 20]
                            });
                            userMarker = L.marker(latLng, { icon: blueIcon }).addTo(map);
                        } else {
                            userMarker.setLatLng(latLng);
                        }
                        map.panTo(latLng);
                        recomputeRouting();
                    }

                    function updateTargetAndRoute(lat, lng, merchantId) {
                        if (lat === 0.0 && lng === 0.0) {
                            if (routeLine) { map.removeLayer(routeLine); routeLine = null; }
                            activeTarget = null;
                            return;
                        }
                        activeTarget = [lat, lng];
                        recomputeRouting();
                    }

                    function recomputeRouting() {
                        if (routeLine) { map.removeLayer(routeLine); routeLine = null; }
                        if (userMarker && activeTarget) {
                            routeLine = L.polyline([userMarker.getLatLng(), activeTarget], {
                                color: '#FF5722',
                                weight: 6,
                                opacity: 0.85,
                                dashArray: '8, 12'
                            }).addTo(map);
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://localhost", html, "text/html", "UTF-8", null)
    }

    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
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
