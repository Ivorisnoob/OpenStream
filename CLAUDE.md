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
data/local         Room: watch later, downloads, watch progress, id mappings
data/repository    Repository implementations (anime, downloads, progress, OpenSubtitles)
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
- Room schema changes need a real `Migration` in `di/DatabaseModule.kt` (current version 6).
  `fallbackToDestructiveMigration` is only a safety net; users' downloads and progress live there.

## How the main features work

- **Sources.** `ExtensionProviderRegistry` turns installed catalog entries into providers.
  `StreamingRepositoryImpl` resolves them in parallel, ranks with `ServerRanker`, and runs
  `fallback` providers only when direct ones return nothing (or on "Find more" / failover).
  Engines: `vidking-direct` (`VidkingDirectApi`, encrypted payload, prefers the master playlist so
  quality switches in-player), `web-embed` and `vidking-webview` (`WebEmbedResolver`, hidden
  WebView that records media requests).
- **Catalog.** `extensions/index.json` is published; `app/src/main/assets/extensions/official-repo.json`
  must be a byte-identical copy (`OfficialCatalogTest` checks). Bundled and fetched copies are merged
  per entry by `versionCode`. Contributor guide: `extensions/README.md`; format: `docs/EXTENSIONS.md`.
- **Playback.** `PlaybackSession` owns one `ExoPlayer` for the whole app. `ExoPlayerView` attaches
  to it and never releases it; leaving the player keeps playback going in `MiniPlayer`. The session
  also records watch progress (`WatchProgressRepository`) and queues the next episode for
  Continue Watching. Debug builds log player events under `EventLogger`.
- **Player UI.** Controls in `PlayerControls`; settings and sources share `PlayerPanelHost`
  (bottom sheet inline, in-player side panel in fullscreen so immersive mode survives).
- **Subtitles.** `OpenSubtitlesRepository` (keyless legacy REST API) plus any the stream carries.
  Files are gzipped; the player decompresses and strips promo cues.
- **Network.** `AppDns` (DNS-over-HTTPS, default AdGuard, chosen in Settings) backs every OkHttp
  client and Coil's image loader (`OpenStreamApp`), because some ISPs block TMDB at the DNS level.
  Media3 playback/downloads and WebView sources still use the system resolver.
- **Settings.** `AppSettingsStore` (SharedPreferences) holds theme, dynamic color, DNS and
  Wi-Fi-only downloads; `MainActivity` applies the theme.
- **Downloads.** Everything goes through Media3's `DownloadManager` (`DownloadRepositoryImpl`):
  queue resolves sources in the app scope, live progress from the manager, pause/resume/retry,
  one rendition up to 1080p from master playlists. Offline playback reads the same cache.

## Known external constraints

- `vidking.net` (the web embed) is down; the Vidking API used by `vidking-direct` works.
- Vidking dub routes (English/Hindi) return links locked to Vidking's server IP, so their CDN often
  answers 403 on devices. The player falls back to the previous stream when a picked source fails.
- Several Vidking routes currently 404/500 for most titles; Yoru (`cdn`) is the reliable one.
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
- Commit and push only when asked. End commit messages with the attribution trailer in use.
- Compile before calling work done, and say plainly what was and wasn't verified.
- Treat playback and source changes as high risk; name any undocumented third-party behavior they
  rely on.
