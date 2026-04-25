package com.fileleader.data.db

import androidx.room.*
import com.fileleader.data.model.ScanHistoryEntity
import com.fileleader.data.model.TrashEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT 30")
    fun getHistory(): Flow<List<ScanHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ScanHistoryEntity)

    @Query("DELETE FROM scan_history WHERE timestamp < :before")
    suspend fun deleteBefore(before: Long)
}

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash_entries ORDER BY deletedAt DESC")
    fun getAll(): Flow<List<TrashEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TrashEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<TrashEntry>)

    @Delete
    suspend fun delete(entry: TrashEntry)

    @Query("DELETE FROM trash_entries WHERE deletedAt < :before")
    suspend fun deleteExpired(before: Long)

    @Query("SELECT SUM(size) FROM trash_entries")
    suspend fun getTotalSize(): Long?

    @Query("SELECT COUNT(*) FROM trash_entries")
    suspend fun getCount(): Int
}

@Database(
    entities = [ScanHistoryEntity::class, TrashEntry::class],
    version = 1,
    exportSchema = false
)
abstract class FileLeaderDatabase : RoomDatabase() {
    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun trashDao(): TrashDao
}
