package com.example.model

data class CODMachine(
    val name: String,
    val branch: String,
    val mapUrl: String,
    val latitude: Double,
    val longitude: Double
)

data class LocationPreset(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val description: String
)
