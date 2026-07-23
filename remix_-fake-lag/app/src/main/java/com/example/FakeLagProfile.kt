package com.example

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import android.content.Context

@Entity(tableName = "fakelag_profiles")
data class FakeLagProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val simulatedPing: Int,
    val freezeDropRate: Int,
    val freezeMinSize: Int,
    val freezeMaxSize: Int,
    val ghostReplayCount: Int,
    val ghostBlockThreshold: Int,
    val posUpdateInterval: Int,
    val teleportReleaseWindow: Int,
    val telekillAutoDelay: Float,
    val bwBlockUpload: Boolean,
    val bwBlockDownload: Boolean,
    val bwUploadLimit: Int,
    val bwDownloadLimit: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface FakeLagProfileDao {
    @Query("SELECT * FROM fakelag_profiles ORDER BY timestamp DESC")
    fun getAllProfiles(): Flow<List<FakeLagProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: FakeLagProfile)

    @Delete
    suspend fun deleteProfile(profile: FakeLagProfile)

    @Query("SELECT * FROM fakelag_profiles WHERE id = :id")
    suspend fun getProfileById(id: Int): FakeLagProfile?
}

@Database(entities = [FakeLagProfile::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): FakeLagProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fakelag_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class FakeLagProfileRepository(private val profileDao: FakeLagProfileDao) {
    val allProfiles: Flow<List<FakeLagProfile>> = profileDao.getAllProfiles()

    suspend fun insert(profile: FakeLagProfile) {
        profileDao.insertProfile(profile)
    }

    suspend fun delete(profile: FakeLagProfile) {
        profileDao.deleteProfile(profile)
    }
}
