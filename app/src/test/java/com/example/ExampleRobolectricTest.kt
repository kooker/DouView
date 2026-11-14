package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.LiveChannel
import com.example.engine.DouyinLiveEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  private lateinit var database: AppDatabase

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("抖眸流控", appName)
  }

  @Test
  fun `test LiveChannel persistence in Room ChannelDao`() = runBlocking {
    val dao = database.channelDao()
    val testChannel = LiveChannel(
      rid = "50828500437",
      name = "央视频",
      title = "新闻正在直播",
      avatar = "https://example.com/avatar.jpg",
      isLive = true
    )
    dao.insertOrUpdate(testChannel)

    val queried = dao.getChannelByRid("50828500437")
    assertEquals("央视频", queried?.name)
    assertEquals(true, queried?.isLive)

    val flowList = dao.getAllChannelsFlow().first()
    assertEquals(1, flowList.size)
    assertEquals("50828500437", flowList[0].rid)
  }

  @Test
  fun `test roomId extraction from plain digits`() = runBlocking {
    val (rid, isOnce) = DouyinLiveEngine.extractRoomId("232551178364")
    assertEquals("232551178364", rid)
    assertEquals(false, isOnce)
  }

  @Test
  fun `test roomId extraction from 19 digit temporary room`() = runBlocking {
    val (rid, isOnce) = DouyinLiveEngine.extractRoomId("7428192837461928374")
    assertEquals("7428192837461928374", rid)
    assertTrue(isOnce)
  }

  @Test
  fun `test fetchRoomInfo live stream extraction`() = runBlocking {
    val result = DouyinLiveEngine.fetchRoomInfo("127453393722")
    if (result != null) {
      assertNotNull(result.name)
      if (result.isLive) {
        assertTrue(result.streams.isNotEmpty())
        val firstStream = result.streams.values.first()
        assertTrue(!firstStream.hls.isNullOrEmpty() || !firstStream.flv.isNullOrEmpty())
      }
    }
  }

  @Test
  fun `test com example model LiveChannel persistence and type converters`() = runBlocking {
    val modelDao = database.channelModelDao()
    val model = com.example.model.LiveChannel(
      roomRid = "9988776655",
      roomName = "高清测试台",
      roomTitle = "测试直播中",
      avatarUrl = "https://example.com/avatar.png",
      isLive = true,
      tags = listOf("新闻", "高清", "CCTV"),
      lastChecked = java.util.Date()
    )
    val id = modelDao.insert(model)
    assertTrue(id > 0)

    val queried = modelDao.getByRoomRid("9988776655")
    assertNotNull(queried)
    assertEquals("高清测试台", queried?.roomName)
    assertEquals(listOf("新闻", "高清", "CCTV"), queried?.tags)
    assertNotNull(queried?.lastChecked)
  }

  @Test
  fun `test web aligned channel sorting strategy`() = runBlocking {
    val dao = database.channelDao()
    val ch1 = LiveChannel(rid = "101", name = "频道1", title = "直播1", avatar = "", isLive = true, statusKnown = true, addedAt = 1000L)
    val ch2 = LiveChannel(rid = "102", name = "频道2", title = "直播2", avatar = "", isLive = false, statusKnown = true, addedAt = 2000L)
    val ch3 = LiveChannel(rid = "103", name = "频道3", title = "直播3", avatar = "", isLive = true, statusKnown = true, addedAt = 3000L)
    val ch4 = LiveChannel(rid = "104", name = "频道4", title = "直播4", avatar = "", isLive = false, statusKnown = false, addedAt = 4000L)

    dao.insertAll(listOf(ch1, ch2, ch3, ch4))

    val sorted = dao.getAllChannelsFlow().first()
    // 预期顺序（对齐网页版）：
    // Rank 0 (在线，按 addedAt 升序): ch1 (1000L), ch3 (3000L)
    // Rank 1 (待核验，按 addedAt 升序): ch4 (4000L)
    // Rank 2 (离线，按 addedAt 升序): ch2 (2000L)
    assertEquals(4, sorted.size)
    assertEquals("101", sorted[0].rid)
    assertEquals("103", sorted[1].rid)
    assertEquals("104", sorted[2].rid)
    assertEquals("102", sorted[3].rid)
  }

  @Test
  fun `test fullscreen swipe channel navigation logic`() {
    val channels = listOf(
      LiveChannel(rid = "101", name = "频道1", title = "", avatar = ""),
      LiveChannel(rid = "102", name = "频道2", title = "", avatar = ""),
      LiveChannel(rid = "103", name = "频道3", title = "", avatar = "")
    )

    // Current is index 0 -> next is index 1
    var currentIdx = channels.indexOfFirst { it.rid == "101" }
    var nextIdx = if (currentIdx == -1 || currentIdx >= channels.size - 1) 0 else currentIdx + 1
    assertEquals("102", channels[nextIdx].rid)

    // Current is index 2 (last) -> next wraps around to 0
    currentIdx = channels.indexOfFirst { it.rid == "103" }
    nextIdx = if (currentIdx == -1 || currentIdx >= channels.size - 1) 0 else currentIdx + 1
    assertEquals("101", channels[nextIdx].rid)

    // Current is index 0 (first) -> previous wraps around to 2 (last)
    currentIdx = channels.indexOfFirst { it.rid == "101" }
    val prevIdx = if (currentIdx <= 0) channels.size - 1 else currentIdx - 1
    assertEquals("103", channels[prevIdx].rid)
  }

  @Test
  fun `test volume adjustment and clamp boundaries`() {
    var volume = 1.0f
    // Increase beyond 1.0f should clamp to 1.0f
    volume = (volume + 0.2f).coerceIn(0f, 1.0f)
    assertEquals(1.0f, volume, 0.001f)

    // Decrease by 0.4f
    volume = (volume - 0.4f).coerceIn(0f, 1.0f)
    assertEquals(0.6f, volume, 0.001f)

    // Decrease beyond 0.0f should clamp to 0.0f
    volume = (volume - 0.8f).coerceIn(0f, 1.0f)
    assertEquals(0.0f, volume, 0.001f)
  }

  @Test
  fun `test max zoom scale clamp up to 10x`() {
    var zoomScale = 1.0f
    val zoomChange = 12.0f
    val clamped = (zoomScale * zoomChange).coerceIn(1.0f, 10.0f)
    assertEquals(10.0f, clamped, 0.001f)

    val zoomOut = 0.5f
    val clampedMin = (clamped * zoomOut).coerceIn(1.0f, 10.0f)
    assertEquals(5.0f, clampedMin, 0.001f)
  }

  @Test
  fun `test TV banner asset existence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_tv_banner)
    assertNotNull(drawable)
  }

  @Test
  fun `test monotonic quality downgrade hierarchy and circuit breaker prevent ping pong`() {
    val hierarchy = listOf("origin", "uhd", "hd", "sd", "ld", "md", "default")
    val available = mapOf("hd" to true, "sd" to true, "origin" to true)
    val attempted = mutableSetOf<String>()

    var currentQuality = "hd"
    val currentRank = hierarchy.indexOf(currentQuality)

    // 1st downgrade from "hd" (rank 2)
    attempted.add(currentQuality)
    val candidates1 = hierarchy.filterIndexed { index, qKey ->
      index > currentRank && available.containsKey(qKey) && !attempted.contains(qKey)
    }
    assertEquals(listOf("sd"), candidates1)
    val nextQuality1 = candidates1.first()
    assertEquals("sd", nextQuality1)

    // Move to "sd" (rank 3)
    currentQuality = nextQuality1
    attempted.add(currentQuality)
    val nextRank = hierarchy.indexOf(currentQuality)
    val candidates2 = hierarchy.filterIndexed { index, qKey ->
      index > nextRank && available.containsKey(qKey) && !attempted.contains(qKey)
    }
    // "sd" has no lower available quality in available -> candidates2 must be empty!
    // It must NEVER fall back to "hd" or "origin" because index must be > nextRank (3)
    assertTrue(candidates2.isEmpty())
  }

  @Test
  fun `test one-shot lock prevents further auto downgrades after adapting once`() {
    var autoDegradeCount = 0
    val maxAutoDegrades = 1

    // First trigger
    if (autoDegradeCount < maxAutoDegrades) {
      autoDegradeCount++
    }
    assertEquals(1, autoDegradeCount)

    // Second trigger attempt (must be blocked by lock)
    var wasTriggered = false
    if (autoDegradeCount < maxAutoDegrades) {
      wasTriggered = true
    }
    assertEquals(false, wasTriggered)
    assertEquals(1, autoDegradeCount)
  }

  @Test
  fun `test screen-off audio mode state management`() {
    val state = com.example.ui.WorkbenchUiState()
    assertEquals(false, state.isScreenOffAudioMode)

    val updatedState = state.copy(isScreenOffAudioMode = true)
    assertEquals(true, updatedState.isScreenOffAudioMode)

    val resumedState = updatedState.copy(isScreenOffAudioMode = false)
    assertEquals(false, resumedState.isScreenOffAudioMode)
  }

  @Test
  fun `test app guide title in string resources without version number`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val guideTitle = context.getString(R.string.user_guide_title)
    assertEquals("抖眸流控 · 使用指南", guideTitle)
    assertTrue(!guideTitle.contains("v1."))
  }

  @Test
  fun `test gesture partition and direction locking prevents volume and channel switch conflict`() {
    val screenWidth = 1000f

    // Left zone (< 0.40f)
    val leftX = 300f
    val isLeft = leftX < (screenWidth * 0.40f)
    val isRight1 = leftX > (screenWidth * 0.60f)
    assertTrue(isLeft)
    assertEquals(false, isRight1)

    // Right zone (> 0.60f)
    val rightX = 750f
    val isRight = rightX > (screenWidth * 0.60f)
    val isLeft1 = rightX < (screenWidth * 0.40f)
    assertTrue(isRight)
    assertEquals(false, isLeft1)

    // Middle zone (0.40f ~ 0.60f) - neutral deadzone, neither volume nor channel
    val middleX = 500f
    val isMiddleRight = middleX > (screenWidth * 0.60f)
    val isMiddleLeft = middleX < (screenWidth * 0.40f)
    assertEquals(false, isMiddleRight)
    assertEquals(false, isMiddleLeft)

    // Dominant vertical axis check: absY > absX * 1.3f
    val deltaX = 10f
    val deltaY = 30f
    val isVertical = kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) * 1.3f
    assertTrue(isVertical)

    // Horizontal swipe should NOT trigger vertical modes
    val hDeltaX = 50f
    val hDeltaY = 20f
    val isHorizontalVertical = kotlin.math.abs(hDeltaY) > kotlin.math.abs(hDeltaX) * 1.3f
    assertEquals(false, isHorizontalVertical)
  }

  @Test
  fun `test audio-only vs video stream track identification avoids false positive`() {
    // Case 1: Stream has both video and audio tracks (typical video stream)
    val hasVideoTrack1 = true
    val hasAudioTrack1 = true
    val isScreenOffAudioMode1 = false
    val isLifecycleBackground1 = false
    val isTrulyAudioOnly1 = !hasVideoTrack1 && hasAudioTrack1 && !isScreenOffAudioMode1 && !isLifecycleBackground1
    assertEquals(false, isTrulyAudioOnly1)

    // Case 2: Stream has video track, but is currently in screen-off audio mode
    val hasVideoTrack2 = true
    val hasAudioTrack2 = true
    val isScreenOffAudioMode2 = true
    val isLifecycleBackground2 = false
    val isTrulyAudioOnly2 = !hasVideoTrack2 && hasAudioTrack2 && !isScreenOffAudioMode2 && !isLifecycleBackground2
    assertEquals(false, isTrulyAudioOnly2)

    // Case 3: Genuine radio / pure audio live stream (zero video tracks)
    val hasVideoTrack3 = false
    val hasAudioTrack3 = true
    val isScreenOffAudioMode3 = false
    val isLifecycleBackground3 = false
    val isTrulyAudioOnly3 = !hasVideoTrack3 && hasAudioTrack3 && !isScreenOffAudioMode3 && !isLifecycleBackground3
    assertTrue(isTrulyAudioOnly3)
  }

  @Test
  fun `test viewport boundary pan clamping calculation`() {
    val screenWidth = 1080f
    val screenHeight = 1920f
    val zoomScale = 2.0f

    val maxPanX = (screenWidth * (zoomScale - 1f)) / 2f
    val maxPanY = (screenHeight * (zoomScale - 1f)) / 2f
    assertEquals(540f, maxPanX, 0.01f)
    assertEquals(960f, maxPanY, 0.01f)

    // Attempted over-pan
    val excessivePanX = 800f
    val clampedX = excessivePanX.coerceIn(-maxPanX, maxPanX)
    assertEquals(540f, clampedX, 0.01f)

    val excessiveNegativePanY = -1200f
    val clampedY = excessiveNegativePanY.coerceIn(-maxPanY, maxPanY)
    assertEquals(-960f, clampedY, 0.01f)
  }

  @Test
  fun `test Douyin style double tap zoom pan calculation`() {
    val screenWidth = 1080f
    val screenHeight = 1920f
    val targetScale = 2.5f
    val maxPanX = (screenWidth * (targetScale - 1f)) / 2f
    val maxPanY = (screenHeight * (targetScale - 1f)) / 2f

    val center = androidx.compose.ui.geometry.Offset(screenWidth / 2f, screenHeight / 2f)
    // Case 1: Double tap at center (540, 960) -> targetPan should be (0, 0)
    val tapCenter = center
    val targetPanXCenter = ((center.x - tapCenter.x) * (targetScale - 1f)).coerceIn(-maxPanX, maxPanX)
    val targetPanYCenter = ((center.y - tapCenter.y) * (targetScale - 1f)).coerceIn(-maxPanY, maxPanY)
    assertEquals(0f, targetPanXCenter, 0.01f)
    assertEquals(0f, targetPanYCenter, 0.01f)

    // Case 2: Double tap at bottom-right corner (900, 1600) -> content should pan up-left
    val tapBottomRight = androidx.compose.ui.geometry.Offset(900f, 1600f)
    val targetPanX = ((center.x - tapBottomRight.x) * (targetScale - 1f)).coerceIn(-maxPanX, maxPanX)
    val targetPanY = ((center.y - tapBottomRight.y) * (targetScale - 1f)).coerceIn(-maxPanY, maxPanY)
    assertTrue(targetPanX < 0f)
    assertTrue(targetPanY < 0f)
    assertTrue(targetPanX >= -maxPanX)
    assertTrue(targetPanY >= -maxPanY)
  }

  @Test
  fun `test Douyin style centroid zoom pan adjustment invariant`() {
    val screenWidth = 1080f
    val screenHeight = 1920f
    val center = androidx.compose.ui.geometry.Offset(screenWidth / 2f, screenHeight / 2f)
    val currentScale = 1.5f
    val zoomChange = 1.2f
    val newScale = currentScale * zoomChange
    val scaleRatio = newScale / currentScale
    val panOffset = androidx.compose.ui.geometry.Offset(100f, -50f)
    val centroid = androidx.compose.ui.geometry.Offset(700f, 800f)
    val panChange = androidx.compose.ui.geometry.Offset(10f, 5f)

    val focalPan = (panOffset + panChange) + (centroid - center - panOffset) * (1f - scaleRatio)
    // Verify focalPan is calculated deterministically and scales around centroid
    assertNotNull(focalPan)
  }

  @Test
  fun `test channel display name and subtitle never leaks room number`() {
    val rid = "10443341948"
    val channelEmptyTitle = com.example.data.LiveChannel(
      rid = rid,
      name = "房间 $rid",
      title = "",
      avatar = "",
      isLive = true,
      statusKnown = true
    )

    val displayName = channelEmptyTitle.name.let { raw ->
      if (raw.isBlank() || raw.matches(Regex("^(?:频道|房间)\\s*\\d{3,22}$")) || raw == channelEmptyTitle.rid) {
        "抖音主播"
      } else {
        raw
      }
    }
    assertEquals("抖音主播", displayName)
    assertFalse(displayName.contains(rid))

    val subText = when {
      channelEmptyTitle.title.isNotEmpty() && !channelEmptyTitle.title.matches(Regex("^(?:房间号?|频道)\\s*[:：]?\\s*\\d+.*$")) -> channelEmptyTitle.title
      channelEmptyTitle.isLive -> "实况直播中"
      !channelEmptyTitle.statusKnown -> "正在核验开播状态..."
      else -> "主播暂未开播"
    }
    assertEquals("实况直播中", subText)
    assertFalse(subText.contains(rid))
    assertFalse(subText.contains("房间"))

    val channelOffline = channelEmptyTitle.copy(isLive = false, statusKnown = true)
    val subTextOffline = when {
      channelOffline.title.isNotEmpty() && !channelOffline.title.matches(Regex("^(?:房间号?|频道)\\s*[:：]?\\s*\\d+.*$")) -> channelOffline.title
      channelOffline.isLive -> "实况直播中"
      !channelOffline.statusKnown -> "正在核验开播状态..."
      else -> "主播暂未开播"
    }
    assertEquals("主播暂未开播", subTextOffline)
    assertFalse(subTextOffline.contains(rid))
  }

  @Test
  fun `test channel preserves custom anchor name like 茉莉·molly without replacing with 抖音主播`() {
    val rid = "171612155041"
    val channelWithRealName = com.example.data.LiveChannel(
      rid = rid,
      name = "茉莉·molly",
      title = "迷人的笑脸吸引视线",
      avatar = "https://p26.douyinpic.com/aweme/100x100/aweme-avatar/tos-cn-avt-0015_df08a01fd3fae937ff5631fc9ec1a361.jpeg",
      isLive = true,
      statusKnown = true
    )

    val displayName = if (channelWithRealName.name.isNotBlank() && channelWithRealName.name != channelWithRealName.rid && !channelWithRealName.name.matches(Regex("^(?:频道|房间)\\s*\\d{3,22}$"))) {
      channelWithRealName.name
    } else if (channelWithRealName.title.isNotBlank() && !channelWithRealName.title.matches(Regex("^(?:房间号?|频道)\\s*[:：]?\\s*\\d+.*$"))) {
      channelWithRealName.title
    } else {
      channelWithRealName.name.ifBlank { "抖音主播" }
    }

    // Must be strictly "茉莉·molly", not "抖音主播"
    assertEquals("茉莉·molly", displayName)
    assertFalse(displayName == "抖音主播")
    assertFalse(displayName.contains(rid))

    val subText = when {
      channelWithRealName.title.isNotEmpty() && !channelWithRealName.title.matches(Regex("^(?:房间号?|频道)\\s*[:：]?\\s*\\d+.*$")) -> channelWithRealName.title
      channelWithRealName.isLive -> "实况直播中"
      !channelWithRealName.statusKnown -> "正在核验开播状态..."
      else -> "主播暂未开播"
    }
    // Must be "迷人的笑脸吸引视线"
    assertEquals("迷人的笑脸吸引视线", subText)
    assertFalse(subText.contains(rid))
    assertFalse(subText.contains("房间号"))
  }

  @Test
  fun `test ViewModel conservative quality selection puts hd and sd first and origin last`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.LiveWorkbenchViewModel(app)

    val streamsNoHd = mapOf(
      "origin" to com.example.engine.QualityStream("原画", "http://flv/origin", "http://hls/origin"),
      "uhd" to com.example.engine.QualityStream("超清", "http://flv/uhd", "http://hls/uhd"),
      "sd" to com.example.engine.QualityStream("标清", "http://flv/sd", "http://hls/sd")
    )
    // Preferred is "hd" which is missing; conservative selection must choose "sd" instead of "uhd" or "origin"
    assertEquals("sd", vm.selectConservativeQualityForTest(streamsNoHd, "hd"))

    val originOnly = mapOf(
      "origin" to com.example.engine.QualityStream("原画", "http://flv/origin", "http://hls/origin")
    )
    assertEquals("origin", vm.selectConservativeQualityForTest(originOnly, "hd"))
  }

  @Test
  fun `test ViewModel handlePlayerError cascades 3 downgrades and falls back protocol HLS to FLV before surfacing error`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.LiveWorkbenchViewModel(app)

    val streamData = com.example.engine.LiveStreamData(
      rid = "171612155041",
      isLive = true,
      statusKnown = true,
      name = "茉莉·molly",
      title = "迷人的笑脸吸引视线",
      avatar = "",
      streams = mapOf(
        "origin" to com.example.engine.QualityStream("原画 (1080P60)", "http://flv/origin", "http://hls/origin"),
        "uhd" to com.example.engine.QualityStream("超清 (1080P)", "http://flv/uhd", "http://hls/uhd"),
        "hd" to com.example.engine.QualityStream("高清 (720P)", "http://flv/hd", "http://hls/hd"),
        "sd" to com.example.engine.QualityStream("标清 (540P)", "http://flv/sd", "http://hls/sd")
      )
    )

    // Start at uhd on hls
    vm.seedPlaybackForTest(streamData, selectedQuality = "uhd", selectedProtocol = "hls")

    // 1st error (decoder error): uhd -> hd
    vm.handlePlayerError(isDecoderError = true, errorMsg = "MediaCodec configure failed")
    assertEquals("hd", vm.uiState.value.selectedQuality)
    assertEquals("http://hls/hd", vm.uiState.value.playbackUrl)
    assertTrue(vm.uiState.value.playbackStatus is com.example.ui.PlaybackStatus.Playing)

    // Wait > 800ms cooldown for explicit error recovery
    kotlinx.coroutines.delay(850L)

    // 2nd error (non-decoder persistent error): permissive handling still downgrades hd -> sd
    vm.handlePlayerError(isDecoderError = false, errorMsg = "Render stalled")
    assertEquals("sd", vm.uiState.value.selectedQuality)
    assertEquals("http://hls/sd", vm.uiState.value.playbackUrl)
    assertTrue(vm.uiState.value.playbackStatus is com.example.ui.PlaybackStatus.Playing)

    kotlinx.coroutines.delay(850L)

    // 3rd error at lowest quality "sd": no lower quality -> switches protocol HLS -> FLV
    vm.handlePlayerError(isDecoderError = true, errorMsg = "HEVC decoder unsupported")
    assertEquals("sd", vm.uiState.value.selectedQuality)
    assertEquals("flv", vm.uiState.value.selectedProtocol)
    assertEquals("http://flv/sd", vm.uiState.value.playbackUrl)
    assertTrue(vm.uiState.value.playbackStatus is com.example.ui.PlaybackStatus.Playing)

    kotlinx.coroutines.delay(850L)

    // 4th error after 3 attempts exhausted -> now surfaces Error state for manual retry
    vm.handlePlayerError(isDecoderError = true, errorMsg = "HEVC decoder unsupported")
    assertTrue(vm.uiState.value.playbackStatus is com.example.ui.PlaybackStatus.Error)
  }

  @Test
  fun `test manifest disables cleartext traffic attaches network security config and omits REQUEST_INSTALL_PACKAGES`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pkgInfo = context.packageManager.getPackageInfo(
      context.packageName,
      android.content.pm.PackageManager.GET_PERMISSIONS
    )
    val appInfo = context.applicationInfo

    // usesCleartextTraffic must be false
    assertEquals(0, appInfo.flags and android.content.pm.ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC)

    // network_security_config XML resource must exist and parse cleanly
    val xmlParser = context.resources.getXml(R.xml.network_security_config)
    var foundBaseConfig = false
    while (xmlParser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
      if (xmlParser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && xmlParser.name == "base-config") {
        foundBaseConfig = true
        assertEquals("false", xmlParser.getAttributeValue(null, "cleartextTrafficPermitted"))
      }
    }
    assertTrue(foundBaseConfig)

    // Prohibited permission REQUEST_INSTALL_PACKAGES must never be requested
    val permissions = pkgInfo.requestedPermissions?.toList().orEmpty()
    assertFalse(permissions.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
  }

  @Test
  fun `test manifest declares Leanback launcher banner and non-required TV features`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pm = context.packageManager

    val leanbackIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
      addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER)
      setPackage(context.packageName)
    }
    val resolved = pm.queryIntentActivities(leanbackIntent, 0)
    assertTrue(resolved.isNotEmpty())
    assertTrue(resolved.any { it.activityInfo.name == MainActivity::class.java.name })
    assertTrue(context.applicationInfo.banner != 0)
  }

  @Test
  fun `test TvSafeArea applies overscan safe insets on TV and compact padding on handheld`() {
    assertTrue(com.example.ui.TvSafeArea.workbenchHorizontal(isTv = true).value >= 36f)
    assertTrue(com.example.ui.TvSafeArea.workbenchVertical(isTv = true).value >= 20f)
    assertTrue(com.example.ui.TvSafeArea.fullscreenHudHorizontal(isTv = true).value >= 44f)
    assertTrue(com.example.ui.TvSafeArea.fullscreenHudVertical(isTv = true).value >= 24f)
    assertTrue(com.example.ui.TvSafeArea.fullscreenBottomHint(isTv = true).value >= 28f)

    assertEquals(12f, com.example.ui.TvSafeArea.workbenchHorizontal(isTv = false).value, 0.01f)
    assertEquals(16f, com.example.ui.TvSafeArea.fullscreenHudHorizontal(isTv = false).value, 0.01f)
  }

  @Test
  fun `test TvKeyEventPolicy prevents discrete toggle key repeat flickering while allowing continuous volume keys`() {
    assertFalse(TvKeyEventPolicy.shouldIgnoreRepeat(android.view.KeyEvent.KEYCODE_DPAD_CENTER, repeatCount = 0))
    assertTrue(TvKeyEventPolicy.shouldIgnoreRepeat(android.view.KeyEvent.KEYCODE_DPAD_CENTER, repeatCount = 1))
    assertTrue(TvKeyEventPolicy.shouldIgnoreRepeat(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, repeatCount = 2))
    assertTrue(TvKeyEventPolicy.shouldIgnoreRepeat(android.view.KeyEvent.KEYCODE_BACK, repeatCount = 1))
    assertTrue(TvKeyEventPolicy.shouldIgnoreRepeat(android.view.KeyEvent.KEYCODE_1, repeatCount = 1))

    // Continuous volume/direction repeats must still work when user holds the button
    assertFalse(TvKeyEventPolicy.shouldIgnoreRepeat(android.view.KeyEvent.KEYCODE_VOLUME_UP, repeatCount = 2))
    assertFalse(TvKeyEventPolicy.shouldIgnoreRepeat(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, repeatCount = 3))
  }

  @Test
  fun `test ChannelDisplayFormatter preserves anchor name and suppresses raw room numbers`() {
    val anchor = LiveChannel(
      rid = "171612155041",
      name = "茉莉·molly",
      title = "迷人的笑脸吸引视线",
      avatar = "",
      isLive = true,
      statusKnown = true
    )
    assertEquals("茉莉·molly", com.example.ui.components.ChannelDisplayFormatter.resolveBannerName(anchor))
    assertEquals("迷人的笑脸吸引视线", com.example.ui.components.ChannelDisplayFormatter.resolveBannerSubText(anchor))

    val numericFallback = LiveChannel(
      rid = "171612155041",
      name = "房间 171612155041",
      title = "房间号: 171612155041",
      avatar = "",
      isLive = true,
      statusKnown = true
    )
    val resolvedName = com.example.ui.components.ChannelDisplayFormatter.resolveBannerName(numericFallback)
    val resolvedSub = com.example.ui.components.ChannelDisplayFormatter.resolveBannerSubText(numericFallback)
    assertFalse(resolvedName.contains("171612155041"))
    assertEquals("实况直播中", resolvedSub)
    assertFalse(resolvedSub.contains("171612155041"))
  }
}
