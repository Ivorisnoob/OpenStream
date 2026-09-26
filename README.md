<div align="center">

<img src="docs/assets/icon.png" width="128" alt="OpenStream app icon">

# OpenStream

**Movies, series and anime in one native Android app.**

Browse what's trending, pick up where you left off, and watch with a real player:
quality switching, subtitles, dubs, downloads and picture-in-picture.

[![Latest release](https://img.shields.io/github/v/release/Ivorisnoob/OpenStream?color=FF5F8A&label=release)](https://github.com/Ivorisnoob/OpenStream/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-1A1030)](LICENSE)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)

[**Download the latest APK**](https://github.com/Ivorisnoob/OpenStream/releases/latest)

</div>

## Screenshots

<table>
  <tr>
    <td align="center"><img src="screenshots/home.jpg" width="240" alt="Home"><br><sub>Home</sub></td>
    <td align="center"><img src="screenshots/home-shelves.jpg" width="240" alt="Curated shelves"><br><sub>Curated shelves</sub></td>
    <td align="center"><img src="screenshots/details.jpg" width="240" alt="Title page"><br><sub>Title page</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/details-more.jpg" width="240" alt="Cast, trailers and more like this"><br><sub>Cast, trailers and more like this</sub></td>
    <td align="center"><img src="screenshots/player.jpg" width="240" alt="Player"><br><sub>Player</sub></td>
    <td align="center"><img src="screenshots/player-settings.jpg" width="240" alt="Playback settings"><br><sub>Playback settings</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/search.jpg" width="240" alt="Search"><br><sub>Search</sub></td>
    <td align="center"><img src="screenshots/search-results.jpg" width="240" alt="Search results"><br><sub>Search results</sub></td>
    <td align="center"><img src="screenshots/history.jpg" width="240" alt="History"><br><sub>History</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/saved.jpg" width="240" alt="Saved"><br><sub>Saved</sub></td>
    <td align="center"><img src="screenshots/downloads.jpg" width="240" alt="Downloads"><br><sub>Downloads</sub></td>
    <td></td>
  </tr>
</table>

## Features

**Discover**
- A Home screen with a trending carousel, a top 10, new episodes this week, and popular movies,
  series and anime
- Search that runs as you type, with filters, genres, trending picks and recent searches
- Title pages with seasons and episodes, cast, trailers and similar titles

**Watch**
- Continue watching: resume from the exact spot, and the next episode is lined up for you
- Auto-play the next episode, and watched marks on episodes you've finished
- Switch quality in the player (up to 4K where the source has it)
- Subtitles in many languages, from the stream or from OpenSubtitles
- Pick the audio: original, dub, or another language when a source carries it
- A mini player keeps the video going while you browse, and picture-in-picture works outside the app

**Keep**
- Download a single episode or a whole season, with pause, resume and retry, and play it offline
- Saved and History, each with its own search, filters and sorting
- In-app updates: check GitHub for a new release, then download and install it without leaving the app

**Feel**
- Material 3 Expressive design with dynamic color on Android 12 and up
- A themed app icon that follows your wallpaper colors
- Layouts built for phones, tablets and landscape

## How streams work

OpenStream doesn't host any video. Titles and artwork come from [TMDB](https://www.themoviedb.org/).
Streams come from **source extensions** listed in a JSON catalog. The app checks every installed
source in parallel, ranks the results, and plays the best one. If a source fails, it moves on to
the next.

Not every title is available yet, and coverage grows as sources are added. Want to add one? See
the [extension contributor guide](extensions/README.md) and the
[catalog format](docs/EXTENSIONS.md).

## Build from source

You need Android Studio (or JDK 17 and the Android SDK) and a free
[TMDB API key](https://www.themoviedb.org/settings/api).

```bash
git clone https://github.com/Ivorisnoob/OpenStream.git
cd OpenStream
echo "TMDB_API_KEY=your_key_here" >> local.properties
./gradlew installDebug
```

Without a key the app falls back to TMDB's `DEMO_KEY`, which is heavily rate-limited.

## Tech stack

| Area | What's used |
| --- | --- |
| UI | Jetpack Compose, Material 3 Expressive (`1.5.0-alpha29`) |
| Language and build | Kotlin 2.3, AGP 9 with built-in Kotlin, Gradle version catalog |
| Architecture | Single activity, Navigation Compose, MVVM with `StateFlow`, Hilt |
| Data | Retrofit, OkHttp, kotlinx-serialization, Room, Coil 3 |
| Playback | Media3 ExoPlayer (HLS and MP4), a Media3 download service, and a hidden WebView fallback for web sources |

Architecture notes and conventions are in [CLAUDE.md](CLAUDE.md).

## Contributing

Bug reports, fixes and new sources are all welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE)

## Acknowledgments

- [TMDB](https://www.themoviedb.org/) for metadata and artwork. This product uses the TMDB API but
  is not endorsed or certified by TMDB.
- [OpenSubtitles](https://www.opensubtitles.org/) for subtitles
- [Material 3 Expressive](https://m3.material.io/) for the design system
