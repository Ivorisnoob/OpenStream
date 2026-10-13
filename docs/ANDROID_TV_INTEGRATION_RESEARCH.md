# Android TV & Google TV Platform Integration Guide
**Streaming App Architecture & Best Practices (Benchmarks: Kodi, Jellyfin Android TV, Stremio TV, SmartTube, TiviMate)**

---

## Executive Summary

Engineering a first-class streaming client for Android TV and Google TV requires bridging modern Android architecture (Jetpack Compose, Media3, Coil, Kotlin Coroutines) with TV-specific platform contracts and harsh hardware realities. Unlike mobile devices with 6GB–16GB RAM and high-end Octa-core CPUs, mainstream TV devices (Chromecast with Google TV HD, Amazon Fire TV Stick Lite/HD, low-end smart TVs) operate on **1GB–1.5GB total system RAM**, weak quad-core Cortex-A35/A53 processors, and strict hardware decoder limitations.

This report synthesizes deep architectural research across leading open-source and specialized TV streaming applications (**Jellyfin for Android TV, Kodi, Stremio TV, SmartTube, and TiviMate**) into actionable blueprints across four core pillars:
1. **Android TV Home Screen Integration** ("Play Next" & Channels via `androidx.tvprovider`)
2. **System Voice & Global Search** (`ContentProvider` + Google Assistant / Searchable configuration)
3. **TV Input & Remote Optimization** (D-Pad, media keys, long-press, power/screensaver, display mode matching)
4. **Performance & Memory Optimization** (TV Compose vs Leanback, low-RAM stick survivability, bitmap pooling)

---

## 1. Android TV Home Screen Integration

### 1.1 Android TV Leanback vs Google TV (LauncherX) Architecture

Android TV’s home screen architecture transitioned in Android 8.0 (API 26) from basic notifications to **Channels and Programs** governed by `androidx.tvprovider:tvprovider`.

```
+---------------------------------------------------------------------------------+
|                               Android TV / Google TV Launcher                   |
+---------------------------------------------------------------------------------+
           |                                                      |
           v                                                      v
  +------------------+                                  +-------------------+
  | "Play Next" Row  |                                  | Custom App Rows   |
  | (Watch Next)     |                                  | (Preview Channels)|
  +------------------+                                  +-------------------+
           |                                                      |
           +--------------------------+---------------------------+
                                      |
                         TvContractCompat ContentProvider
                                      |
                      +-------------------------------+
                      | OpenStream TvSyncManager /     |
                      | WorkManager Periodic Sync     |
                      +-------------------------------+
```

* **Play Next / Watch Next Row (`WatchNextProgram`)**:
  * Unified, platform-level row at the top of the launcher.
  * Supported on **both** Android TV Leanback launcher and Google TV (LauncherX).
  * System displays dynamic watch progress bars directly on program cards.
* **Custom App Channels (`PreviewChannel` & `PreviewProgram`)**:
  * On classic **Android TV**, apps can publish multiple horizontal channel rows (e.g., "Trending Movies", "Recently Added Anime"). Users can toggle and re-order them via "Customize Channels".
  * On **Google TV**, custom app channels are relegated to the bottom of the "Apps" tab or aggregated into Google TV's algorithmic recommendation tabs.
* **Google TV Media Actions Cloud Ingest vs Local TvContractCompat**:
  * Commercial partner apps (Netflix, Disney+, Prime Video) integrate via Google's cloud-side Media Actions catalog feed.
  * Standalone/open-source apps (Jellyfin, SmartTube, Stremio, OpenStream) rely on **local `TvContractCompat`**, which remains fully functional on Android TV and feeds the system-level "Continue Watching / Play Next" row across modern Google TV builds.
* **Amazon Fire TV (Fire OS)**:
  * Fire OS (Fire OS 6/7/8 based on Android 7/9/11) suppresses `TvContractCompat` channels. Instead, Amazon utilizes the proprietary Amazon Recommendations API / Catalog Ingest. For multi-platform streaming apps, `TvContractCompat` calls must be wrapped in `try/catch` or runtime checks to prevent `SecurityException` / `ClassNotFoundException` on Fire OS devices.

---

### 1.2 "Play Next" / "Watch Next" Implementation (`WatchNextProgram`)

#### Watch Next Program Types
The Android TV framework defines four semantic types for `WatchNextProgram`:
1. `WATCH_NEXT_TYPE_CONTINUE`: In-progress item (user started watching and paused between ~3% and ~95%).
2. `WATCH_NEXT_TYPE_NEXT`: The immediate next episode after the user finishes an episode of an episodic series.
3. `WATCH_NEXT_TYPE_NEW`: Newly released episodes/content for followed series.
4. `WATCH_NEXT_TYPE_WATCHLIST`: Items manually saved to "Watch Later" / "Bookmarks".

#### Critical Metadata Fields
```kotlin
val builder = WatchNextProgram.Builder()
    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
    .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE) // or TYPE_TV_EPISODE
    .setTitle(media.title)
    .setDescription(media.overview)
    .setPosterArtUri(Uri.parse(media.backdropOrPosterUrl))
    .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9) // 16:9 for Continue Watching
    .setInternalProviderId("openstream_${media.type}_${media.id}") // UNIQUE DEDUPLICATION KEY
    .setContentId(media.id.toString())
    .setIntentUri(deepLinkUri)
    .setLastPlaybackPositionMillis(currentPositionMs.toInt())
    .setDurationMillis(totalDurationMs.toInt())
    .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
```

#### Lifecycle Synchronization Strategy (Jellyfin & Stremio Reference)
* **3% - 95% Rule**:
  * If playback terminates under 3% (< 60s), do not add to Play Next (prevents launcher clutter from accidental clicks).
  * If playback terminates between 3% and 95%, upsert as `WATCH_NEXT_TYPE_CONTINUE` with progress timestamps.
  * If playback exceeds 95%, delete the finished item from Play Next. For episodic content (TV series/anime), immediately fetch the next episode from the database and publish it as `WATCH_NEXT_TYPE_NEXT` with `lastPlaybackPositionMillis = 0`.
* **Deduplication & Reconciliation Pattern**:
  Always query `TvContractCompat.WatchNextPrograms.CONTENT_URI` by `COLUMN_INTERNAL_PROVIDER_ID` before insertion. If an entry exists, call `update()`; otherwise, call `insert()`.

```kotlin
// Production Kotlin implementation for Play Next syncing
class TvPlayNextManager(private val context: Context) {

    private val contentResolver = context.contentResolver

    fun updateProgress(
        mediaId: String,
        mediaType: String,
        title: String,
        overview: String,
        posterUrl: String,
        positionMs: Long,
        durationMs: Long,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ) {
        val internalId = "openstream_${mediaType}_$mediaId"
        val progressRatio = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f

        // Remove if completed (> 95%) or barely started (< 2%)
        if (progressRatio >= 0.95f || progressRatio < 0.02f) {
            removeProgram(internalId)
            return
        }

        val existingProgramId = findExistingProgramId(internalId)
        val deepLinkUri = Uri.parse("openstream://play/$mediaType/$mediaId?resumeMs=$positionMs")

        val builder = WatchNextProgram.Builder()
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setType(if (mediaType == "tv") TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE else TvContractCompat.PreviewPrograms.TYPE_MOVIE)
            .setTitle(title)
            .setDescription(overview)
            .setPosterArtUri(Uri.parse(posterUrl))
            .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
            .setInternalProviderId(internalId)
            .setIntentUri(deepLinkUri)
            .setLastPlaybackPositionMillis(positionMs.toInt())
            .setDurationMillis(durationMs.toInt())
            .setLastEngagementTimeUtcMillis(System.currentTimeMillis())

        if (seasonNumber != null && episodeNumber != null) {
            builder.setSeasonNumber(seasonNumber)
                .setEpisodeNumber(episodeNumber)
        }

        val program = builder.build()

        try {
            if (existingProgramId != null) {
                val updateUri = TvContractCompat.buildWatchNextProgramUri(existingProgramId)
                contentResolver.update(updateUri, program.toContentValues(), null, null)
            } else {
                contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())
            }
        } catch (e: Exception) {
            Log.e("TvPlayNextManager", "Failed to sync WatchNextProgram: ${e.message}")
        }
    }

    fun removeProgram(internalProviderId: String) {
        val existingId = findExistingProgramId(internalProviderId) ?: return
        try {
            val uri = TvContractCompat.buildWatchNextProgramUri(existingId)
            contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            Log.e("TvPlayNextManager", "Failed to delete WatchNextProgram: ${e.message}")
        }
    }

    private fun findExistingProgramId(internalProviderId: String): Long? {
        val projection = arrayOf(
            TvContractCompat.WatchNextPrograms._ID,
            TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
        )
        return try {
            contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                projection,
                "${TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID} = ?",
                arrayOf(internalProviderId),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(cursor.getColumnIndexOrThrow(TvContractCompat.WatchNextPrograms._ID))
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
```

---

### 1.3 Custom Preview Channels & Program Rows (Leanback Launcher)

Apps can publish dedicated channels to the Android TV launcher using `PreviewChannelHelper`.

```kotlin
class TvChannelManager(private val context: Context) {
    private val helper = PreviewChannelHelper(context)

    fun createOrUpdateChannel(
        internalChannelId: String,
        displayName: String,
        description: String,
        appLinkIntentUri: Uri
    ): Long {
        val existingChannel = helper.allChannels.firstOrNull { it.internalProviderId == internalChannelId }
        
        if (existingChannel != null) {
            return existingChannel.id
        }

        val channel = PreviewChannel.Builder()
            .setDisplayName(displayName)
            .setDescription(description)
            .setAppLinkIntentUri(appLinkIntentUri)
            .setInternalProviderId(internalChannelId)
            .setLogo(BitmapFactory.decodeResource(context.resources, R.drawable.ic_tv_channel_logo))
            .build()

        val channelId = helper.publishChannel(channel)
        
        // Request launcher to make the primary channel browsable (visible by default)
        TvContractCompat.requestChannelBrowsable(context, channelId)
        return channelId
    }

    fun syncChannelPrograms(channelId: Long, items: List<MediaItemDto>) {
        // Clear old stale programs
        val existingPrograms = helper.allProgramsInChannel(channelId)
        for (prog in existingPrograms) {
            helper.deletePreviewProgram(prog.id)
        }

        // Add fresh items
        items.forEachIndexed { index, media ->
            val program = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setTitle(media.title)
                .setDescription(media.overview)
                .setPosterArtUri(Uri.parse(media.posterUrl))
                .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_2_3)
                .setWeight(items.size - index) // Controls ordering on row
                .setIntentUri(Uri.parse("openstream://details/${media.type}/${media.id}"))
                .setInternalProviderId("channel_${channelId}_${media.id}")
                .build()

            helper.publishPreviewProgram(program)
        }
    }
}
```

---

## 2. System Voice & Global Search Integration

Android TV and Google TV feature a unified search interface driven by Google Assistant and the system search overlay. Sideloaded and native media apps can surface their entire catalog into global system search via Android’s **Searchable Configuration** and an exported `ContentProvider`.

```
                    +--------------------------------+
                    | Google Assistant / Voice Input |
                    +--------------------------------+
                                   |
                     "Search Dune on OpenStream"
                                   |
                                   v
                    +--------------------------------+
                    | Android TV Global SearchEngine |
                    +--------------------------------+
                                   |
                   content://com.ivor.openstream.search/
                   search_suggest_query/Dune?limit=50
                                   |
                                   v
             +----------------------------------------------+
             | OpenStreamSearchContentProvider              |
             | -> Queries Room Database / In-Memory Index   |
             +----------------------------------------------+
                                   |
                              MatrixCursor
                   [Title, PosterUri, IntentDataUri, ...]
                                   |
                                   v
                    +--------------------------------+
                    | Android TV Global Search UI    |
                    | Card with OpenStream Icon      |
                    +--------------------------------+
```

### 2.1 Searchable Configuration (`res/xml/searchable.xml`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<searchable xmlns:android="http://schemas.android.com/apk/res/android"
    android:label="@string/app_name"
    android:hint="@string/search_hint"
    android:searchSuggestAuthority="com.ivor.openstream.search"
    android:searchSuggestIntentAction="android.intent.action.VIEW"
    android:searchSuggestIntentData="content://com.ivor.openstream.search/item"
    android:searchSuggestSelection=" ?"
    android:searchSuggestThreshold="1"
    android:includeInGlobalSearch="true"
    android:searchSettingsDescription="@string/search_settings_description">
</searchable>
```

### 2.2 Manifest Declarations

```xml
<!-- Search Activity -->
<activity
    android:name=".ui.search.SearchActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.SEARCH" />
        <action android:name="com.google.android.gms.actions.SEARCH_ACTION" />
        <action android:name="android.media.action.MEDIA_PLAY_FROM_SEARCH" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <meta-data
        android:name="android.app.searchable"
        android:resource="@xml/searchable" />
</activity>

<!-- Suggestion Content Provider -->
<provider
    android:name=".data.search.TvSearchContentProvider"
    android:authorities="com.ivor.openstream.search"
    android:exported="true"
    android:readPermission="android.permission.GLOBAL_SEARCH"
    tools:ignore="ExportedContentProvider" />
```

### 2.3 `ContentProvider` Implementation for Android TV Global Search

The global search framework expects suggestions to conform to specific column projections defined in `SearchManager`.

```kotlin
class TvSearchContentProvider : ContentProvider() {

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI("com.ivor.openstream.search", "search_suggest_query/*", SEARCH_SUGGEST)
        addURI("com.ivor.openstream.search", "search_suggest_query", SEARCH_SUGGEST)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val query = if (uri.pathSegments.size > 1) {
            uri.lastPathSegment?.lowercase()
        } else {
            selectionArgs?.firstOrNull()?.lowercase()
        }

        if (query.isNullOrBlank()) return null

        // System expects this exact column contract:
        val columns = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,            // Title
            SearchManager.SUGGEST_COLUMN_TEXT_2,            // Subtitle / Year / Genre
            SearchManager.SUGGEST_COLUMN_ICON_1,            // Poster image URI
            SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID,    // Media ID for deep link
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,     // Intent.ACTION_VIEW
            SearchManager.SUGGEST_COLUMN_DURATION,          // Duration in ms
            SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR,   // Release year
            SearchManager.SUGGEST_COLUMN_CONTENT_TYPE       // MIME or "video/mp4"
        )

        val cursor = MatrixCursor(columns)

        // Run local repository search (e.g., query cached TMDB items or Room database)
        // Must complete synchronously and quickly (< 200ms) or Google Assistant drops results!
        val searchResults = runBlocking(Dispatchers.IO) {
            getSearchRepository().quickSearch(query, limit = 20)
        }

        searchResults.forEachIndexed { index, item ->
            cursor.addRow(arrayOf(
                index.toLong(),
                item.title,
                "${item.releaseYear} • ${item.genreSummary}",
                item.posterUrl,
                "openstream://details/${item.type}/${item.id}",
                Intent.ACTION_VIEW,
                item.durationMs,
                item.releaseYear,
                "video/*"
            ))
        }

        return cursor
    }

    override fun getType(uri: Uri): String = SearchManager.SUGGEST_MIME_TYPE
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val SEARCH_SUGGEST = 1
    }
}
```

---

## 3. TV Input & Remote Control Optimization

TV remotes vary widely across hardware vendors: from Apple TV-style minimalist remotes (Chromecast with Google TV, Fire TV) to 45-key operator remotes with numeric pads, teletext color buttons, and dedicated media keys (Sony Bravia, Philips, LG, Shield TV).

### 3.1 Comprehensive Remote Key Code Dispatch Matrix

| Key Category | Key Codes | Primary Streaming Function | Fallback / Context Function |
|---|---|---|---|
| **Directional Pad (D-Pad)** | `KEYCODE_DPAD_UP`<br>`KEYCODE_DPAD_DOWN`<br>`KEYCODE_DPAD_LEFT`<br>`KEYCODE_DPAD_RIGHT` | Focus navigation in UI | **Playback**: Left/Right = Seek 10s backward/forward; Up/Down = Show player controls / Quick channel switcher |
| **Selection / Confirmation** | `KEYCODE_DPAD_CENTER`<br>`KEYCODE_ENTER`<br>`KEYCODE_NUMPAD_ENTER` | Activate focused element | **Playback**: Toggle OSD overlay. **Long Press**: Open Context Menu (Mark Watched, Track selection) |
| **Media Playback** | `KEYCODE_MEDIA_PLAY`<br>`KEYCODE_MEDIA_PAUSE`<br>`KEYCODE_MEDIA_PLAY_PAUSE`<br>`KEYCODE_MEDIA_STOP` | Direct ExoPlayer command: `player.play()`, `player.pause()` | Ensure `MEDIA_PLAY_PAUSE` toggles state correctly without double-triggering |
| **Trickplay Scrubbing** | `KEYCODE_MEDIA_FAST_FORWARD`<br>`KEYCODE_MEDIA_REWIND`<br>`KEYCODE_MEDIA_STEP_FORWARD`<br>`KEYCODE_MEDIA_STEP_BACKWARD` | Seek forward/backward (10s–30s or adaptive step curve) | Holding down triggers accelerated scrub speed |
| **Episode / Track Navigation** | `KEYCODE_MEDIA_NEXT`<br>`KEYCODE_MEDIA_PREVIOUS`<br>`KEYCODE_CHANNEL_UP`<br>`KEYCODE_CHANNEL_DOWN` | Play next/previous episode or audio track | `CHANNEL_UP/DOWN` acts as Next/Prev Episode in streaming apps; Channel Zap in IPTV (TiviMate) |
| **Color Keys** (Teletext / Smart TV) | `KEYCODE_PROG_RED`<br>`KEYCODE_PROG_GREEN`<br>`KEYCODE_PROG_YELLOW`<br>`KEYCODE_PROG_BLUE` | Quick Actions (TiviMate standard):<br>• Red: Audio Track Selector<br>• Green: Subtitle Toggle/Picker<br>• Yellow: Video Quality / Source List<br>• Blue: Add to Favorites / Watchlist | Dedicated hardware shortcuts found on European, Asian, and operator Android TV boxes |
| **Information & Context** | `KEYCODE_INFO`<br>`KEYCODE_GUIDE`<br>`KEYCODE_MENU`<br>`KEYCODE_BOOKMARK` | `INFO`: Toggle "Stats for Nerds" or show movie synopsis.<br>`MENU`: Context Action sheet.<br>`GUIDE`: Show Episode Sheet / Season selector. | On remotes without `MENU`, long-press `DPAD_CENTER` acts as the universal menu trigger |
| **Direct Subtitle** | `KEYCODE_CAPTIONS` | Direct cycle subtitle tracks (Off -> Sub 1 -> Sub 2) | Instant subtitle toggle |
| **Numeric Keypad** | `KEYCODE_0` .. `KEYCODE_9` | Direct jump to timestamp (e.g. `1`, `4`, `0` = jump to 14:00) | Direct channel entry in Live TV |

---

### 3.2 Long-Press Detection Patterns

In Android TV development, handling long-press on hardware remotes requires specialized event tracking because remotes send repeated `ACTION_DOWN` events with incrementing `repeatCount`.

#### View / Activity Layer (Native Key Tracking)
```kotlin
class TvPlayerActivity : ComponentActivity() {

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyDown(keyCode, event)

        // Essential: start tracking on repeatCount == 0 to receive onKeyLongPress!
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.repeatCount == 0) {
                event.startTracking()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            showPlaybackContextMenu() // Subtitles, Audio, Speed, Aspect Ratio
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }
}
```

#### Jetpack TV Compose Layer
In Jetpack Compose for TV, capture key presses using `Modifier.onPreviewKeyEvent` with custom coroutine timer tracking or `combinedClickable`:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvMediaCard(
    media: MediaItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        modifier = Modifier
            .width(160.dp)
            .aspectRatio(2f / 3f)
    ) {
        AsyncImage(
            model = media.posterUrl,
            contentDescription = media.title,
            contentScale = ContentScale.Crop
        )
    }
}
```

---

### 3.3 Screensaver Prevention, Ambient Mode & OLED Burn-In Protection

One of the most dangerous bugs in Android TV streaming apps is leaving `FLAG_KEEP_SCREEN_ON` enabled indefinitely when playback is paused. On modern OLED TVs (LG, Sony, Philips), static paused frames cause irreversible panel burn-in.

#### Best Practice Lifecycle State Machine
1. **Video Playing**: Set `keepScreenOn = true`.
2. **Video Paused / Buffering Error**: Start an inactivity countdown timer (e.g., **3 minutes**).
3. **Timer Expired**: Set `keepScreenOn = false`, hide playback controls, dim the screen or let Google TV **Ambient Mode (Daydream)** activate automatically.
4. **User Interacts (Any D-Pad click)**: Reset timer, wake UI, and restore normal brightness.

```kotlin
@Composable
fun KeepScreenOnController(isPlaying: Boolean) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    DisposableEffect(isPlaying) {
        val window = activity.window
        var timeoutJob: Job? = null
        val scope = CoroutineScope(Dispatchers.Main)

        if (isPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            // Paused: Keep screen on for 3 minutes, then release to permit TV Ambient Mode
            timeoutJob = scope.launch {
                delay(3.minutes)
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        onDispose {
            timeoutJob?.cancel()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
```

#### Immersive Mode & System Bars
Android TV does not display traditional status bars or navigation bars, but `WindowInsetsControllerCompat` must still enforce strict immersive presentation and overscan margins:

```kotlin
fun Activity.enableTvImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.hide(WindowInsetsCompat.Type.systemBars())
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}
```
* **Overscan TV Safe Margins**: Always maintain a minimum **5% safe margin** (48dp horizontally, 27dp vertically at 1080p) around screen borders. Many older TV panels cut off the outer 3%–5% of the frame via hardware overscan.

---

### 3.4 Auto Frame Rate (AFR) & Display Mode Switching (Kodi & SmartTube Pattern)

Most movies and series are mastered at **23.976 fps** or **24.000 fps**. Playing 24fps content on a standard 60Hz TV display forces **3:2 pulldown**, resulting in noticeable motion judder during camera pans.

* Apps like **Kodi, Jellyfin, and SmartTube** query `Display.supportedModes` to match the display refresh rate to the video stream.
* On Android 6.0+ (API 23+), set `Window.attributes.preferredDisplayModeId`.
* On Android 11+ (API 30+), ExoPlayer supports `Surface.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)`.

```kotlin
fun matchDisplayRefreshRate(activity: Activity, videoFps: Float) {
    val display = activity.window.decorView.display ?: return
    val supportedModes = display.supportedModes

    // Find mode matching resolution (e.g. 3840x2160 or 1920x1080) and target fps
    val targetMode = supportedModes.firstOrNull { mode ->
        val diff = abs(mode.refreshRate - videoFps)
        diff < 0.05f || (videoFps in 23.9f..24.1f && abs(mode.refreshRate - 23.976f) < 0.05f)
    }

    if (targetMode != null) {
        val params = activity.window.attributes
        params.preferredDisplayModeId = targetMode.modeId
        activity.window.attributes = params
    }
}
```

---

## 4. Performance & Memory Optimization on Constrained TV Hardware

### 4.1 Low-RAM Profile: The 1GB–1.5GB Challenge

| Device | SoC | RAM | OS Heap (`largeHeap="true"`) | GPU |
|---|---|---|---|---|
| **Chromecast with Google TV (HD)** | Amlogic S805X2 | 1.5 GB | ~256 MB | Mali-G31 MP2 |
| **Fire TV Stick Lite / HD** | MediaTek MT8695D | 1.0 GB | ~192 MB | IMG GE8300 |
| **Xiaomi Mi TV Stick** | Amlogic S805Y | 1.0 GB | ~192 MB | Mali-450 |
| **Nvidia Shield TV Pro** | Tegra X1+ | 3.0 GB | ~512 MB | Maxwell 256-core |

On a 1GB stick, the Android OS and system launcher consume **650MB–800MB**. The foreground streaming app is left with **< 200MB of real memory** before the Low Memory Killer (LMK) terminates it.

---

### 4.2 UI Framework Evaluation: Leanback Views vs Jetpack TV Compose

```
+------------------------------------+---------------------------------------+
| Classic Leanback (Views)           | Modern TV Compose (tv-material3)      |
+------------------------------------+---------------------------------------+
| • Rock-solid RecyclerView recycling| • High developer velocity             |
| • Extremely low memory footprint   | • Declarative UI unified with mobile  |
| • Zero GC pauses during D-pad navigation • Higher memory allocation churn |
| • Rigid, dated UI layout patterns  | • Potential recomposition frame drops |
| • XML / Fragment boilerplate       | • Focus restoration requires tuning  |
+------------------------------------+---------------------------------------+
```

#### Why TV Compose Can Stutter on 1GB Sticks
1. **Recomposition Churn**: Holding D-Pad right across a 50-item row causes 50 rapid focus transitions. If item composables allocate lambdas or read unstable state during recomposition, ART GC triggers every 2–3 seconds, causing dropped frames (stutter).
2. **Excessive Item Prefetch**: `TvLazyRow` default prefetch behavior can allocate too many offscreen poster bitmaps into memory simultaneously.

#### Production Rules for TV Compose on Low-RAM Hardware:
* **Rule 1: Limit `beyondBoundsItemCount`**: Keep `beyondBoundsItemCount = 1` or `0`. Never load 5+ offscreen cards.
* **Rule 2: Strict `@Immutable` / `@Stable` Models**: All data passed to cards must be immutable to allow compiler skipping.
* **Rule 3: Zero Dynamic Blur**: Avoid `Modifier.blur()`. The Mali-G31 GPU cannot render real-time multi-pass Gaussian blurs at 60fps. Use static gradient drawables or pre-computed assets.
* **Rule 4: Use `TvLazyRow` / `TvLazyColumn` with Key Identifiers**: Always provide stable `key = { item.id }` to prevent item recreation during scrolling.

```kotlin
@Composable
fun OptimizedMediaRow(
    title: String,
    items: ImmutableList<MediaItem>,
    onItemClick: (MediaItem) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
    )

    TvLazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        // Crucial for 1GB sticks: avoid holding large offscreen caches in memory
        pivotOffsets = PivotOffsets(parentFraction = 0.1f)
    ) {
        items(
            items = items,
            key = { it.id }
        ) { media ->
            TvMediaCard(
                media = media,
                onClick = { onItemClick(media) }
            )
        }
    }
}
```

---

### 4.3 Image Loading & Bitmap Pooling Optimization (Coil 3 Configuration)

Bitmaps are the single largest consumer of heap memory in a streaming app. A single uncompressed 1080p poster in `ARGB_8888` consumes:
$$\text{1920} \times \text{1080} \times \text{4 bytes} = \mathbf{8.29\text{ MB}}$$
Loading 25 full-sized backdrop images into memory simultaneously consumes **~200MB**, immediately triggering an `OutOfMemoryError` on a Fire TV Stick Lite!

#### Coil 3 TV Optimization Checklist:
1. **Downsample to Exact Card Display Dimensions**: If a TV poster card is 160dp x 240dp on a 1080p screen, downsample the image during decode to exactly **240 x 360 pixels** (~345 KB).
2. **Switch to `Bitmap.Config.RGB_565`**: `RGB_565` uses **2 bytes per pixel** instead of 4 (`ARGB_8888`). Because movie posters and backdrops do not contain transparency, this cuts memory consumption by **50%** with zero visual degradation.
3. **Cap In-Memory Cache to 15% of App Heap**: Prevent Coil from consuming more than 30MB–40MB of memory.
4. **Use Disk Cache Aggressively**: Let local flash storage hold cached images rather than RAM.

```kotlin
// Production Coil 3 configuration for Android TV
fun provideTvCoilImageLoader(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                // Restrict memory cache to 15% of available app heap
                .maxSizePercent(0.15)
                .strongReferencesEnabled(true)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(150L * 1024 * 1024) // 150 MB disk cache
                .build()
        }
        // Force RGB_565 for 50% RAM savings on TV poster art
        .bitmapConfig(Bitmap.Config.RGB_565)
        .allowRgb565(true)
        .crossfade(150) // Short crossfade to reduce GPU overdraw
        .build()
}
```

---

### 4.4 System Memory Trim Handling (`ComponentActivity.onTrimMemory`)

Android TV fires `onTrimMemory()` callbacks as system memory pressure increases. Streaming apps must actively drop caches to avoid process termination.

```kotlin
class MainActivity : ComponentActivity() {

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // App moved to background (user pressed Home button)
                // Release in-memory bitmap cache immediately!
                Coil.imageLoader(this).memoryCache?.clear()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // Foreground memory is critically low: purge caches aggressively
                Coil.imageLoader(this).memoryCache?.clear()
                System.gc()
            }
        }
    }
}
```

---

### 4.5 Media3 / ExoPlayer Video Surface & Codec Management

#### SurfaceView vs TextureView
* **Mandatory on TV**: Always use `SurfaceView` (`androidx.media3.ui.PlayerView` with `app:surface_type="surface_view"`).
* **Never use `TextureView` on Android TV**:
  * `TextureView` forces video frames through the OpenGL/Vulkan composition pipeline as an ordinary texture, consuming an extra 1080p/4K buffer in system RAM.
  * Adds 1–2 frames of presentation latency.
  * Drops 4K 60fps frames on low-end Mali-G31 GPUs.
  * `SurfaceView` punches a hole directly through the window hierarchy to the dedicated hardware display plane, enabling zero-copy hardware decoding.

#### Hardware Decoder Instance Limits
* TV chips (Amlogic S805X2 / S905Y4) support a **maximum of ONE 4K hardware decoder** or two 1080p decoders.
* If navigating away from the video player to a details page, **release ExoPlayer immediately** via `player.release()`. Keeping an ExoPlayer instance paused in the background locks the hardware decoder, causing any subsequent video or live trailer preview to fail with `MediaCodec.CodecException: ERROR_INSUFFICIENT_RESOURCE`.

---

## 5. Architectural Implementation Roadmap for OpenStream

To integrate these TV capabilities into OpenStream while maintaining full smartphone compatibility:

```
+-------------------------------------------------------------------------------+
| OpenStream Architecture                                                       |
+-------------------------------------------------------------------------------+
  |
  +---> app/src/main/
          |
          +---> data/tv/
          |       +---> TvPlayNextManager.kt       (WatchNextProgram sync)
          |       +---> TvSearchContentProvider.kt (Global search cursor provider)
          |       +---> TvChannelManager.kt        (Leanback preview channels)
          |
          +---> ui/tv/
          |       +---> TvFocusModifier.kt         (D-Pad focus wrappers)
          |       +---> TvRemoteKeyHandler.kt      (KeyEvent routing & long-press)
          |       +---> TvKeepScreenOn.kt          (OLED-safe screensaver timer)
          |
          +---> di/
                  +---> TvModule.kt                (ImageLoader RGB_565 & DI bindings)
```

1. **Gradle Dependencies**:
   Add to `gradle/libs.versions.toml`:
   ```toml
   androidx-tvprovider = { group = "androidx.tvprovider", name = "tvprovider", version = "1.0.0" }
   androidx-tv-foundation = { group = "androidx.tv", name = "tv-foundation", version = "1.0.0-alpha11" }
   androidx-tv-material = { group = "androidx.tv", name = "tv-material", version = "1.0.0" }
   ```
2. **Leanback Feature Gating**:
   Keep `android.software.leanback` and `android.hardware.touchscreen` marked `required="false"` so the single APK supports both phones, tablets, and TV devices.
3. **Playback Hook**:
   Inject `TvPlayNextManager` into the Media3 playback progress collector. When position updates occur, invoke `TvPlayNextManager.updateProgress()`.
4. **Coil TV Configuration**:
   Detect TV mode via `UiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION` and conditionally apply `Bitmap.Config.RGB_565` and reduced cache limits.

---

## 6. Reference App Implementations & Architectural Benchmark Summary

| Feature | Jellyfin Android TV | Kodi | Stremio TV | SmartTube | TiviMate |
|---|---|---|---|---|---|
| **UI Framework** | Leanback Views & Hybrid Compose | Custom C++ Rendering Engine | Jetpack Compose + Leanback | Custom Leanback Views + Optimized RecycledViewPool | Custom Highly-Optimized Android Views |
| **Play Next Sync** | `RecommendationSyncWorker` + Playback End Hook | Native Android TV Rec Service | Custom Sync Service | Watch History Sync | N/A (Live IPTV focus) |
| **Global Voice Search** | `SearchableProvider` with Local Index | Custom Voice Search Provider | Voice intent receiver | Direct YouTube Voice Search Interceptor | Custom EPG Search Interceptor |
| **Color Remote Keys** | Partial | Full (via keymap.xml) | None | Customizable Shortcuts | Full Custom Remapping (Audio, Subs, EPG) |
| **Refresh Rate Matching** | Display.Mode API switching | Complete custom display mode switching | ExoPlayer AFR | Custom AFR with resolution scaling | Full AFR (23.976 / 24 / 50 / 60 Hz) |
| **Low-RAM Survival** | Low bitmap cache size | Manual RAM buffer tuning in advancedsettings.xml | Stream cache size limit | RGB_565 + strict RecycledViewPool sharing | Extreme low-overhead view caching |
