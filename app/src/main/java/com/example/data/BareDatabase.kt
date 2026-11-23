package com.example.data

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

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val category: String,
    val isGamingPortal: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "site_gaming_profiles")
data class SiteGamingProfileEntity(
    @PrimaryKey val host: String,
    val autoGamingMode: Boolean = true,
    val renderScalePercent: Int = 85,
    val forceWebGlHighPerf: Boolean = true,
    val disableAntialias: Boolean = false,
    val aggressiveShield: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY isGamingPortal DESC, createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun count(): Int

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(bookmarks: List<BookmarkEntity>)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}

@Dao
interface SiteProfileDao {
    @Query("SELECT * FROM site_gaming_profiles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SiteGamingProfileEntity>>

    @Query("SELECT * FROM site_gaming_profiles WHERE host = :host LIMIT 1")
    suspend fun getByHost(host: String): SiteGamingProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: SiteGamingProfileEntity)

    @Query("DELETE FROM site_gaming_profiles WHERE host = :host")
    suspend fun deleteByHost(host: String)
}

@Database(
    entities = [BookmarkEntity::class, SiteGamingProfileEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BareDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun siteProfileDao(): SiteProfileDao

    companion object {
        @Volatile
        private var instance: BareDatabase? = null

        fun getInstance(context: Context): BareDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BareDatabase::class.java,
                    "bare_turbo_local.db"
                ).build().also { instance = it }
            }
        }
    }
}
