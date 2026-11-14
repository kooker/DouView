package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM live_channels ORDER BY (CASE WHEN isLive = 1 THEN 0 WHEN statusKnown = 0 THEN 1 ELSE 2 END) ASC, addedAt ASC")
    fun getAllChannelsFlow(): Flow<List<LiveChannel>>

    @Query("SELECT * FROM live_channels ORDER BY addedAt ASC")
    suspend fun getAllChannels(): List<LiveChannel>

    @Query("SELECT * FROM live_channels WHERE rid = :rid LIMIT 1")
    suspend fun getChannelByRid(rid: String): LiveChannel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(channel: LiveChannel)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<LiveChannel>)

    @Delete
    suspend fun delete(channel: LiveChannel)

    @Query("DELETE FROM live_channels WHERE rid = :rid")
    suspend fun deleteByRid(rid: String)

    @Query("DELETE FROM live_channels WHERE isOnce = 1 AND isLive = 0 AND statusKnown = 1")
    suspend fun clearOfflineOnceChannels()

    @Query("DELETE FROM live_channels")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(channels: List<LiveChannel>) {
        deleteAll()
        if (channels.isNotEmpty()) {
            insertAll(channels)
        }
    }
}
