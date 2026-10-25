package com.reelshield.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: DetectionEvent): Long

    @Query("SELECT * FROM detection_events ORDER BY timestamp DESC LIMIT 50")
    fun getRecentEvents(): Flow<List<DetectionEvent>>

    @Query("SELECT COUNT(*) FROM detection_events WHERE targetPackage = :packageName")
    fun getCountForPackage(packageName: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM detection_events")
    fun getTotalCount(): Flow<Int>

    @Query("DELETE FROM detection_events")
    suspend fun clearAll()
}
