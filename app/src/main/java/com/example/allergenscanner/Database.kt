package com.example.allergenscanner

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 6. This is the new database file.
 * It defines the ScanHistoryItem table, the DAO, and the Database class.
 */

// --- 1. The Data Entity (Table) ---
@Entity(tableName = "scan_history")
data class ScanHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val barcode: String,
    val productName: String,
    val scanTime: Long = System.currentTimeMillis(),
    val scanResult: String, // "SAFE" or "UNSAFE"
    val conflictingAllergens: String // Comma-separated list
)

// --- 2. The Data Access Object (DAO) ---
@Dao
interface ScanHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScanHistoryItem)

    @Query("SELECT * FROM scan_history ORDER BY scanTime DESC")
    fun getAllHistory(): Flow<List<ScanHistoryItem>>

    @Query("DELETE FROM scan_history")
    suspend fun clearAll()
}

// --- 3. The Database Class ---
@Database(entities = [ScanHistoryItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        // Singleton prevents multiple instances of database opening at the same time.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "allergen_scanner_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}