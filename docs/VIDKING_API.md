# Vidking Player API Documentation

> **Purpose:** This document provides the complete API reference for integrating the Vidking embeddable video player into OpenStream.

---

## Overview

Vidking is an embeddable video streaming player that uses **TMDB IDs** to serve content. It's optimized for performance using **HLS.js** for modern streaming and provides customization options via URL parameters.

---

## Base URL

```
https://www.vidking.net
```

---

## Embed Endpoints

### 1. Movies

**Endpoint:**
```
/embed/movie/{tmdbId}
```

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `tmdbId` | Integer | The TMDB (The Movie Database) ID for the movie |

**Example:**
```
https://www.vidking.net/embed/movie/1078605
```

---

### 2. TV Series / Anime

**Endpoint:**
```
/embed/tv/{tmdbId}/{season}/{episode}
```

**Parameters:**
| Parameter | Type | Description |
|-----------|------|-------------|
| `tmdbId` | Integer | The TMDB ID for the TV series/anime |
| `season` | Integer | Season number (1-indexed) |
| `episode` | Integer | Episode number (1-indexed) |

**Example:**
```
https://www.vidking.net/embed/tv/119051/1/8
```

---

## Query Parameters (Customization)

Append these as URL query parameters to customize player behavior:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `color` | String (Hex) | - | Primary player color (without `#`). Example: `e50914` for Netflix red |
| `autoPlay` | Boolean | `false` | Auto-start video on load |
| `nextEpisode` | Boolean | `false` | Show "Next Episode" button (TV only) |
| `episodeSelector` | Boolean | `false` | Show episode selection menu (TV only) |
| `progress` | Number | `0` | Start playback at specific time (in seconds) |

---

## Full URL Examples

### Movie with Custom Color + Autoplay
```
https://www.vidking.net/embed/movie/1078605?color=e50914&autoPlay=true
```

### Movie Starting at Specific Time (Resume Playback)
```
https://www.vidking.net/embed/movie/1078605?color=e50914&progress=120&autoPlay=true
```

### TV Series with All Features Enabled
```
https://www.vidking.net/embed/tv/119051/1/8?color=e50914&autoPlay=true&nextEpisode=true&episodeSelector=true
```

---

## HTML Iframe Integration

### Basic Movie Embed
```html
<iframe 
  src="https://www.vidking.net/embed/movie/1078605" 
  width="100%" 
  height="600" 
  frameborder="0" 
  allowfullscreen>
</iframe>
```

### Fully Customized TV Embed
```html
<iframe 
  src="https://www.vidking.net/embed/tv/119051/1/8?color=e50914&autoPlay=true&nextEpisode=true&episodeSelector=true" 
  width="100%" 
  height="600" 
  frameborder="0" 
  allowfullscreen>
</iframe>
```

---

## Progress Updates via postMessage

The Vidking player sends real-time progress updates via the browser's `postMessage` API. This is essential for OpenStream's **watch progress tracking** and **resume playback** features.

### Progress Data Structure

```typescript
interface VidkingProgressEvent {
  id: number;           // TMDB Content ID
  type: 'movie' | 'tv'; // Content type
  progress: number;     // Watch progress percentage (0-100)
  timestamp: number;    // Current playback position in seconds
  duration: number;     // Total duration in seconds
  season?: number;      // Season number (TV only)
  episode?: number;     // Episode number (TV only)
}
```

### JavaScript Listener Example

```javascript
window.addEventListener('message', (event) => {
  // Verify origin for security
  if (event.origin !== 'https://www.vidking.net') return;
  
  const data = event.data;
  
  console.log('Content ID:', data.id);
  console.log('Type:', data.type);
  console.log('Progress:', data.progress + '%');
  console.log('Current Time:', data.timestamp + 's');
  console.log('Duration:', data.duration + 's');
  
  if (data.type === 'tv') {
    console.log('Season:', data.season);
    console.log('Episode:', data.episode);
  }
  
  // Save progress to local storage or backend
  saveWatchProgress(data);
});
```

### Kotlin/Android WebView Equivalent

```kotlin
webView.addJavascriptInterface(object {
    @JavascriptInterface
    fun onProgressUpdate(json: String) {
        val progress = Json.decodeFromString<VidkingProgress>(json)
        // Handle progress update
        viewModel.updateWatchProgress(progress)
    }
}, "AndroidBridge")

// Inject JavaScript to forward postMessage to Android
webView.evaluateJavascript("""
    window.addEventListener('message', (event) => {
        if (event.origin === 'https://www.vidking.net') {
            AndroidBridge.onProgressUpdate(JSON.stringify(event.data));
        }
    });
""", null)
```

---

## TMDB Integration

Since Vidking uses **TMDB IDs**, you'll need to integrate with the TMDB API to:

1. **Search for anime** by title
2. **Get TMDB ID** for the content
3. **Fetch metadata** (posters, descriptions, seasons/episodes)
4. **Generate Vidking URL** using the TMDB ID

### TMDB API Reference
- **Website:** https://www.themoviedb.org/
- **API Docs:** https://developers.themoviedb.org/3
- **Anime Discovery:** Use the "TV" endpoint with `with_genres=16` (Animation)

---

## OpenStream Usage Notes

### For Dynamic Theming
Extract the dominant color from the anime poster and pass it to Vidking:

```kotlin
val dominantColor = extractDominantColor(posterBitmap) // Returns "e50914"
val playerUrl = "https://www.vidking.net/embed/tv/$tmdbId/$season/$episode?color=$dominantColor&autoPlay=true"
```

### For Resume Playback
Use the `progress` parameter with saved timestamp:

```kotlin
val savedTimestamp = watchHistoryDao.getProgress(tmdbId, season, episode)
val playerUrl = "https://www.vidking.net/embed/tv/$tmdbId/$season/$episode?progress=$savedTimestamp"
```

### For Next Episode Auto-Play
Enable `nextEpisode=true` and listen for episode completion via postMessage.

---

## Summary Table

| Feature | Implementation |
|---------|----------------|
| **Stream Movies** | `/embed/movie/{tmdbId}` |
| **Stream TV/Anime** | `/embed/tv/{tmdbId}/{season}/{episode}` |
| **Custom Colors** | `?color=HEX` |
| **Auto-play** | `?autoPlay=true` |
| **Resume Playback** | `?progress=SECONDS` |
| **Episode Navigation** | `?nextEpisode=true&episodeSelector=true` |
| **Progress Tracking** | `postMessage` listener |
