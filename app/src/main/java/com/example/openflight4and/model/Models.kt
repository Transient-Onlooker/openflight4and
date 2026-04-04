package com.example.openflight4and.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.android.gms.maps.model.LatLng
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.Locale

@Serializable
data class Airport(
    val iata: String,
    val nameKo: String,
    val nameEn: String,
    val cityKo: String,
    val cityEn: String,
    val country: String,
    val latitude: Double,
    val longitude: Double
) {
    @Transient
    val location: LatLng = LatLng(latitude, longitude)

    // UI 표시용 헬퍼
    fun localizedName(languageTag: String = Locale.getDefault().toLanguageTag()): String {
        return if (languageTag.lowercase().startsWith("ko")) nameKo else nameEn.ifBlank { nameKo }
    }

    fun localizedCity(languageTag: String = Locale.getDefault().toLanguageTag()): String {
        return if (languageTag.lowercase().startsWith("ko")) cityKo else cityEn.ifBlank { cityKo }
    }

    val displayName: String get() = localizedName()
    val displayCity: String get() = localizedCity()
}

// Room Database Entity for Flight Logs
@Entity(tableName = "flight_sessions")
data class FlightSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val flightNumber: String,
    val originIata: String,
    val originName: String,
    val destinationIata: String,
    val destinationName: String,
    val seatNumber: String?,
    val focusCategory: String?,
    val distanceKm: Int,
    val durationMinutes: Int,
    val startTime: Long,
    val endTime: Long,
    val isCompleted: Boolean,
    val notes: String? = null
)

// UI 에서 사용하는 임시 상태 홀더 (Draft)
data class FlightDraft(
    val origin: Airport,
    val destination: Airport? = null,
    val distanceKm: Int = 0,
    val estimatedMinutes: Int = 0,
    val flightNumber: String = generateFlightNumber(),
    val boardingTime: String = "",
    val seatNumber: String? = null,
    val focusCategory: String? = null,
    val status: String = "Scheduled",
    val timeScale: Float = 1f
)

fun generateFlightNumber(): String {
    val prefix = listOf("OF", "FF", "FL").random()
    val number = (100..999).random()
    return "$prefix$number"
}
