package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChannelRepository(private val channelDao: ChannelDao) {

    val allChannels: Flow<List<LiveChannel>> = channelDao.getAllChannelsFlow()

    companion object {
        fun getDefaultChannels(): List<LiveChannel> = listOf(
            LiveChannel(
                rid = "127453393722",
                name = "央视网快看",
                avatar = "https://p11.douyinpic.com/aweme/100x100/aweme-avatar/tos-cn-avt-0015_9f9763d9e7dee65a1aab0367eb18fbf7.jpeg?from=3067671334",
                title = "总台央视新闻频道正在播出",
                isLive = true,
                statusKnown = true,
                isOnce = false,
                addedAt = 1000L
            ),
            LiveChannel(
                rid = "232551178364",
                name = "电影频道云影院",
                avatar = "https://p11.douyinpic.com/aweme/100x100/aweme-avatar/tos-cn-i-0813c001_oUFo1iCeNB1CQEAdbgAGABo1EfLE6nAiVpIArA.jpeg?from=3067671334",
                title = "直播丨𝐂𝐂𝐓𝐕𝟔电影频道🎬",
                isLive = true,
                statusKnown = true,
                isOnce = false,
                addedAt = 2000L
            ),
            LiveChannel(
                rid = "50828500437",
                name = "央视频",
                avatar = "https://p3.douyinpic.com/aweme/100x100/aweme-avatar/douyin-user-private-image_d3bd1f1f37aab2648c3983977878aa84_1129948451452958.jpeg?from=3067671334",
                title = "新闻正在直播",
                isLive = true,
                statusKnown = true,
                isOnce = false,
                addedAt = 3000L
            ),
            LiveChannel(
                rid = "662843050230",
                name = "央视文艺",
                avatar = "https://p3.douyinpic.com/aweme/100x100/aweme-avatar/tos-cn-avt-0015_5ccbed59fefa7532c128978954f8ee1c.jpeg?from=3067671334",
                title = "金秋综艺特辑",
                isLive = false,
                statusKnown = true,
                isOnce = false,
                addedAt = 4000L
            ),
            LiveChannel(
                rid = "971054279946",
                name = "央视网美学",
                avatar = "https://p11.douyinpic.com/aweme/100x100/aweme-avatar/tos-cn-avt-0015_38ef7871eadde681c4ebd394980e96be.jpeg?from=3067671334",
                title = "央视纪录片频道直播",
                isLive = false,
                statusKnown = true,
                isOnce = false,
                addedAt = 5000L
            ),
            LiveChannel(
                rid = "296728101980",
                name = "央视网财经",
                avatar = "https://p3.douyinpic.com/aweme/100x100/aweme-avatar/douyin-user-private-image_720916559455351_3f464b16c37ad87ab87bed82636eb7f45a2e4c32c374bbf5b78cf69c95453bab.jpeg?from=3067671334",
                title = "央视财经频道节目直播",
                isLive = false,
                statusKnown = true,
                isOnce = false,
                addedAt = 6000L
            ),
            LiveChannel(
                rid = "783811579858",
                name = "央视三农",
                title = "CCTV-17节目真好看！",
                avatar = "https://p11.douyinpic.com/aweme/100x100/aweme-avatar/tos-cn-avt-0015_e09edb984889a1774ab8c954991ba986.jpeg?from=3067671334",
                isLive = true,
                statusKnown = true,
                isOnce = false,
                addedAt = 7000L
            ),
            LiveChannel(
                rid = "193671986973",
                name = "安静听歌（24小时听歌）",
                avatar = "https://p11.douyinpic.com/aweme/100x100/aweme-avatar/tos-cn-avt-0015_168266fd62fc19e0f60b1dffea96e55a.jpeg?from=3067671334",
                title = "热门流行伤感经典好听伤感怀旧音乐老歌1000首！",
                isLive = true,
                statusKnown = true,
                isOnce = false,
                addedAt = 8000L
            )
        )
    }

    suspend fun checkAndInitializeDefaults() = withContext(Dispatchers.IO) {
        val existing = channelDao.getAllChannels()
        if (existing.isEmpty()) {
            channelDao.insertAll(getDefaultChannels())
        } else {
            val existingMap = existing.associateBy { it.rid }
            val defaults = getDefaultChannels()
            val toUpdate = mutableListOf<LiveChannel>()
            val toAdd = mutableListOf<LiveChannel>()

            for (defaultChannel in defaults) {
                val current = existingMap[defaultChannel.rid]
                if (current == null) {
                    toAdd.add(defaultChannel)
                } else if (current.addedAt != defaultChannel.addedAt && current.addedAt < 10000L) {
                    // 修正历史版本相同 addedAt 时间戳，对齐网页版固定默认排序
                    toUpdate.add(current.copy(addedAt = defaultChannel.addedAt))
                }
            }
            if (toAdd.isNotEmpty()) {
                channelDao.insertAll(toAdd)
            }
            if (toUpdate.isNotEmpty()) {
                channelDao.insertAll(toUpdate)
            }
        }
    }

    suspend fun insertOrUpdate(channel: LiveChannel) = withContext(Dispatchers.IO) {
        channelDao.insertOrUpdate(channel)
    }

    suspend fun insertAll(channels: List<LiveChannel>) = withContext(Dispatchers.IO) {
        channelDao.insertAll(channels)
    }

    suspend fun delete(channel: LiveChannel) = withContext(Dispatchers.IO) {
        channelDao.delete(channel)
    }

    suspend fun deleteByRid(rid: String) = withContext(Dispatchers.IO) {
        channelDao.deleteByRid(rid)
    }

    suspend fun clearOfflineOnceChannels() = withContext(Dispatchers.IO) {
        channelDao.clearOfflineOnceChannels()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        channelDao.deleteAll()
    }

    suspend fun resetDefaults() = withContext(Dispatchers.IO) {
        channelDao.deleteAll()
        channelDao.insertAll(getDefaultChannels())
    }

    suspend fun getAllChannels(): List<LiveChannel> = withContext(Dispatchers.IO) {
        channelDao.getAllChannels()
    }
}
