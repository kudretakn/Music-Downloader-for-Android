package com.example.ytmusicdownloader.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "download_history")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val format: String, // "MP3" or "MP4"
    val filePath: String,
    val date: Long = System.currentTimeMillis()
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_history ORDER BY date DESC")
    fun getAll(): Flow<List<DownloadItem>>

    @Insert
    suspend fun insert(item: DownloadItem)

    @Delete
    suspend fun delete(item: DownloadItem)
    
    @Query("DELETE FROM download_history")
    suspend fun clearAll()
}

@Database(entities = [DownloadItem::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yt_downloader_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
