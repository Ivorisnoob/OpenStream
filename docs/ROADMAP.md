# Roadmap

Features OpenStream is missing compared with other streaming apps, grouped by area. Each area is
ordered roughly by priority. Tick items off as they land; if the code already does something listed
here, trust the code and fix this file.

Last reviewed: 2026-09-27.

## Already in place

For reference, so these don't get re-added: double-tap to seek 10s, swipe for brightness (left) and
volume (right), playback speed, picture-in-picture, auto-play next episode with countdown
(`UpNextOverlay`), recent searches and genre browsing in Search, Continue Watching, watch later,
history, downloads with pause/resume/retry, share a title, OpenSubtitles plus embedded subtitles, caption styling,
source failover, in-app updates with release notes, dynamic color.

## Priority

- [x] **Private DNS (DNS-over-HTTPS).** Several Indian ISPs poison DNS for TMDB, so titles and
      artwork fail to load without AdGuard. Settings → Network → DNS picks System, AdGuard
      (default), Cloudflare or Google; every OkHttp client and Coil go through `AppDns`, which falls
      back to the network's DNS if the resolver is unreachable.
- [ ] **DNS for playback and downloads.** Media3 still uses `DefaultHttpDataSource` (system DNS), and
      WebView sources can't take a custom resolver. Switch Media3 to `OkHttpDataSource` if stream
      CDNs turn out to be DNS-blocked too. High-risk playback change.

## Player

- [ ] **MediaSession and media notification.** `media3-session` is not a dependency, so background
      and MiniPlayer playback has no notification, lock-screen controls, headset/Bluetooth buttons
      or Android Auto/Wear surface. Highest-impact player gap.
- [x] **Pinch to zoom.** Pinch out to fill (crop the black bars), pinch in to fit.
- [x] **Resize / aspect button.** Fit, Zoom to fill, Stretch (fullscreen).
- [x] **Skip intro / recap / credits.** AniSkip for anime (MAL id looked up on AniList); a manual
      "Skip 85s" early in anything else.
- [x] **Subtitle sync offset.** Shift cues earlier or later in the player (sideloaded subtitles only;
      embedded tracks are rendered by Media3).
- [x] **Screen lock.** Ignore touches during playback until unlocked.
- [x] **Horizontal swipe to scrub** with a time preview.
- [x] **Fullscreen swipes.** Inline: swipe up for fullscreen, down to shrink into the mini player.
      Fullscreen: swipe down the middle to exit; brightness and volume moved to the outer thirds.
- [x] **Double-tap the middle** to play/pause (the sides still seek).
- [x] **Mini player swipes**: up to open the player, sideways to dismiss.
- [x] **Navbar scrub**: drag across the floating toolbar to switch tabs.
- [x] **Hold for 2x speed** while long-pressing.
- [x] **Stacking double-tap seek** (10s, 20s, 30s...).
- [x] **Configurable seek step** (5, 10, 15 or 30s in Settings).
- [x] **Sleep timer.** End of episode or after N minutes.
- [x] **Orientation lock** in fullscreen.
- [x] **Remembered audio and subtitle language** applied to every title (original vs. dub, subtitle
      language), instead of picking per episode.
- [ ] **Chromecast.** Needs `media3-cast`; source URLs with Referer/Origin headers may not play on a
      Cast receiver.
- [ ] **Seekbar thumbnail previews.** Only when the stream ships trick-play images; low priority.

Fixed: the brightness gesture now starts from the current brightness, and leaving the player hands
brightness back to the system.

## Discovery

- [x] **Person pages.** Cast on Details opens a page with photo, bio and filmography.
- [x] **Filters and sorting** in search beyond genre: type, year, rating, original language.
- [ ] **In-app trailers.** Trailers currently open YouTube externally.
- [ ] **Upcoming episodes calendar** for followed shows.
- [x] **"Not interested" / hide title** from Home rows (long-press; undo in Settings).

## Library and sync

- [x] **Backup and restore.** Watch Later, history/progress, hidden titles and settings as a JSON
      file; restoring merges.
- [ ] **Trakt / AniList / MAL sync** for history, progress and ratings (optional sign-in, OAuth; no
      user-supplied keys).
- [x] **Mark watched / unwatched** by season (and per episode from its long-press menu).
- [ ] **Mark a whole title watched / unwatched.**
- [ ] **Custom lists** beyond Watch Later.
- [ ] **New episode notifications** for followed shows (needs WorkManager, not in the app yet).
- [ ] **Profiles** with separate history and progress.

## Downloads

- [x] **Wi-Fi only** download setting (unmetered network requirement on the `DownloadManager`).
- [x] **Download a whole season** in one action ("Download season" on Details).
- [x] **Default download quality** setting (480p, 720p or 1080p).
- [x] **Storage view**: space used, free space, delete all.
- [ ] **Download subtitles** alongside the video for offline playback.

## Settings

- [x] Theme: light / dark / system, and a dynamic color toggle.
- [x] Playback defaults: speed, auto-play next, seek step.
- [ ] Playback defaults: streaming quality, skip-intro behavior.
- [x] Subtitle defaults: the last language picked (or off) is applied to every title.
- [x] Clear image cache. (There is no separate stream cache; the Media3 cache holds downloads.)
- [x] Clear history / reset progress.

## Platform and app

- [x] **Offline state.** Offline banner with a shortcut to Downloads.
- [x] **Deep links.** TMDB links and text shared to the app open Details (Android 12+ needs the user
      to allow the links under "Open by default").
- [ ] **Localization.** Almost all UI text is hard-coded in Compose; move it to `strings.xml`.
- [ ] **Tablet and foldable layouts** (list-detail, nav rail on wide screens).
- [ ] **Android TV** (leanback launcher entry, D-pad focus).
- [x] **Predictive back** (`android:enableOnBackInvokedCallback`).
- [x] **App shortcuts** (Continue Watching, Search, Downloads).
- [ ] **Continue Watching widget.**
- [ ] **Shared element transitions** from cards to Details artwork.
- [x] **Crash and log export** (local file) for bug reports.
- [ ] **Media3 upgrade.** The project is on 1.3.1; newer releases bring session, HLS and
      decoder fixes. Treat as a high-risk playback change.

## Release

- [x] Signed release APK built on GitHub Actions for the owner's pushes to `main` (artifact only,
      no GitHub release). Needs the `OPENSTREAM_KEYSTORE_*` / `OPENSTREAM_KEY_*` repo secrets.
