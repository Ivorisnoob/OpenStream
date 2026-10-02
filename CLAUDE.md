# OpenStream

Native Android app for discovering and streaming movies, series and anime. Metadata comes from TMDB;
streams come from source extensions listed in a JSON catalog. Anime gets dedicated rows and handling
(original vs. dub audio), but the app is not anime-only. Code names like `AnimeDto`,
`AnimeRepository` and `animeId` predate that and are fine to keep.

When a doc and the code disagree, trust the code, then fix the doc.

## Build and run

```sh
./gradlew :app:compileDebugKotlin     # fastest compile check
./gradlew installDebug                # build + install (applicationId com.ivor.openstream.debug)
./gradlew :app:testDebugUnitTest      # JVM unit tests
```

`local.properties` holds `TMDB_API_KEY` (falls back to `DEMO_KEY`) and optionally
`VIDKING_API_BASE_URL`. `assembleRelease` signs when `OPENSTREAM_KEYSTORE_PATH`,
`OPENSTREAM_KEYSTORE_PASSWORD`, `OPENSTREAM_KEY_ALIAS` and `OPENSTREAM_KEY_PASSWORD` are set in the
environment. `.github/workflows/release-apk.yml` builds a signed APK artifact on the owner's pushes
to `main` and on manual dispatch (any branch); there are no other CI checks.

## Toolchain

Versions live only in `gradle/libs.versions.toml`; never hard-code them in Gradle files.

- Gradle 9.8, AGP 9.4 with **built-in Kotlin**: the app module does not apply
  `org.jetbrains.kotlin.android` and has no `kotlinOptions`. The root build declares the Kotlin plugin
  only to pin its version (2.3.21).
- compileSdk 37, targetSdk 36, minSdk 26.
- Compose BOM 2026.09.00 with **Material 3 `1.5.0-alpha29`** (Expressive). KSP, Hilt, Room 2.8,
  kotlinx-serialization, Retrofit/OkHttp, Coil 3, Media3 1.3.
- Alpha APIs change between releases. Before using a Material 3 API, confirm it exists in alpha29
  (for example, `javap` on the cached AAR under `~/.gradle/caches/modules-2`). Known change: `Slider`
  with custom `thumb`/`track` is now state-based (`rememberSliderState`).

## Architecture

Single activity (`MainActivity`), Navigation Compose, Hilt everywhere.

```
data/remote        TMDB (TmdbApi), GitHub releases, DTOs
data/local         Room: profiles, watch later, custom lists, downloads, watch progress, id mappings
data/repository    Repository implementations (anime, downloads, progress, lists, OpenSubtitles)
data/cast          Cast options, media item converter, LAN proxy the receiver streams through
data/streaming     Source resolution, extension -> provider registry, id mapping
data/extensions    Extension catalog: repos, cache, parser, ranking, bundled copy
data/service       Media3 download service
domain             Models and repository interfaces
presentation       Screens + ViewModels; player/session holds the app-wide player
ui/theme           Colors, type, ExpressiveShapes
```

Rules:
- Composables render state and forward intent. ViewModels own screen state as `StateFlow`.
  Networking and persistence stay in `data/`.
- Routes and arguments live in `presentation/navigation/AppNavigation.kt`.
- Room schema changes need a real `Migration` in `di/DatabaseModule.kt` (current version 8).
  `fallbackToDestructiveMigration` is only a safety net; users' downloads and progress live there.

## How the main features work

- **Sources.** `ExtensionProviderRegistry` turns installed catalog entries into providers.
  `StreamingRepositoryImpl` resolves them in parallel, ranks with `ServerRanker`, and runs
  `fallback` providers only when direct ones return nothing, or have found nothing after 8 s (or on
  "Find more" / failover). Each provider gets 25 s, enforced by awaiting rather than by cancelling
  (OkHttp's blocking `execute()` ignores coroutine cancellation); the IMDb id lookup gets 8 s.
  Cancelled resolutions record nothing; five failures in a row skip a provider for two minutes.
  Engines: `vidking-direct` (`VidkingDirectApi`, encrypted payload, prefers the master playlist so
  quality switches in-player), `web-embed` and `vidking-webview` (`WebEmbedResolver`: first a native
  pass through `HosterExtractors` (Filemoon, StreamWish/VidHide, Voe, Mp4Upload, Vidmoly, ok.ru) when
  the embed is or frames a known hoster, else a hidden WebView that records media requests).
  Anime engines (`data/streaming/anime`): `anikoto`, `reanime`, `animepahe`, `fouranimo`, `animegg`.
  `VideoServer` can carry a MIME hint (HLS for URLs without `.m3u8`) and the source's own intro/outro
  times, which the player prefers over AniSkip. `AnimeEpisodeMapper`
  maps TMDB season/episode to an AniList episode (ani.zip + AniList GraphQL, both keyless);
  megaplay embeds decrypt with a fixed AES key; `ImagePrefixStrippingDataSource` strips the fake
  PNG header some anime CDNs put before TS segments.
- **Catalog.** `extensions/index.json` is published; `app/src/main/assets/extensions/official-repo.json`
  must be a byte-identical copy (`OfficialCatalogTest` checks). Bundled and fetched copies are merged
  per entry by `versionCode`. Contributor guide: `extensions/README.md`; format: `docs/EXTENSIONS.md`.
- **Playback.** `PlaybackSession` owns one `ExoPlayer` for the whole app. `ExoPlayerView` attaches
  to it and never releases it; leaving the player keeps playback going in `MiniPlayer`. The session
  also records watch progress (`WatchProgressRepository`) and queues the next episode for
  Continue Watching. Debug builds log player events under `EventLogger`.
- **Casting.** `PlaybackSession` also owns a Media3 `CastPlayer` (Default Media Receiver, options in
  `CastOptionsProvider`, initialised from `MainActivity`). `activePlayer` is the TV while casting;
  connecting moves the item there at the phone's position, disconnecting brings it back paused.
  Everything the receiver loads goes through `CastMediaProxy`, a small HTTP server on the phone:
  stateless URLs carry the target and headers, HLS playlists are rewritten, segments are read through
  the download cache and `ImagePrefixStrippingDataSource`, subtitles are served as WebVTT. The
  phone must stay on the TV's network. `PlayerScreen` swaps `ExoPlayerView` for `CastPlaybackView`.
- **Player UI.** Controls in `PlayerControls`; settings and sources share `PlayerPanelHost`
  (bottom sheet inline, in-player side panel in fullscreen so immersive mode survives).
- **Subtitles.** `CombinedSubtitleRepository`: `OpenSubtitlesRepository` (keyless legacy REST API)
  and `SubSourceRepository` (keyless, mirrors subsource.net's own API: IMDb search -> list ->
  download token -> zip), plus any the stream carries. `SubtitleFetcher` downloads and unwraps
  them (gzip, zip, charset) for both the player and the cast proxy; promo cues are stripped.
  `SavedSubtitleRepository` keeps subtitles on the device (`filesDir/subtitles/<download id>/`, files
  plus `index.json`), saved from Downloads (any language, or an imported file). Downloads also
  record the stream's own subtitles there (`stream.json`) so the sheet can offer them; older
  downloads re-resolve the stream sources to find them. `DownloadSubtitleSaver` saves every
  subtitle in the languages chosen in Settings (default English) when a download starts: the
  stream's own, plus OpenSubtitles/SubSource unless turned off. The player lists
  them first for that title/episode, online or offline; `SubtitleFetcher` reads `file:` URLs only
  from that folder because the cast proxy fetches whatever URL it is given. Deleting a download
  deletes its saved subtitles.
- **Network.** `AppDns` (DNS-over-HTTPS, default AdGuard, chosen in Settings) backs every OkHttp
  client and Coil's image loader (`OpenStreamApp`), because some ISPs block TMDB at the DNS level.
  Media3 playback/downloads and WebView sources still use the system resolver.
- **Settings.** `AppSettingsStore` (SharedPreferences) holds theme, dynamic color, DNS,
  Wi-Fi-only downloads and the subtitle languages saved with downloads; `MainActivity` applies the
  theme.
- **Skip intro.** `SkipTimesRepository`: `AnimeEpisodeMapper` gives the MAL id and the episode within
  that entry (AniList title search only as a fallback), AniSkip v2 gives the intro/recap/credits
  times (anime only), and the player picks the submission timed on the closest file length. All
  keyless public APIs with no stability promise.
- **Profiles.** `profiles` table (seeded with profile 1); the active id is in `AppSettingsStore`.
  Watch Later, progress, hidden titles and custom lists carry `profileId`; their DAOs take it and
  the repositories follow the active id with `flatMapLatest`. Downloads are device-wide.
  Switching profile stops playback. Kids profiles: `KidsContentFilter` adds TMDB certification
  filters to discover calls and checks everything else against the US rating (G/PG,
  TV-Y..TV-PG; unrated is hidden); Home hides Settings and leaving takes a hold on the avatar.
  `include_adult=false` is added to every TMDB request.
- **Lists.** Watch Later plus user lists (`CustomListRepository`, `custom_lists` tables); Details has
  "Add to list" and "Mark all watched", the Saved tab shows the lists.
- **Backup / diagnostics.** `LibraryBackup` (JSON, merge on restore) and `Diagnostics` (crash files
  in `filesDir/crashes`, recorder installed in `OpenStreamApp`) back the Settings entries.
- **Deep links.** `MainActivity` is `singleTask`; `DeepLinks` turns TMDB links (VIEW or shared text)
  into Details, `AppShortcut` handles launcher shortcuts.
- **Downloads.** Everything goes through Media3's `DownloadManager` (`DownloadRepositoryImpl`):
  queue resolves sources in the app scope, live progress from the manager, pause/resume/retry,
  one rendition up to 1080p from master playlists. Offline playback reads the same cache.

## Known external constraints

- `vidking.net` (the web embed) is down; the Vidking API used by `vidking-direct` works.
- Vidking dub routes (English/Hindi) return links locked to Vidking's server IP, so their CDN often
  answers 403 on devices. The player falls back to the previous stream when a picked source fails.
- Several Vidking routes currently 404/500 for most titles; Yoru (`cdn`) is the reliable one.
- AniList (about 90 requests a minute) and AniSkip are unauthenticated and undocumented as a
  contract; skip buttons simply don't appear when they fail.
- Casting relies on the receiver being allowed to load `http://` media from the phone's LAN address
  (the Default Media Receiver page is HTTPS). Networks with client isolation block it.
- Wyzie subtitles now require an API key and were removed. Don't add features that need users to
  supply API keys.

## Design

Material 3 Expressive first: prefer expressive components (`LoadingIndicator`, `SegmentedListItem`,
`ToggleButton`, carousels, `HorizontalFloatingToolbar`) over hand-built equivalents. Use
`MaterialTheme.colorScheme` roles and `ExpressiveShapes`; no random hard-coded colors. Artwork does
real work on Home, Details and the player. Keep edge-to-edge, 48dp touch targets, headings marked for
screen readers, and meaningful content descriptions. Design references (guidelines, M3 Expressive
guides, component list) live in `docs/design/`.

## Working agreements

- Don't write tests for their own sake, or temporary test files for probing. Probe with one-off
  scripts outside the repo; add tests when behavior needs guarding.
- Don't drive the app on the emulator unless asked; the maintainer tests. `installDebug` when asked.
- Never use emojis: not in code, comments, commits, PRs, changelogs or messages sent anywhere.
- Commit and push only when asked. End commit messages with the attribution trailer in use.
- Compile before calling work done, and say plainly what was and wasn't verified.
- Treat playback and source changes as high risk; name any undocumented third-party behavior they
  rely on.
