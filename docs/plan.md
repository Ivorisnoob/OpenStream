# Plan: Multi-Provider Streaming Architecture ("Option 1")

**Status:** Proposed · **Author:** Agent-assisted planning session · **Target:** OpenStream Android app

---

## Table of Contents

1. [Goal](#1-goal)
2. [Why: current-state problems](#2-why-current-state-problems)
3. [How competitor apps achieve "every quality, many servers"](#3-how-competitor-apps-achieve-every-quality-many-servers)
4. [Target architecture](#4-target-architecture)
5. [Domain layer: contracts and models](#5-domain-layer-contracts-and-models)
6. [ID mapping: TMDB ↔ streaming-provider IDs](#6-id-mapping-tmdb--streaming-provider-ids)
7. [Phase-by-phase implementation plan](#7-phase-by-phase-implementation-plan)
8. [Provider specifications](#8-provider-specifications)
9. [Server-picker UX design (Material 3 Expressive)](#9-server-picker-ux-design-material-3-expressive)
10. [Playback and download integration changes](#10-playback-and-download-integration-changes)
11. [Resilience strategy: racing, health, failover](#11-resilience-strategy-racing-health-failover)
12. [Testing plan](#12-testing-plan)
13. [Risks and mitigations](#13-risks-and-mitigations)
14. [Out of scope / future phases](#14-out-of-scope--future-phases)

---

## 1. Goal

Replace the single hard-coded Vidking embed + hidden-WebView sniffer with a
**multi-provider streaming layer**:

- The app asks **N independent sources in parallel** for playable links for a given episode.
- Results are merged into a **server picker**: every entry shows provider name,
  quality badge (`360p`…`1080p`, `HD`, `4K` when available), audio type (sub/dub),
  and live/dead status.
- If one provider dies (they all die eventually), the app keeps working through the others.
- The user can switch servers mid-session without leaving the player.

Non-goals (for this plan): torrent/debrid support, account sync, scraping sites
that require CAPTCHA solving. Those are listed in [§14](#14-out-of-scope--future-phases).

---

## 2. Why: current-state problems

Verified against the codebase today:

| # | Problem | Where |
|---|---------|-------|
| P1 | Extraction lives **inside a composable**. A hidden `WebView` intercepts network requests during composition. This violates our own layering rules (presentation must not do networking). | `PlayerScreen.kt:296–401` |
| P2 | **Single point of failure.** One embed host (`vidking.net`). If it changes domains, blocks WebViews, or adds ads/popups, the whole app cannot play anything. | `PlayerScreen.kt:167–173` |
| P3 | **No quality choice.** Whatever URL the sniffer catches first wins (MP4 preferred over m3u8, otherwise first-seen). No resolution list, no sub/dub selection. | `PlayerScreen.kt:351–369` |
| P4 | **Hard-coded `Referer`** baked into the shared download stack and player HTTP datasource. Every new provider needs its own headers, so this must become per-source metadata. | `DownloadModule.kt:45`, `DownloadRepositoryImpl.kt:61`, `ExoPlayerView.kt:181` |
| P5 | The JS XHR/fetch hook (`AndroidSniffer`) parses JSON opportunistically but the parsed payload is discarded (`onMetadataFound` only logs). All the quality/source data Vidking actually exposes is thrown away. | `PlayerScreen.kt:232–241, 311–347` |
| P6 | Downloads depend on the sniffed URL being available before the user taps download; there is no provider contract to re-resolve links later. | `PlayerViewModel.kt:111–127` |

---

## 3. How competitor apps achieve "every quality, many servers"

Findings from researching open-source apps (CloudStream, Dantotsu, Aniyomi,
Miruro, zenshin, Moopa, Cinema HD-class APKs):

1. **They never rely on one source.** CloudStream/Dantotsu/Aniyomi load dozens of
   scraper extensions; web apps (Miruro, zenshin) query multi-provider APIs.
2. **The server list is a merged result set.** Each source returns its own link(s)
   with metadata; the app renders them side by side and lets the user pick.
3. **Quality labels come from the source payload**, not guesswork: providers return
   structured arrays like `{ url, quality: "1080p", isDub: false }`.
4. **Failures are invisible.** Sources that time out or error simply don't appear;
   if the selected server stalls, apps offer the next one immediately.
5. **True 4K generally comes from torrent/debrid tiers** — out of scope here, but the
   provider contract below is designed so a debrid provider could be added later
   without breaking anything.

Conclusion: we replicate pattern (1)–(4) with a small, clean provider layer.

---

## 4. Target architecture

```
┌──────────────────────────────────────────────────────────────────┐
│ presentation/player                                              │
│   PlayerScreen        PlayerViewModel                            │
│     • server picker UI      • exposes ServersState               │
│     • quality badges        • selectServer()                     │
│     • NO WebView            • auto-picks best server             │
└──────────────┬───────────────────────────────────────────────────┘
               │ domain interfaces only
┌──────────────▼───────────────────────────────────────────────────┐
│ domain                                                           │
│   StreamingRepository                                            │
│   VideoServer / StreamQuality / StreamSubtitle (models)          │
│   MediaIdentity (tmdbId, imdbId, anilistId, malId, title, se/ep) │
└──────────────┬───────────────────────────────────────────────────┘
               │ implemented by
┌──────────────▼───────────────────────────────────────────────────┐
│ data/streaming                                                   │
│   StreamingRepositoryImpl                                        │
│     ├─ IdMappingService        (TMDB→IMDb/AniList, §6)           │
│     └─ List<StreamProvider>    injected, ordered                 │
│          ├─ VidkingWebViewProvider   (existing behavior, boxed)  │
│          ├─ ConsumetProvider         (self-hosted REST)          │
│          ├─ HianimeApiProvider       (aniwatch-api REST)         │
│          └─ (future providers plug in here)                      │
└──────────────────────────────────────────────────────────────────┘
```

Key decisions:

- **D1 — Extraction leaves the UI.** The WebView becomes an implementation detail
  of `VidkingWebViewProvider`, running inside `data/streaming`. Presentation never
  sees a WebView again.
- **D2 — Providers are stateless and parallel-safe.** Each implements one method:
  *given a `MediaIdentity`, return `Result<List<VideoServer>>`*. No shared mutable
  state; each call gets its own lightweight resources.
- **D3 — Repository merges, dedupes, ranks.** The ViewModel receives one clean list.
- **D4 — Per-server request headers** travel with the `VideoServer` so ExoPlayer and
  the downloader stop assuming Vidking's `Referer`.
- **D5 — Keep TMDB as the spine.** Discovery/details/history stay exactly as they
  are. Only playback resolution gains the mapping step.

New packages (all under `com.ivor.openstream`):

```
domain/repository/StreamingRepository.kt
domain/model/VideoServer.kt
domain/model/StreamQuality.kt
domain/model/MediaIdentity.kt
data/streaming/StreamingRepositoryImpl.kt
data/streaming/IdMappingService.kt
data/streaming/StreamProvider.kt
data/streaming/providers/VidkingWebViewProvider.kt
data/streaming/providers/ConsumetProvider.kt
data/streaming/providers/HianimeApiProvider.kt
data/remote/consumet/ConsumetApi.kt (+ DTOs)
data/remote/hianime/HianimeApi.kt (+ DTOs)
di/StreamingModule.kt
```

---

## 5. Domain layer: contracts and models

```kotlin
// domain/model/StreamQuality.kt
enum class StreamQuality(val label: String, val rank: Int) {
    Q360("360p", 0),
    Q480("480p", 1),
    Q720("720p", 2),
    Q1080("1080p", 3),
    HD("HD", 3),          // unlabeled HD sources
    Q1440("1440p", 4),
    Q2160("4K", 5),
    UNKNOWN("", 2);       // rank it mid-tier rather than hiding it

    companion object {
        fun parse(raw: String?): StreamQuality = when (raw?.lowercase()) {
            "360p" -> Q360; "480p" -> Q480; "720p" -> Q720
            "1080p" -> Q1080; "1440p" -> Q1440; "2160p", "4k" -> Q2160
            "hd", "default" -> HD
            else -> UNKNOWN
        }
    }
}

// domain/model/VideoServer.kt
data class VideoServer(
    val id: String,                  // stable within a resolve session
    val providerName: String,        // "Hianime", "Consumet/Zoro", "Vidking"
    val name: String,                // human label e.g. "Vidstream", "MegaCloud"
    val url: String,                 // direct .m3u8 / .mp4
    val quality: StreamQuality,
    val isDub: Boolean = false,
    val headers: Map<String, String> = emptyMap(),   // Referer/UA/etc. for THIS url
    val subtitleUrl: String? = null, // provider-supplied subs if any
    val isDownloadable: Boolean = true
)

// domain/model/MediaIdentity.kt
data class MediaIdentity(
    val tmdbId: Int,
    val tmdbType: String,            // "tv" | "movie"
    val imdbId: String? = null,
    val anilistId: Int? = null,
    val malId: Int? = null,
    val title: String,
    val originalTitle: String? = null,
    val season: Int = 1,
    val episode: Int = 1,
    val year: Int? = null            // release year, disambiguates remakes
)

// domain/repository/StreamingRepository.kt
interface StreamingRepository {
    /** Resolve ALL servers for an episode across enabled providers. */
    suspend fun getServers(identity: MediaIdentity): List<VideoServer>

    /** Re-validate a previously returned server before playback/download. */
    suspend fun refreshServer(server: VideoServer): Result<VideoServer>
}
```

```kotlin
// data/streaming/StreamProvider.kt
interface StreamProvider {
    val id: String                    // "hianime", "consumet", "vidking-webview"
    val displayName: String
    val isEnabled: Boolean            // reads settings; lets users disable a dead one
    suspend fun resolve(identity: MediaIdentity): Result<List<VideoServer>>
}
```

Design notes:

- `getServers` returns `List<VideoServer>` directly (not `Result`) because partial
  failure is normal: one dead provider must not fail the call. Failures are logged
  and surfaced as counts ("3 servers from 2 of 3 sources").
- `refreshServer` exists because stream URLs expire (typically 6–24 h). Downloads
  will call it instead of trusting stored URLs.

---

## 6. ID mapping: TMDB ↔ streaming-provider IDs

This is the hardest part of the whole plan. Providers key off their own site IDs
(Hianime slug, Consumet provider IDs) or AniList/MAL IDs; we hold TMDB IDs.
Strategies, in order of preference:

### S1 — TMDB external IDs (trivial, do always)

`GET https://api.themoviedb.org/3/{type}/{tmdbId}/external_ids` returns `imdb_id`
and (for TV) `tvdb_id`. One Retrofit addition to the existing `TmdbApi`. IMDb IDs
unlock Stremio-style ecosystems later and some providers accept them directly.

### S2 — Anify mappings (best AniList coverage)

Anify publishes mapping tables between AniList ↔ MAL ↔ TVDB/anime IDs
(`https://anify.to/mappings` style endpoints used by elyzen/ayoko/reveraki).
Fetch once, cache in Room (small static table, refreshed weekly). TMDB→AniList is
not always present, so chain S1's IMDb/TVDB ID where needed.

### S3 — Title-search fallback (always works, needs care)

Every provider supports text search. Match pipeline:

1. Search `originalTitle` (romaji titles index better than English ones).
2. Filter candidates by start-year proximity (±1).
3. For TV: pick result whose episode count ≥ requested episode number.
4. Cache `(tmdbId, season) → providerMediaId` in Room so search runs once per show.

### S4 — anime-offline-database snapshot (bulk option)

manami-project's `anime-offline-database` ships cross-source ID mappings as a JSON
dataset (~50 MB). Overkill for now; noted as the upgrade path if S2+S3 prove
insufficient.

**Decision: implement S1 + S3 at launch, add S2 behind the same
`IdMappingService` interface.** The service caches everything in Room:

```kotlin
// data/local/entity/IdMappingEntity.kt
@Entity(tableName = "id_mappings")
data class IdMappingEntity(
    @PrimaryKey val cacheKey: String,          // "hianime:tv:94605:1"
    val providerId: String,
    val providerMediaId: String,
    val resolvedAt: Long
)
```

---

## 7. Phase-by-phase implementation plan

Each phase compiles and ships independently. Phases 1–3 contain no visible UI
change (behavior identical to today), which keeps risk contained.

### Phase 0 — Prep (½ day)

- [ ] Add Room entity/DAO above + bump nothing in `libs.versions.toml`
      (Room already present at 2.6.1; register entity in `AppDatabase`).
- [ ] Add `external_ids` endpoint to `TmdbApi` + DTO.
- [ ] Create empty `data/streaming` package + `StreamingModule` Hilt module.
- [ ] Extract the magic strings (`"Referer" to "https://www.vidking.net/"`,
      `"tv"/"movie"` literals) into constants.

### Phase 1 — Contracts (½ day)

- [ ] `StreamQuality`, `VideoServer`, `MediaIdentity`, `StreamProvider`,
      `StreamingRepository` exactly as specified in §5.
- [ ] Unit tests for `StreamQuality.parse`.

### Phase 2 — Box the existing behavior: `VidkingWebViewProvider` (1–2 days)

Move the sniffer wholesale out of `PlayerScreen.kt`:

- [ ] Port the WebView logic (JS injection, `shouldInterceptRequest`) into a plain
      class that takes a `Context` (application context), loads the embed URL
      off-screen, and emits results via a callback/`suspendCancellableCoroutine`.
- [ ] Parse what the JS hook already captures (`sources[]`, `file`, quality fields)
      into **multiple** `VideoServer`s instead of discarding them (fixes P5).
      Vidking-style payloads typically expose per-quality entries — surface them.
- [ ] Sniffed subtitle URLs attach as `VideoServer.subtitleUrl` alongside the
      existing wyzie subtitles.
- [ ] Timeout guard: 15 s, then report zero servers (the next provider covers).
- [ ] Delete the WebView block from `PlayerScreen.kt`; the screen now renders a
      temporary "Resolving streams…" expressive loading state driven by
      `PlayerViewModel.servers` (this phase temporarily has a single provider,
      so UX matches today's).
- [ ] Remove `webInterface`/sniffer remnants; net −200 LOC from PlayerScreen.

### Phase 3 — First REST provider: `HianimeApiProvider` (2–3 days)

Targets a self-hostable aniwatch-api instance (spec in §8.2).

- [ ] `HianimeApi` Retrofit interface + DTOs (`search`, `episodes`, `servers`,
      `sources` incl. `.m3u8` lists and `subtitle` files).
- [ ] Resolution flow: cached mapping lookup → S3 title search → episode-list
      match by number → `servers` → `sources` per candidate server (top 2).
- [ ] Map each source to `VideoServer(quality = parse(quality), isDub =
      category=="dub", headers = mapOf("Referer" to <server-specific>))`.
      Hianime sources require a `Referer` of the streaming host — capture it from
      the sources response rather than hard-coding.
- [ ] Base URL configurable via `BuildConfig.HIANIME_BASE_URL` with a debug
      default pointing at a local/self-hosted instance (no public-instance
      dependency committed into the app).

### Phase 4 — `StreamingRepositoryImpl` merge engine (1–2 days)

- [ ] Launch all enabled providers concurrently under `coroutineScope { }` +
      `async`, each wrapped in `withTimeout(20_000)` and individual try/catch.
- [ ] Merge results: dedupe by `(url)`, rank by `quality.rank` desc then provider
      order, tag failures for telemetry counters.
- [ ] Emit progress via callback Flow so the UI can show "found 4 servers…" while
      slower providers still race (perceived speed matters — see §9).
- [ ] Persist last-good server per `(tmdbId, season, episode)` in DataStore so
      replays skip straight to the known-working entry (revalidated via
      `refreshServer` HEAD request).

### Phase 5 — Server-picker UI (2–3 days)

Spec in §9. Wire-up:

- [ ] `PlayerViewModel`: `ServersState` (Idle / Resolving(progress) / Ready(list,
      activeId) / Empty(failedProviders)), `selectServer()`,
      `retryResolution()`.
- [ ] `PlayerScreen`: remove all WebView code paths (already gone after Phase 2),
      add picker trigger button in `PlayerControls`, auto-play best-ranked server
      on Ready.
- [ ] On playback error (`Player.Listener.onPlayerError`): mark server dead,
      auto-switch to next-best, show a brief toast/surface — mirrors how
      CloudStream behaves and removes today's silent black-screen failure mode.

### Phase 6 — Download integration (1–2 days)

- [ ] `HlsDownloadService` consumes `VideoServer.headers` dynamically; delete the
      global Vidking `Referer` from `DownloadModule` and
      `DownloadRepositoryImpl`.
- [ ] Store `providerId` + enough info to `refreshServer()` on the download row so
      expired downloads can re-resolve before retry (new nullable columns +
      Room migration vN+1).
- [ ] Server picker gets a "Download from this server" affordance.

### Phase 7 — Optional second REST provider: `ConsumetProvider` (1–2 days)

Only if Hianime coverage proves insufficient for movies/non-anime TV — Consumet's
`zoro` provider overlaps Hianime; its value-add is `gogoanime` (older catalog,
dubs) and movie providers. Same shape as Phase 3; spec in §8.3.

### Effort summary

| Phase | Duration | Risk |
|---|---|---|
| 0 Prep | ½ d | none |
| 1 Contracts | ½ d | none |
| 2 WebView boxing | 1–2 d | **medium** — touches working playback; test on device |
| 3 Hianime provider | 2–3 d | medium — external API shape drift |
| 4 Merge engine | 1–2 d | low |
| 5 Picker UI | 2–3 d | low |
| 6 Downloads | 1–2 d | medium — Room migration |
| 7 Consumet (optional) | 1–2 d | low |

Total ≈ 9–15 working days end-to-end, with a working app after every phase.

---

## 8. Provider specifications

### 8.1 VidkingWebViewProvider (internal, no network spec needed)

Same embeds as today (`embed/movie/{tmdbId}`, `embed/tv/{tmdbId}/{s}/{e}`), same
sniffing, but returns structured results and honors timeouts. Kept as the
lowest-effort fallback since it requires no ID mapping (accepts TMDB IDs natively).

### 8.2 HianimeApiProvider — aniwatch-api compatible

Self-hostable Node scraper for hianime.to; the de-facto standard REST shape used
by Miruro/zenshin/Moopa clones. Key routes (v2):

| Endpoint | Purpose | Notes |
|---|---|---|
| `GET /api/v2/hianime/search?q={q}&page=1` | title search → `animeId` slugs like `frieren-beyond-journeys-end-184636` | S3 matching input |
| `GET /api/v2/hianime/anime/{id}/episodes` | numbered episode list → `episodeId`s like `?ep=1184960` | maps season/episode |
| `GET /api/v2/hianime/episode/servers?animeEpisodeId={ep}` | lists `sub`/`dub`/`raw` categories × server names (`Vidstream`, `MegaCloud`, …) with `serverName`+`file` hashes | feeds the picker |
| `GET /api/v2/hianime/episode/sources?animeEpisodeId={ep}&server={name}&category=sub\|dub` | final payload: `sources[].{url, quality}`, `subtitles[].{url, lang}` | m3u8 + burned-in subs |

Response sketch (sources):

```json
{
  "success": true,
  "sources": [
    { "url": "https://…/playlist.m3u8", "quality": "default", "isM3U8": true }
  ],
  "servers": [ { "serverName": "Vidstream" } ],
  "subtitles": [ { "url": "https://…en.vtt", "lang": "English" } ]
}
```

Headers: stream URLs require `Referer` set to the *player page* host (returned in
payload/observed per deployment) and a browser-like User-Agent — carried in
`VideoServer.headers`, never hard-coded globally.

### 8.3 ConsumetProvider — consumet.ts API (self-hosted)

Public `api.consumet.org` is demo-only (5-hour access tokens, 30 req/min) —
self-host on Vercel/Docker per upstream docs, or use any community instance the
user configures in Settings.

| Endpoint | Purpose |
|---|---|
| `GET /anime/zoro/{query}?page=1` | search → `id`, `subOrDub` |
| `GET /anime/info?id={id}` | details + `episodes[].id` |
| `GET /anime/watch/{episodeId}` | `sources[].{url, quality, isDub}`, `headers.Referer` (explicitly provided!), `subtitles[]` |

Consumet is unmaintained upstream (last meaningful commits ~2023) — treat as
best-effort; its explicit `headers` field makes it the easiest integration, which
is why it stays optional-but-cheap.

### 8.4 Future slots

- **MiruroAPI-compatible** edge worker (12 providers, XOR-decoded pipe) — strongest
  uptime in testing but youngest project; revisit after Phase 7.
- **Debrid providers** (Real-Debrid/TorBox REST) for genuine 4K — §14.

---

## 9. Server-picker UX design (Material 3 Expressive)

The picker is the user-visible payoff; it must feel native to OpenStream, not like
a debug menu.

**Trigger:** a server chip in the player controls bar
(`PlayerControls.kt`) showing `ServerName · 1080p`; tapping opens a modal bottom
sheet.

**Sheet layout (bottom sheet, `ExpressiveShapes` large-radius top corners):**

- Header row: `Display/headlineSmall` title "Servers", trailing count
  "7 sources".
- Auto-select banner: thin `surfaceContainerHigh` strip —
  "Auto · Best available" describing ranking (quality ↓, sub before dub).
- Server list: full-width tonal list items, each containing:
  - Leading icon container (`primaryContainer`, morphing shape on selection):
    play icon for video, download icon variant for downloadable-only entries.
  - Two-line body: **provider + server name** (`titleMedium`, bold), caption line
    `1080p · SUB · HLS` with quality rendered as a small filled badge
    (`tertiaryContainer` for ≥1080p, neutral otherwise; `4K` gets the expressive
    treatment — primary container, bold).
  - Trailing state: check icon on the active server; subtle progress spinner on
    the one currently buffering.
- While resolving: `LoadingIndicator` + live counter "Searching sources… 3 found",
  with already-found servers tappable immediately (don't gate on slowest provider).
- Empty/error state: short confident copy — "No servers responded. Retry?" with a
  filled retry button; failed provider names listed in `onSurfaceVariant`.

**Motion:** sheet slides with standard predictive-back-aware animation; selection
morphs the leading shape and plays a quick scale-in on the check. No gratuitous
per-item stagger.

---

## 10. Playback and download integration changes

| Concern | Change |
|---|---|
| HTTP headers | Build a `DefaultHttpDataSource.Factory` per playback session seeded from `activeServer.headers` (`ExoPlayerView.kt:181` loses its constant). |
| Cache datasource | Existing `CacheDataSource.Factory` wraps the per-server HTTP factory; unchanged otherwise. |
| Subtitles | Prefer `VideoServer.subtitleUrl`; keep wyzie `SubtitleApi` results appended as today. |
| Downloads | `DownloadRepository.downloadVideo(...)` gains a `headers: Map<String,String>` param; `HlsDownloadService` applies them to its datasource. Remove global Vidking referer (`DownloadModule.kt`). |
| Expiry | Before resuming a download or replaying a >6 h-old link, `refreshServer()`; on 403/404, re-run `getServers` and rematch by URL prefix/provider. |
| Error recovery | `onPlayerError` → mark dead → auto-advance to next server (max 3 hops, then surface picker). |

---

## 11. Resilience strategy: racing, health, failover

1. **Parallel resolve, first-paint-fast.** Render the picker as soon as any
   provider returns; late arrivals append with a soft fade-in.
2. **Per-provider circuit breaker.** After 5 consecutive failures, a provider is
   benched for the session (still retryable manually from Settings → Sources).
3. **Link validation.** `refreshServer` issues a cheap ranged GET (`Range: bytes=0-1`)
   with the server's headers; 200/206 = alive.
4. **Auto-rank heuristics.** Track per-provider success latency in memory; prefer
   historically fastest providers when qualities tie.
5. **Never trust one source.** Even after auto-select succeeds, keep the full list
   one tap away — the entire premise of this plan.

---

## 12. Testing plan

- **Unit:** `StreamQuality.parse`, merge/dedupe/rank logic, mapping cache keys,
  episode-number matcher (season packs, absolute numbering, specials ≤ 0).
- **Integration (JVM + MockWebServer):** each provider against recorded JSON
  fixtures (commit fixtures under `src/test/resources/providers/`).
- **Manual device matrix (required per AGENTS.md for player changes):**
  - Wi-Fi + cellular; background/foreground mid-playback.
  - Airplane-mode during resolve (graceful empty state).
  - Kill a provider base URL (simulate outage) → remaining providers still play.
  - Download from each provider class (HLS w/ headers vs plain MP4), then offline replay.
  - Episode switch, next-episode auto-advance, server switch mid-buffer.

---

## 13. Risks and mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Provider API shapes drift (unmaintained upstreams) | High | Providers isolated behind one interface; fixtures-based tests make drift a compile/test-time catch; circuit breaker hides dead ones. |
| WebView provider breaks silently inside background thread context | Medium | It already ran on main via composition; port carefully, keep `post`-based hop to main for callbacks, add strict 15 s timeout. |
| ID mapping wrong-show errors (playing wrong series) | Medium | Year + episode-count guards (S3); cache invalidation on mismatch report; manual override long-term. |
| Referer/host requirements differ per deployment | Medium | Headers always come from responses, never constants (D4). |
| Legal exposure unchanged in kind but broadened in surface | — | Same posture as today: app hosts nothing; providers configurable; document clearly in README disclaimer. |
| Scope creep toward debrid/torrents | Medium | Explicitly deferred (§14). |

---

## 14. Out of scope / future phases

- **Debrid tier (true 4K/HDR):** Real-Debrid/TorBox REST integration turning
  magnets into direct HTTPS — the provider interface already fits
  (`resolve()` → debrid lookup by IMDb/TMDB → cached-torrent URL). Natural Phase 8.
- **Stremio addon client protocol** (`/manifest.json` + `/stream/...`) — would
  inherit Torrentio/MediaFusion ecosystems; pairs perfectly with S1 IMDb IDs.
- **Settings screen for sources** (enable/disable, custom base URLs, priority).
- **Paging 3, offline mode, tracking sync (AniList)** — unrelated to this plan,
  tracked separately.

---

## Appendix A — File-level change map

| File | Change |
|---|---|
| `presentation/player/PlayerScreen.kt` | **Delete** WebView/sniffer blocks (≈150 LOC); add picker trigger + resolving states |
| `presentation/player/PlayerViewModel.kt` | Add `StreamingRepository`, `ServersState`, selection + retry APIs |
| `presentation/player/components/PlayerControls.kt` | Server chip + quality badge |
| `presentation/player/components/ExoPlayerView.kt` | Accept per-server `headers`; drop constant Referer |
| `data/repository/DownloadRepositoryImpl.kt` | Dynamic headers; store provider info; migration |
| `data/service/HlsDownloadService.kt` | Apply per-request headers |
| `di/DownloadModule.kt` | Remove hard-coded Referer default |
| `di/NetworkModule.kt` | Add `@Named("Hianime")`, `@Named("Consumet")` Retrofit instances (base URLs from `BuildConfig`) |
| `di/StreamingModule.kt` | **New** — binds `StreamingRepository`, provides ordered `List<StreamProvider>` |
| `data/local/AppDatabase.kt` | + `IdMappingEntity`, + download-schema migration |
| `data/remote/TmdbApi.kt` | + `externalIds(type, id)` |

## Appendix B — References (researched Aug 2026)

- recloudstream/cloudstream — extension/server-picker reference architecture
- aniyomiorg/aniyomi-extensions, rebelonion/Dantotsu — extension model
- ghoshRitesh12/aniwatch-api (+ ryanwtf7/hianime-api fork) — Hianime REST shape
- consumet/api.consumet.org + consumet.ts — unified provider API (status: demo-limited)
- Eltik/Anify — successor API + mapping tables
- Shineii86/MiruroAPI — multi-provider edge worker
- manami-project/anime-offline-database — bulk ID mapping dataset
