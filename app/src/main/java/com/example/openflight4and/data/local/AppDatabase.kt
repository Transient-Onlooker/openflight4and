package com.example.openflight4and.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.openflight4and.model.FlightSession
import kotlinx.coroutines.flow.Flow

@Dao
interface FlightSessionDao {
    @Insert
    suspend fun insertSession(session: FlightSession)

    @Query("DELETE FROM flight_sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT * FROM flight_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<FlightSession>>

    @Query("SELECT * FROM flight_sessions WHERE isCompleted = 1 ORDER BY startTime DESC LIMIT 5")
    fun getRecentCompletedSessions(): Flow<List<FlightSession>>

    @Query("SELECT COUNT(*) FROM flight_sessions WHERE isCompleted = 1")
    fun getTotalFlightsCount(): Flow<Int>

    @Query("SELECT SUM(distanceKm) FROM flight_sessions WHERE isCompleted = 1")
    fun getTotalDistance(): Flow<Int?> // Can be null if empty

    @Query("SELECT SUM(durationMinutes) FROM flight_sessions WHERE isCompleted = 1")
    fun getTotalFocusMinutes(): Flow<Int?>
}

@Database(entities = [FlightSession::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun flightSessionDao(): FlightSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "openflight_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }

        fun resetForTests() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
