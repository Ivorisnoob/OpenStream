# OpenAnime

A modern Android application for browsing and streaming anime, built with Jetpack Compose and Material Design 3 Expressive.

## Overview

OpenAnime provides a native Android experience for discovering and watching anime content. The app fetches metadata from The Movie Database (TMDB) and streams video through the Vidking player, all wrapped in a beautiful Material Design 3 Expressive interface.

## Features

- Browse popular anime titles with rich metadata
- Search anime by title with persistent search history
- View detailed information including seasons and episodes
- Stream episodes with custom playback controls
- Track watch history
- Dynamic color theming (Android 12+)
- Material Design 3 Expressive UI with fluid animations

## Tech Stack

### Core Technologies
- **Language:** Kotlin 2.0.21
- **UI Framework:** Jetpack Compose with Material 3 Expressive (1.5.0-alpha13)
- **Architecture:** Clean Architecture (Domain/Data/Presentation layers)
- **Dependency Injection:** Hilt 2.51.1 with KSP

### Networking & Data
- **HTTP Client:** Retrofit 2.11.0 + OkHttp 4.12.0
- **Serialization:** Kotlinx Serialization 1.6.3
- **Image Loading:** Coil 3 (3.0.0-rc01)
- **Local Storage:** SharedPreferences (Room 2.6.1 available for future use)

### Media Playback
- **Video Player:** Media3 ExoPlayer 1.3.1
- **Streaming:** HLS (.m3u8) extraction via WebView

### Navigation
- **Navigation Compose:** 2.8.0-alpha08

## Requirements

- **Minimum SDK:** 26 (Android 8.0)
- **Target SDK:** 36
- **Compile SDK:** 36
- **JDK:** 17
- **TMDB API Key:** Required (see Setup section)

## Setup

### 1. Clone the Repository
```bash
git clone https://github.com/ivorisnoob/OpenAnime.git
cd OpenAnime
```

### 2. Configure TMDB API Key
Create a `local.properties` file in the project root and add your TMDB API key:
```properties
TMDB_API_KEY=your_api_key_here
```

You can obtain a free API key from [The Movie Database](https://www.themoviedb.org/settings/api).

### 3. Build and Run
```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Release build
./gradlew assembleRelease
```

## Project Structure

```
com.ivor.openanime/
├── data/
│   ├── remote/          # API interfaces and DTOs
│   └── repository/      # Repository implementations
├── domain/
│   └── repository/      # Repository interfaces
├── di/                  # Dependency injection modules
├── presentation/
│   ├── home/           # Main browsing screen
│   ├── search/         # Search functionality
│   ├── details/        # Anime details view
│   ├── player/         # Video playback
│   ├── watch_history/  # Watch history tracking
│   ├── navigation/     # App navigation graph
│   └── components/     # Reusable UI components
└── ui/
    └── theme/          # Material Design 3 theming
```

## Architecture

OpenAnime follows Clean Architecture principles with clear separation of concerns:

### Data Layer
- **TmdbApi:** Retrofit interface for TMDB endpoints
- **AnimeRepositoryImpl:** Concrete implementation handling API calls
- **DTOs:** Data transfer objects for network responses

### Domain Layer
- **AnimeRepository:** Repository interface defining data operations
- Pure Kotlin with no Android dependencies

### Presentation Layer
- **ViewModels:** State management with Kotlin Flows
- **Screens:** Jetpack Compose UI components
- **Navigation:** Type-safe navigation with sealed classes

## Key Components

### Material Design 3 Expressive
The app leverages Material 3 Expressive components for enhanced visual appeal:
- `MaterialExpressiveTheme` with spring-based animations
- `ExpandedFullScreenSearchBar` for immersive search
- `LargeFlexibleTopAppBar` for dynamic headers
- `LoadingIndicator` with shape-morphing animations
- Custom `ExpressiveShapes` with larger corner radii

### Video Playback
Dual-approach architecture:
1. **WebView:** Intercepts Vidking embed URLs to extract HLS streams
2. **ExoPlayer:** Native playback with custom controls

### State Management
Consistent ViewModel pattern using Kotlin StateFlow:
```kotlin
sealed interface UiState {
    data object Loading : UiState
    data class Success(val data: List<Anime>) : UiState
    data class Error(val message: String?) : UiState
}
```

## API Integration

### TMDB Endpoints
- **Discover:** Popular anime with genre filtering
- **Search:** Query anime by title
- **Details:** Full metadata including seasons
- **Season Details:** Episode lists for specific seasons

### Image URLs
Images are constructed as:
```
https://image.tmdb.org/t/p/w500{posterPath}
```

## Development Guidelines

### Code Style
- Follow Kotlin coding conventions
- Use Jetpack Compose best practices
- Maintain Clean Architecture boundaries
- Prefer composition over inheritance

### Component Priority
1. Check for M3 Expressive components first
2. Use standard M3 if no Expressive version exists
3. Never create custom components that duplicate M3 functionality

### Dependency Management
All versions are managed in `libs.versions.toml` using Gradle Version Catalog.

## Known Limitations

- Video extraction relies on Vidking URL patterns (subject to change)
- Pagination limited to first page on some screens
- Watch history uses SharedPreferences (Room migration planned)
- No offline support currently

## Future Enhancements

- Migrate to Room database for robust local storage
- Implement Paging 3 for infinite scroll
- Add retry mechanisms for network errors
- Extract reusable UI components
- Implement proper watch history tracking from player
- Add subtitle support
- Offline download capability

## Documentation

Additional documentation is available in the repository:
- `AGENT.md` - Comprehensive codebase context for AI agents
- `docs/VIDKING_API.md` - Vidking player API reference
- `docs/IMPLEMENTATION_PLAN.md` - Architecture decisions
- `.agent/rules/code-style-guide.md` - Development rules

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our development process, coding standards, and how to submit pull requests.

Key guidelines:
- Follow Clean Architecture principles
- Use Material 3 Expressive components appropriately
- Ensure all builds pass without errors
- Update documentation for significant changes

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [The Movie Database (TMDB)](https://www.themoviedb.org/) for anime metadata
- [Vidking](https://www.vidking.net/) for video streaming
- Material Design 3 Expressive for the design system

---

Built with Kotlin and Jetpack Compose
