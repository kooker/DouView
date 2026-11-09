package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 抖音直播间数据实体模型 (Room Entity)
 * 用于在本地 SQLite 数据库中持久化存储用户添加或关注的直播间
 */
@Entity(tableName = "live_channels")
data class LiveChannel(
    @PrimaryKey
    val rid: String,
    val name: String,
    val title: String,
    val avatar: String,
    val isLive: Boolean = false,
    val statusKnown: Boolean = true,
    val isOnce: Boolean = false, // 是否为单场临时直播间（短链接/口令/15位以上临时房间号）
    val addedAt: Long = System.currentTimeMillis()
)

// 兼容别名
typealias ChannelEntity = LiveChannel
