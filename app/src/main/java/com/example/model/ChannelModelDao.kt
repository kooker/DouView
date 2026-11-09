package com.example.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 针对 com.example.model.LiveChannel 实体的持久化增删改查 DAO 接口
 */
@Dao
interface ChannelModelDao {

    @Query("SELECT * FROM channel_models ORDER BY (CASE WHEN isLive = 1 THEN 0 WHEN statusKnown = 0 THEN 1 ELSE 2 END) ASC, addedAt DESC")
    fun getAllFlow(): Flow<List<LiveChannel>>

    @Query("SELECT * FROM channel_models")
    suspend fun getAll(): List<LiveChannel>

    @Query("SELECT * FROM channel_models WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): LiveChannel?

    @Query("SELECT * FROM channel_models WHERE roomRid = :roomRid LIMIT 1")
    suspend fun getByRoomRid(roomRid: String): LiveChannel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(channel: LiveChannel): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<LiveChannel>)

    @Update
    suspend fun update(channel: LiveChannel)

    @Delete
    suspend fun delete(channel: LiveChannel)

    @Query("DELETE FROM channel_models WHERE roomRid = :roomRid")
    suspend fun deleteByRoomRid(roomRid: String)

    @Query("DELETE FROM channel_models WHERE isOnce = 1 AND isLive = 0 AND statusKnown = 1")
    suspend fun clearOfflineOnce()

    @Query("DELETE FROM channel_models")
    suspend fun deleteAll()
}
