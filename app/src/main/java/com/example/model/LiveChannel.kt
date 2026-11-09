package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.Date

/**
 * 抖音直播间数据模型 (Room Entity)
 * 包含 id, roomRid, roomName, roomTitle, avatarUrl, isLive 等字段，并配置 Room 类型转换器
 */
@Entity(tableName = "channel_models")
@TypeConverters(LiveChannelConverters::class)
data class LiveChannel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val roomRid: String,
    val roomName: String,
    val roomTitle: String = "",
    val avatarUrl: String = "",
    val isLive: Boolean = false,
    val statusKnown: Boolean = true,
    val isOnce: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
    val lastChecked: Date? = null
)

/**
 * Room 数据库类型转换器
 * 提供 Date 与 List<String> 等复杂类型的序列化与反序列化支持
 */
class LiveChannelConverters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(";;").map { it.trim() }.filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun toStringList(list: List<String>?): String {
        return list?.filter { it.isNotBlank() }?.joinToString(";;") ?: ""
    }
}
