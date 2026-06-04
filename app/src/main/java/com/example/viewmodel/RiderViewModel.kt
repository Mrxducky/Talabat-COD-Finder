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
    val machinesWithDistance: List<Pair<CODMachine, Double>> = emptyList()
)

class RiderViewModel : ViewModel() {

    private val _state = MutableStateFlow(RiderState())
    val state: StateFlow<RiderState> = _state.asStateFlow()

    val machines = listOf(
        CODMachine(
            name = "KeyBS Merchant",
            branch = "Bin Omran",
            mapUrl = "https://maps.app.goo.gl/r7JNNza8Vt18HLtJ9",
            latitude = 25.2854,
            longitude = 51.5310
        ),
        CODMachine(
            name = "Fresh Way Supermarket",
            branch = "Al Rayyan",
            mapUrl = "https://maps.app.goo.gl/kA3MRiBFLtp4pq3T6",
            latitude = 25.2910,
            longitude = 51.4244
        ),
        CODMachine(
            name = "Madeena Hypermarket",
            branch = "Wakra",
            mapUrl = "https://goo.gl/maps/v5aaGiHGVVuCjuL38",
            latitude = 25.1715,
            longitude = 51.6034
        ),
        CODMachine(
            name = "Bathool Supermarket",
            branch = "Lusail",
            mapUrl = "https://maps.app.goo.gl/PubPTcHtkb8EBeoN7",
            latitude = 25.4245,
            longitude = 51.5330
        )
    )

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
