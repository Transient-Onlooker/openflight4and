package com.example.openflight4and.data

import android.content.Context
import com.example.openflight4and.model.Airport
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.serialization.json.Json

class AirportRepository(
    private val context: Context,
    private val reportDataError: (String, Throwable) -> Unit
) {
    fun getAirports(): List<Airport> {
        return try {
            val inputStream = context.assets.open("airports.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }
            Json.decodeFromString<List<Airport>>(jsonString)
        } catch (e: Exception) {
            reportDataError("Failed to load airports.json", e)
            emptyList()
        }
    }
}
