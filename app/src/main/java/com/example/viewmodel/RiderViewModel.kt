package com.example.viewmodel

import androidx.lifecycle.ViewModel
import com.example.model.CODMachine
import com.example.model.LocationPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlin.math.*

enum class WarningLevel(val message: String, val colorHex: Long) {
    SAFE("Safe Balance", 0xFF10B981),
    WARNING("Approaching COD Cash Limit", 0xFFF59E0B),
    CRITICAL("CRITICAL LIMIT REACHED!", 0xFFEF4444)
}

data class RiderState(
    val riderType: String = "Bike", // "Bike" or "Car"
    val currentCOD: Double = 0.0,
    val simulatedLatitude: Double = 25.2926, // Starting at Doha Corniche
    val simulatedLongitude: Double = 51.5235,
    val isRealGpsActive: Boolean = false,
    val nearestMachine: Pair<CODMachine, Double>? = null,
    val machinesWithDistance: List<Pair<CODMachine, Double>> = emptyList(),
    // Custom premium enhancements requested by the user
    val isDarkMode: Boolean = true,
    val isLoggedIn: Boolean = false,
    val loggedInUser: String = "",
    val loggedInPhone: String = "",
    val activeOtpCode: String? = null,
    
    // Talabat Genuine Rider state items
    val isOnline: Boolean = true,
    val selectedMachine: CODMachine? = null,
    val navigationType: String = "External", // "InApp" or "External"
    val avoidHighways: Boolean = false,
    val avoidTolls: Boolean = false,
    val mapboxMetrics: Boolean = true,
    val biometricSignIn: Boolean = true,
    val chatLanguage: String = "English"
)

class RiderViewModel : ViewModel() {

    private val _state = MutableStateFlow(RiderState())
    val state: StateFlow<RiderState> = _state.asStateFlow()

    val machines = com.example.model.CODMachineRepository.getMachines()

    val locationPresets = listOf(
        LocationPreset("Doha Corniche (Center)", 25.2926, 51.5235, "Central Doha"),
        LocationPreset("Bin Omran (Near KeyBS)", 25.2840, 51.5300, "Central Doha"),
        LocationPreset("Al Rayyan (Near Fresh Way)", 25.2900, 51.4230, "West District"),
        LocationPreset("Al Wakra (Near Madeena)", 25.1730, 51.6020, "South Port City"),
        LocationPreset("Lusail Marina (Near Bathool)", 25.4260, 51.5320, "North City"),
        LocationPreset("Hamad International Airport", 25.2608, 51.6138, "East Coast Airport")
    )

    init {
        recalculateDistances()
    }

    fun toggleTheme() {
        _state.value = _state.value.copy(isDarkMode = !_state.value.isDarkMode)
    }

    fun startPhoneVerification(phone: String): String {
        // Generate a random stable 4-digit simulation OTP e.g. "4826"
        val seed = phone.filter { it.isDigit() }.takeLast(4)
        val otp = if (seed.length == 4) seed.reversed() else "7492"
        _state.value = _state.value.copy(activeOtpCode = otp)
        return otp
    }

    fun completePhoneLogin(phone: String, userName: String) {
        _state.value = _state.value.copy(
            isLoggedIn = true,
            loggedInPhone = phone,
            loggedInUser = if (userName.isBlank()) "QAR Rider $phone" else userName,
            activeOtpCode = null
        )
    }

    fun completeGoogleLogin(email: String, name: String) {
        _state.value = _state.value.copy(
            isLoggedIn = true,
            loggedInUser = name,
            loggedInPhone = email // Google email placed in secondary descriptor
        )
    }

    fun logout() {
        _state.value = _state.value.copy(
            isLoggedIn = false,
            loggedInUser = "",
            loggedInPhone = "",
            activeOtpCode = null
        )
    }

    fun updateRiderType(type: String) {
        _state.value = _state.value.copy(riderType = type)
    }

    fun updateCOD(value: Double) {
        val sanitized = if (value.isNaN() || value < 0) 0.0 else value
        _state.value = _state.value.copy(currentCOD = sanitized)
    }

    fun incrementCOD(amount: Double) {
        val current = _state.value.currentCOD
        updateCOD(current + amount)
    }

    fun clearCOD() {
        updateCOD(0.0)
    }

    fun updateLocation(latitude: Double, longitude: Double, isRealGps: Boolean) {
        _state.value = _state.value.copy(
            simulatedLatitude = latitude,
            simulatedLongitude = longitude,
            isRealGpsActive = isRealGps
        )
        recalculateDistances()
    }

    fun toggleOnlineStatus() {
        _state.value = _state.value.copy(isOnline = !_state.value.isOnline)
    }

    fun selectMachine(machine: CODMachine?) {
        _state.value = _state.value.copy(selectedMachine = machine)
    }

    fun updateNavigationType(type: String) {
        _state.value = _state.value.copy(navigationType = type)
    }

    fun toggleAvoidHighways() {
        _state.value = _state.value.copy(avoidHighways = !_state.value.avoidHighways)
    }

    fun toggleAvoidTolls() {
        _state.value = _state.value.copy(avoidTolls = !_state.value.avoidTolls)
    }

    fun toggleMapboxMetrics() {
        _state.value = _state.value.copy(mapboxMetrics = !_state.value.mapboxMetrics)
    }

    fun toggleBiometricSignIn() {
        _state.value = _state.value.copy(biometricSignIn = !_state.value.biometricSignIn)
    }

    fun updateChatLanguage(lang: String) {
        _state.value = _state.value.copy(chatLanguage = lang)
    }

    private fun recalculateDistances() {
        val currentLat = _state.value.simulatedLatitude
        val currentLng = _state.value.simulatedLongitude

        val mapped = machines.map { machine ->
            val dist = calculateDistance(currentLat, currentLng, machine.latitude, machine.longitude)
            machine to dist
        }.sortedBy { it.second }

        val nearest = mapped.firstOrNull()

        _state.value = _state.value.copy(
            nearestMachine = nearest,
            machinesWithDistance = mapped
        )
    }

    // Haversine formula to compute distance in km between two lat/lng coordinates
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun getLimitForType(type: String): Double {
        return if (type == "Bike") 900.0 else 2300.0
    }

    fun getWarningLevel(cod: Double, type: String): WarningLevel {
        val limit = getLimitForType(type)
        return if (type == "Bike") {
            when {
                cod >= 850.0 -> WarningLevel.CRITICAL
                cod >= 700.0 -> WarningLevel.WARNING
                else -> WarningLevel.SAFE
            }
        } else {
            when {
                cod >= 2200.0 -> WarningLevel.CRITICAL
                cod >= 1800.0 -> WarningLevel.WARNING
                else -> WarningLevel.SAFE
            }
        }
    }
}
