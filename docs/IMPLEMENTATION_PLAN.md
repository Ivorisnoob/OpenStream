# OpenStream Implementation Plan

> **Goal:** Build a beautiful, expressive Android anime streaming app using Material 3 Expressive design with Vidking as the streaming backend.

---

## Phase 1: Foundation (Week 1)

### 1.1 Project Setup
- [ ] Initialize Android project with Kotlin & Jetpack Compose
- [ ] Configure Material 3 with `useMaterial3 = true`
- [ ] Set up Gradle dependencies:
  - Jetpack Compose BOM
  - Material 3 (latest with Expressive support)
  - Coil (image loading)
  - Ktor/Retrofit (networking)
  - Room (local database)
  - Hilt (dependency injection)
  - Media3 ExoPlayer (optional, for future native playback)

### 1.2 Core Architecture
- [ ] Implement Clean Architecture layers:
  ```
  app/
  ├── data/           # Repositories, data sources, DTOs
  ├── domain/         # Use cases, entities, repository interfaces
  ├── presentation/   # ViewModels, UI state, Composables
  └── di/             # Hilt modules
  ```
- [ ] Set up Navigation Compose with Shared Element Transitions
- [ ] Create base theme with dynamic color extraction

### 1.3 API Integration
- [ ] TMDB API client for anime discovery/search
- [ ] Vidking URL builder utility
- [ ] Watch progress repository (Room + DataStore)

---

## Phase 2: Core Screens (Week 2-3)

### 2.1 Discovery Hub (Home Screen)
**Features:**
- [ ] Staggered Bento Grid layout
- [ ] Trending anime section (double-wide cards)
- [ ] Continue watching section (with progress bars)
- [ ] Genre-based rows

**Expressive Elements:**
- [ ] Spring-based scroll animations
- [ ] Card lift effect on long-press with haptic feedback
- [ ] Dynamic color extraction from visible posters
- [ ] Variable typography (weight shift based on scroll position)

**Data Flow:**
```
TMDB API → Repository → ViewModel → LazyVerticalStaggeredGrid
```

### 2.2 Search Screen
**Features:**
- [ ] Animated search bar (circle → full-width morphing)
- [ ] Real-time search with debounce
- [ ] Search history with recent/suggested queries
- [ ] Filter chips (genre, year, rating)

**Expressive Elements:**
- [ ] Staggered entrance animation (50ms delay per result)
- [ ] Emoji-enhanced placeholder text
- [ ] Keyboard-aware layout adjustments

### 2.3 Anime Details Screen
**Features:**
- [ ] Hero poster with parallax effect
- [ ] Synopsis with "Read More" expand animation
- [ ] Season/episode selector
- [ ] Related anime section

**Expressive Elements:**
- [ ] Shared element transition from Discovery Hub
- [ ] Liquid Glass top app bar
- [ ] Dynamic theme from poster colors
- [ ] Morphing "Play" button

---

## Phase 3: Cinematic Player (Week 4)

### 3.1 WebView Player Integration
**Features:**
- [ ] Full-screen WebView with Vidking embed
- [ ] Dynamic URL generation with:
  - TMDB ID
  - Season/Episode (for TV)
  - Extracted theme color
  - Resume progress (seconds)
- [ ] postMessage listener for progress updates
- [ ] Edge-to-edge immersive mode

**Implementation:**
```kotlin
@Composable
fun VidkingPlayer(
    tmdbId: Int,
    season: Int? = null,
    episode: Int? = null,
    themeColor: String,
    resumeFrom: Int = 0
) {
    val url = buildVidkingUrl(tmdbId, season, episode, themeColor, resumeFrom)
    
    AndroidView(factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            loadUrl(url)
            
            // Add progress listener bridge
            addJavascriptInterface(ProgressBridge(viewModel), "AndroidBridge")
        }
    })
}
```

### 3.2 Player UI Enhancements
**Features:**
- [ ] Atmospheric blur background (poster colors bleeding)
- [ ] Minimalist gesture controls
- [ ] Episode auto-advance (via postMessage)
- [ ] Picture-in-Picture support

**Expressive Elements:**
- [ ] Progressive blur effect around player
- [ ] Haptic feedback on gestures
- [ ] Smooth brightness/volume slider overlays

---

## Phase 4: Data & Persistence (Week 5)

### 4.1 Local Database (Room)
**Tables:**
- [ ] `WatchHistory` - Track watched content & progress
- [ ] `Favorites` - Saved anime list
- [ ] `SearchHistory` - Recent searches
- [ ] `CachedMetadata` - Offline-accessible anime info

**Schema:**
```kotlin
@Entity
data class WatchHistoryEntity(
    @PrimaryKey val id: String, // "{tmdbId}-{season}-{episode}"
    val tmdbId: Int,
    val type: ContentType,
    val season: Int?,
    val episode: Int?,
    val progressPercent: Int,
    val timestampSeconds: Int,
    val durationSeconds: Int,
    val lastWatched: Long,
    val posterUrl: String,
    val title: String
)
```

### 4.2 Preferences (DataStore)
- [ ] Theme preference (light/dark/system)
- [ ] Autoplay next episode toggle
- [ ] Default video quality
- [ ] Haptic feedback toggle

### 4.3 Offline Support
- [ ] Cache recent posters with Coil disk cache
- [ ] Store last-viewed metadata for offline browsing
- [ ] Graceful degradation when offline

---

## Phase 5: Polish & Expressive Details (Week 6)

### 5.1 Motion System
- [ ] Implement spring-based animations everywhere
- [ ] Shared element transitions between screens
- [ ] Staggered list animations
- [ ] Container morphing (button → player)

### 5.2 Typography System
- [ ] Integrate Roboto Flex variable font
- [ ] Implement scroll-aware weight changes
- [ ] Dynamic sizing based on content importance

### 5.3 Haptics
- [ ] Define haptic patterns:
  - Sharp click: Episode selection
  - Soft wave: Menu open
  - Pulse: Card lift on long-press
  - Tick: Seek gesture

### 5.4 Accessibility
- [ ] Content descriptions for all images
- [ ] Screen reader compatibility
- [ ] Respect `prefers-reduced-motion`
- [ ] High contrast mode support

---

## Phase 6: Testing & Release (Week 7)

### 6.1 Testing
- [ ] Unit tests for ViewModels & Use Cases
- [ ] Integration tests for API clients
- [ ] UI tests for critical flows
- [ ] Performance profiling (especially player screen)

### 6.2 Release Prep
- [ ] App signing configuration
- [ ] ProGuard/R8 optimization
- [ ] Play Store assets (screenshots, description)
- [ ] Privacy policy for streaming content

---

## Tech Stack Summary

| Layer | Technology |
|-------|------------|
| **UI** | Jetpack Compose + Material 3 Expressive |
| **Navigation** | Navigation Compose with Shared Element |
| **Networking** | Ktor or Retrofit |
| **Image Loading** | Coil |
| **Local Storage** | Room + DataStore |
| **DI** | Hilt |
| **Streaming** | Vidking (WebView) |
| **Metadata** | TMDB API |
| **Haptics** | HapticFeedback API |
| **Motion** | Compose Animation (spring-based) |

---

## API Dependencies

| API | Purpose | Rate Limits |
|-----|---------|-------------|
| **TMDB** | Anime metadata, search, posters | Free tier: ~50 req/sec |
| **Vidking** | Video streaming | Check TOS |

---

## File Structure (Proposed)

```
app/src/main/java/com/openstream/
├── MainActivity.kt
├── OpenStreamApp.kt
├── data/
│   ├── local/
│   │   ├── dao/
│   │   ├── entity/
│   │   └── database/
│   ├── remote/
│   │   ├── tmdb/
│   │   └── vidking/
│   └── repository/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── presentation/
│   ├── navigation/
│   ├── theme/
│   ├── components/
│   └── screens/
│       ├── home/
│       ├── search/
│       ├── details/
│       └── player/
└── di/
    └── AppModule.kt
```

---

## Next Steps

1. **Immediate:** Complete Android Studio project setup
2. **Today:** Set up Gradle dependencies and base theme
3. **This Week:** Implement Discovery Hub with mock data
4. **End of Week:** Integrate TMDB API for real content

---

## Success Metrics

| Metric | Target |
|--------|--------|
| App size | < 15 MB |
| Cold start | < 2 seconds |
| Frame drops | < 1% |
| Search latency | < 500ms |
| Player load | < 3 seconds |
