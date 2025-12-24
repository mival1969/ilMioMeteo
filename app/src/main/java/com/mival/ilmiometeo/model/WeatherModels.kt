package com.mival.ilmiometeo.model

data class WeatherItem(
    val day: String,        // e.g. "Lun 25"
    val date: String,       // e.g. "25 Dicembre"
    val iconUrl: String,    // e.g. https://...
    val weatherCode: Int = 0, // NEW: For sprite mapping
    val minTemp: String,    // e.g. 5°C
    val maxTemp: String,    // e.g. 12°C
    val description: String,// e.g. "Pioggia debole"
    val link: String = ""   // Detailed page link, e.g. "/meteo/Pinerolo/domani" or just "domani"
)

data class HourlyItem(
    val time: String,       // e.g. "14:00"
    val iconUrl: String,
    val weatherCode: Int,   // New field for sprite mapping
    val temp: String,       // "8°"
    val rain: String,       // "deboli" or "1 mm"
    val rainType: String,   // "neve", "pioggia", "none"
    val wind: String,       // "W 5 km/h"
    val snowLevel: String,  // "Quota 0°C"
    val airQuality: String, // "Aria"
    val visibility: String, // "<10km"
    val humidity: String,   // "80%"
    val humidityLabel: String = "UR%"
)
