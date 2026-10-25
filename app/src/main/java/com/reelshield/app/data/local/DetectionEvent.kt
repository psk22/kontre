package com.reelshield.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "detection_events")
data class DetectionEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val targetPackage: String,
    val targetPlatform: String,
    val eventType: String,
    val actionTaken: String
)
