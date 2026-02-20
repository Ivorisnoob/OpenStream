# OpenAnime - Agent Context Document

> **Purpose:** This document gives any AI agent full context to understand, navigate, modify, and extend the OpenAnime codebase without prior knowledge.

---

## 1. What is OpenAnime?

OpenAnime is a native Android application for browsing and streaming anime. It fetches metadata (titles, posters, seasons, episodes) from **TMDB (The Movie Database)** and streams video via the **Vidking** embeddable player. It is written entirely in **Kotlin** using **Jetpack Compose** for the UI and follows **Material Design 3 Expressive** principles.

---

## 2. Tech Stack

| Layer              | Technology                                                   |
|--------------------|--------------------------------------------------------------|
| **Language**        | Kotlin 2.0.21                                                |
| **UI Framework**    | Jetpack Compose (Material 3 Expressive, `1.5.0-alpha13`)     |
| **Theming**         | `MaterialExpressiveTheme` (not plain `MaterialTheme`)        |
| **DI**              | Hilt (`2.51.1`) with KSP (`2.0.21-1.0.27`)                  |
| **Networking**      | Retrofit `2.11.0` + OkHttp `4.12.0`                         |
| **Serialization**   | Kotlinx Serialization `1.6.3`                                |
| **Image Loading**   | Coil 3 (`3.0.0-rc01`)                                       |
| **Video Playback**  | Media3 ExoPlayer `1.3.1` (native player) + WebView (Vidking) |
| **Navigation**      | Navigation Compose `2.8.0-alpha08`                           |
| **Local Storage**   | SharedPreferences (for search/watch history). Room `2.6.1` is a dependency but not yet used. |
| **Shapes**          | `androidx.graphics:graphics-shapes:1.1.0-alpha01`            |
| **Build System**    | Gradle with Kotlin DSL, Version Catalog (`libs.versions.toml`) |
| **Min SDK**         | 26                                                           |
| **Target/Compile SDK** | 36                                                        |

---

## 3. Project Structure

```
com.ivor.openanime/
|
|-- OpenAnimeApp.kt              # Application class (@HiltAndroidApp)
|-- MainActivity.kt              # Single Activity, sets up theme + navigation
|
|-- data/
|   |-- local/
|   |   |-- AppDatabase.kt       # Room Database definition
|   |   |-- dao/
|   |   |   |-- DownloadDao.kt   # DAO for downloaded items
|   |   |   |-- WatchLaterDao.kt # DAO for watch later lists
|   |   |-- entity/
|   |       |-- DownloadEntity.kt  # DB Entity for offline downloads
|   |       |-- WatchLaterEntity.kt # DB Entity for watch later
|   |-- remote/
|   |   |-- TmdbApi.kt           # Retrofit interface for TMDB endpoints
|   |   |-- SubtitleApi.kt       # Retrofit interface for subtitle endpoints
|   |   |-- model/
|   |       |-- AnimeDto.kt      # AnimeDto, AnimeDetailsDto, SeasonDto
|   |       |-- SeasonDetailsDto.kt  # SeasonDetailsDto, EpisodeDto
|   |       |-- SubtitleDto.kt   # Subtitle data models
|   |       |-- TmdbResponse.kt  # Generic paginated TMDB response wrapper
|   |-- repository/
|   |   |-- AnimeRepositoryImpl.kt  # Concrete repository (uses TmdbApi)
|   |   |-- DownloadRepositoryImpl.kt # Concrete repository for downloads
|   |   |-- WatchLaterRepositoryImpl.kt # Concrete repository for watch later
|   |-- service/
|       |-- HlsDownloadService.kt # Foreground service for HLS video downloading
|
|-- domain/
|   |-- repository/
|       |-- AnimeRepository.kt   # Repository interface (abstraction)
|       |-- DownloadRepository.kt # Repository interface
|       |-- WatchLaterRepository.kt # Repository interface
|
|-- di/
|   |-- AppModule.kt             # Provides SharedPreferences
|   |-- DatabaseModule.kt        # Provides Room AppDatabase and DAOs
|   |-- DownloadModule.kt        # Provides video downloading utilities
|   |-- NetworkModule.kt         # Provides Json, OkHttp, Retrofit, TmdbApi
|   |-- RepositoryModule.kt      # Binds repo interfaces to impls
|
|-- presentation/
|   |-- navigation/
|   |   |-- AppNavigation.kt     # NavHost + Screen sealed class (all routes)
|   |-- home/
|   |   |-- HomeScreen.kt        # Main screen: anime grid + top app bar
|   |   |-- HomeViewModel.kt     # Loads popular anime list
|   |-- search/
|   |   |-- SearchScreen.kt      # M3 Expressive SearchBar + results grid
|   |   |-- SearchViewModel.kt   # Search execution + history persistence
|   |-- watch_history/
|   |   |-- WatchHistoryScreen.kt    # History grid with LargeFlexibleTopAppBar
|   |   |-- WatchHistoryViewModel.kt # Loads/clears history from SharedPreferences
|   |-- watch_later/
|   |   |-- WatchLaterScreen.kt      # Watch Later list screen
|   |   |-- WatchLaterViewModel.kt   # Interaction with WatchLaterRepository
|   |-- downloads/
|   |   |-- DownloadsScreen.kt       # Displays offline downloaded episodes
|   |   |-- DownloadViewModel.kt     # Interactions with DownloadRepository
|   |-- details/
|   |   |-- DetailsScreen.kt     # Anime details: backdrop, seasons, episodes
|   |   |-- DetailsViewModel.kt  # Loads anime details + season episodes
|   |-- player/
|   |   |-- PlayerScreen.kt      # WebView (Vidking) + ExoPlayer for playback
|   |   |-- PlayerViewModel.kt   # Loads next episodes for the current season
|   |   |-- components/
|   |       |-- ExoPlayerView.kt # ExoPlayer Compose wrapper
|   |       |-- PlayerControls.kt # Custom playback controls overlay
|   |       |-- PlayerSettingsDialog.kt # Video quality etc settings dialog
|   |-- components/              
|       |-- AnimeCard.kt         # Reusable composable for anime grid items
|       |-- ExpressiveBackButton.kt # Expressive style back button
|
|-- ui/
    |-- theme/
        |-- Color.kt             # Light + Dark color tokens
        |-- Shape.kt             # ExpressiveShapes (larger corner radii)
        |-- Theme.kt             # MaterialExpressiveTheme setup
        |-- Type.kt              # Typography scale (bold headlines, readable bodies)
```

---

## 4. Navigation Routes

Defined in `AppNavigation.kt` via a `sealed class Screen`:

| Route                                | Screen              | Arguments                  |
|--------------------------------------|----------------------|----------------------------|
| `home`                               | HomeScreen           | None                       |
| `search`                             | SearchScreen         | None                       |
| `watch_later`                        | WatchLaterScreen     | None                       |
| `downloads`                          | DownloadsScreen      | None                       |
| `history`                            | WatchHistoryScreen   | None                       |
| `details/{mediaType}/{animeId}`      | DetailsScreen        | `mediaType: String, animeId: Int` |
| `player/{mediaType}/{animeId}/{season}/{episode}?downloadId={downloadId}` | PlayerScreen         | `mediaType: String, animeId: Int, season: Int, episode: Int, downloadId: String?` |

Navigation is handled via `NavHostController`. The `HomeScreen` top app bar provides entry points to Search and History.

---

## 5. Data Flow

### API Layer
- **Base URL:** `https://api.themoviedb.org/3/`
- **API Key:** Stored in `local.properties` as `TMDB_API_KEY`, injected via `BuildConfig.TMDB_API_KEY`
- **Auth:** Added as a query parameter (`api_key`) by an OkHttp interceptor in `NetworkModule`

Additionally, standard subtitle downloading APIs are present.

### Key Endpoints (TmdbApi.kt)
| Method                   | Endpoint                               | Purpose                          |
|--------------------------|----------------------------------------|----------------------------------|
| `getPopularAnime(page)`  | `discover/tv?with_genres=16&with_original_language=ja` | Discover popular anime |
| `searchAnime(query, page)` | `search/tv?query=...`               | Search anime by title            |
| `getAnimeDetails(id)`    | `tv/{id}`                              | Full details (seasons list)      |
| `getSeasonDetails(id, n)` | `tv/{id}/season/{n}`                  | Episodes for a specific season   |

### Data Models
- **`AnimeDto`**: id, name, overview, posterPath, backdropPath, firstAirDate, voteAverage, genreIds
- **`AnimeDetailsDto`**: extends AnimeDto + numberOfSeasons, numberOfEpisodes, seasons (List<SeasonDto>)
- **`SeasonDto`**: id, name, overview, posterPath, seasonNumber, episodeCount, airDate
- **`SeasonDetailsDto`**: _id, airDate, episodes (List<EpisodeDto>), name, overview, id, posterPath, seasonNumber
- **`EpisodeDto`**: airDate, episodeNumber, id, name, overview, runtime, seasonNumber, showId, stillPath, voteAverage, voteCount

All models use `@Serializable` (kotlinx.serialization) and `@SerialName` for JSON mapping.

### Image URLs
TMDB images are constructed as: `https://image.tmdb.org/t/p/w500{posterPath}`

---

## 6. Video Playback Architecture

The `PlayerScreen` uses a dual approach:

1. **WebView (Vidking):** An invisible WebView loads the Vidking embed URL (`https://www.vidking.net/embed/tv/{tmdbId}/{season}/{episode}`). A `WebViewClient` intercepts network requests looking for `.m3u8` HLS stream URLs.

2. **ExoPlayer:** Once an `.m3u8` URL is extracted from the WebView's network traffic, it is passed to ExoPlayer for native playback with custom controls (`PlayerControls.kt`).

This approach is **brittle** -- if Vidking changes their URL patterns or obfuscates streams, extraction will break. See `docs/VIDKING_API.md` for the full API reference.

The `PlayerViewModel` fetches episode lists from TMDB so the player can show "next episodes" below the video.

---

## 7. Local Storage

Currently uses both **SharedPreferences** and **Room** database.

**SharedPreferences** (provided by `AppModule`) for:
- **Search History** (`search_history_list`): JSON-encoded `List<String>` of recent queries. Managed by `SearchViewModel`.
- **Watch History** (`watch_history_list`): JSON-encoded `List<AnimeDto>` of watched titles. Managed by `WatchHistoryViewModel`.

**Room Database** (provided by `DatabaseModule`):
- **WatchLaterEntity**: Stores anime added to the watch later list.
- **DownloadEntity**: Stores downloaded episode information and local file paths.
Access is managed via `WatchLaterDao` and `DownloadDao` through their respective repositories.

## 8. Theming and Design System

### MaterialExpressiveTheme
The app uses `MaterialExpressiveTheme` (not plain `MaterialTheme`). This automatically applies expressive motion schemes (spring-based animations) to all M3 components.

- **Light fallback:** `expressiveLightColorScheme()` (the M3 Expressive default)
- **Dark fallback:** Custom `darkColorScheme(...)` defined in `Color.kt`
- **Dynamic color:** Enabled on Android 12+ via `dynamicDarkColorScheme`/`dynamicLightColorScheme`

### ExpressiveShapes (Shape.kt)
```kotlin
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)
```
These are larger than standard M3 defaults, creating a softer, more expressive look.

### Typography (Type.kt)
Uses `FontFamily.Default` with **heavier font weights** for display/headline/title tiers (Black, ExtraBold, Bold) to create visual hierarchy. Body text uses Normal weight for readability.

---

## 9. M3 Expressive Components in Use

The library version is `1.5.0-alpha13`, which includes the following expressive components that are used or available:

| Component                    | Used In               | Notes                                              |
|-------------------------------|------------------------|-----------------------------------------------------|
| `MaterialExpressiveTheme`    | `Theme.kt`            | App-wide theme with expressive motion               |
| `AppBarWithSearch`           | `SearchScreen`         | Top app bar that integrates with SearchBar           |
| `SearchBarState` / `rememberSearchBarState()` | `SearchScreen` | State-based SearchBar management         |
| `SearchBarDefaults.InputField` | `SearchScreen`       | Standard input field composable for SearchBar        |
| `ExpandedFullScreenSearchBar` | `SearchScreen`        | Full-screen expansion for search results/history     |
| `LargeFlexibleTopAppBar`    | `WatchHistoryScreen`   | Expressive top app bar with title + subtitle         |
| `LoadingIndicator`          | `WatchHistoryScreen`, `PlayerScreen` | Shape-morphing loading indicator     |
| `CenterAlignedTopAppBar`    | `HomeScreen`           | Standard centered top app bar                        |
| `LargeTopAppBar`            | Available              | Use for collapsing headers with scroll behavior      |

### Critical Rule: Component Priority
1. **Always check for an M3 Expressive version first** (e.g., `LoadingIndicator` over `CircularProgressIndicator`)
2. **Use standard M3 if no Expressive version exists**
3. **Never create custom components that duplicate M3 functionality**

---

## 10. Dependency Injection Graph

```
@HiltAndroidApp: OpenAnimeApp

Modules:
  NetworkModule (SingletonComponent):
    provideJson()         -> Json
    provideAuthInterceptor() -> Interceptor
    provideOkHttpClient() -> OkHttpClient
    provideRetrofit()     -> Retrofit
    provideTmdbApi()      -> TmdbApi
    provideSubtitleApi()  -> SubtitleApi

  AppModule (SingletonComponent):
    provideSharedPreferences() -> SharedPreferences

  DatabaseModule (SingletonComponent):
    provideAppDatabase()  -> AppDatabase
    provideWatchLaterDao() -> WatchLaterDao
    provideDownloadDao()   -> DownloadDao

  RepositoryModule (SingletonComponent):
    binds AnimeRepositoryImpl -> AnimeRepository
    binds WatchLaterRepositoryImpl -> WatchLaterRepository
    binds DownloadRepositoryImpl -> DownloadRepository

ViewModels (all @HiltViewModel):
  HomeViewModel(AnimeRepository)
  SearchViewModel(AnimeRepository, SharedPreferences, Json)
  WatchHistoryViewModel(SharedPreferences, Json)
  WatchLaterViewModel(WatchLaterRepository)
  DownloadViewModel(DownloadRepository)
  DetailsViewModel(AnimeRepository, SavedStateHandle)
  PlayerViewModel(TmdbApi)  // Note: uses TmdbApi directly, not Repository
```

---

## 11. Build and Run

### Prerequisites
- **TMDB API Key:** Add `TMDB_API_KEY=your_key_here` to `local.properties`
- **JDK 17**
- **Android Studio** with SDK 36

### Commands
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run on connected device
./gradlew installDebug
```

The APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

---

## 12. Key Documentation Files

| File                             | Purpose                                                    |
|----------------------------------|------------------------------------------------------------|
| `rules.md`                       | **Mandatory development rules.** Read this first.          |
| `material-3-expressive-guide.md` | Comprehensive M3 Expressive API reference (522KB). Use as lookup for component signatures, parameters, and examples. |
| `docs/VIDKING_API.md`            | Vidking player API reference: endpoints, parameters, postMessage progress tracking |
| `docs/IMPLEMENTATION_PLAN.md`    | Original implementation plan and architecture decisions     |
| `DesignMindset.md`               | Design philosophy and mindset guidelines                   |
| `Material3ExpressiveDesignGuide.md` | Condensed M3 Expressive design guide                    |
| `components.md`                  | Component reference/catalog                                |
| `shapes.md`                      | Shape system reference                                     |
| `MVP.md`                         | MVP scope definition                                       |

---

## 13. Development Rules (from rules.md)

These are **non-negotiable**:

1. **No emojis.** Speak like a senior engineer.
2. **M3 Expressive first.** Always check for Expressive components before using standard M3.
3. **No custom components that duplicate M3.** Do not reinvent loading indicators, search bars, etc.
4. **No hallucinations.** Verify component existence in the `material-3-expressive-guide.md` before using.
5. **Research dependencies first.** Verify versions and artifact IDs before adding.
6. **`libs.versions.toml` is the single source of truth** for all dependency versions.
7. **Clean Architecture:** Maintain domain/data/presentation layer separation.
8. **Ask, don't assume.** If something is unclear, ask the user.

---

## 14. Known Constraints and Technical Debt

| Area                    | Issue                                                        | Potential Fix                                      |
|-------------------------|--------------------------------------------------------------|----------------------------------------------------|
| **Video Extraction**    | WebView intercepts `.m3u8` URLs from Vidking -- fragile      | Direct API integration or hardened URL extraction   |
| **Local Storage**       | SharedPreferences for structured data (watch history as JSON) | Migrate to Room database                           |
| **Pagination**          | Home screen only loads page 1 of popular anime               | Implement Paging 3 library                         |
| **Watch History Saving** | No mechanism to *add* items to watch history from PlayerScreen | Wire PlayerScreen/DetailsScreen to save viewed items |
| **PlayerViewModel**     | Directly uses `TmdbApi` instead of `AnimeRepository`         | Refactor to go through repository                  |
| **Error Handling**      | Basic error states, no retry mechanisms                      | Add retry buttons, exponential backoff             |
| **Search Pagination**   | Only fetches first page of search results                    | Add infinite scroll with Paging 3                  |

---

## 15. Common Patterns

### ViewModel Pattern
All ViewModels follow this structure:
```kotlin
@HiltViewModel
class XViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<XUiState>(XUiState.Loading)
    val uiState: StateFlow<XUiState> = _uiState.asStateFlow()

    init { loadData() }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = XUiState.Loading
            repository.getData()
                .onSuccess { _uiState.value = XUiState.Success(it) }
                .onFailure { _uiState.value = XUiState.Error(it.message) }
        }
    }
}
```

### UiState Pattern
Two approaches are used:
- **Sealed interface** for screens with distinct states: `Loading | Success | Error` (Home, Details)
- **Data class** for screens with composite state: `SearchUiState(query, results, isLoading, error, history)` (Search, WatchHistory)

### Screen Composable Pattern
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XScreen(
    onBackClick: () -> Unit,
    onItemClick: (Int) -> Unit,
    viewModel: XViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ... }) { innerPadding -> ... }
}
```

### Image Loading
All images use Coil 3:
```kotlin
AsyncImage(
    model = "https://image.tmdb.org/t/p/w500${anime.posterPath}",
    contentDescription = anime.name,
    contentScale = ContentScale.Crop,
    modifier = Modifier.clip(ExpressiveShapes.medium)
)
```

### Anime Grid Layout
Consistently uses `LazyVerticalStaggeredGrid` with `StaggeredGridCells.Fixed(2)` and 16.dp spacing across Home, Search, and History screens. The shared `AnimeCard` composable is defined in `HomeScreen.kt`.

---

## 16. Skill Documents

The `.agent/skills/material-thinking/SKILL.md` file provides a comprehensive workflow for implementing M3 components:
1. Check the guide for component existence
2. Use the Expressive version if available
3. Follow exact API signatures from the guide
4. Verify the build compiles

---

## 17. Quick Reference: Adding a New Screen

1. **Create UI State:** Define a sealed interface or data class in a new ViewModel file
2. **Create ViewModel:** `@HiltViewModel` with `@Inject constructor`, expose `StateFlow<UiState>`
3. **Create Screen Composable:** Use `Scaffold`, collect state, handle Loading/Error/Success
4. **Add Route:** Add a `data object` (or `data class` for args) to `Screen` sealed class in `AppNavigation.kt`
5. **Add to NavHost:** Wire the `composable(Screen.X.route)` block with proper callbacks
6. **Wire Navigation:** Add triggers (buttons, clicks) in existing screens to navigate to the new route

---

*Last updated: 2026-02-11*

- Metadata Sniffer implemented by Antigravity (OpenClaw Bot) on 2026-02-18
