# Roadmap

Features OpenStream is missing compared with other streaming apps, grouped by area. Each area is
ordered roughly by priority. Tick items off as they land; if the code already does something listed
here, trust the code and fix this file.

Last reviewed: 2026-10-03 (after `v2.1` plus PR #15: Stremio add-ons, offline subtitles for
downloads, faster source search).

## Already in place

For reference, so these don't get re-added: double-tap to seek 10s, swipe for brightness (left) and
volume (right), playback speed, picture-in-picture, auto-play next episode with countdown
(`UpNextOverlay`), recent searches and genre browsing in Search, Continue Watching, watch later,
history, downloads with pause/resume/retry, share a title, OpenSubtitles plus SubSource plus
embedded subtitles, caption styling, source failover, in-app updates with release notes, dynamic
color, Chromecast, PiP, profiles with kids filters, per-source ranking, DNS-over-HTTPS.

## Priority

- [x] **Private DNS (DNS-over-HTTPS).** Several Indian ISPs poison DNS for TMDB, so titles and
      artwork fail to load without AdGuard. Settings → Network → DNS picks System, AdGuard
      (default), Cloudflare or Google; every OkHttp client and Coil go through `AppDns`, which falls
      back to the network's DNS if the resolver is unreachable.

## Player

- [ ] **MediaSession and media notification.** `media3-session` is not a dependency, so background
      and MiniPlayer playback has no notification, lock-screen controls, headset/Bluetooth buttons
      or Android Auto/Wear surface. Highest-impact player gap.
- [x] **Pinch to zoom.** Pinch out to fill (crop the black bars), pinch in to fit.
- [x] **Resize / aspect button.** Fit, Zoom to fill, Stretch (fullscreen).
- [x] **Skip intro / recap / credits.** AniSkip for anime; the MAL id and episode come from the same
      TMDB -> AniList mapping the anime sources use (title search only as a fallback), and the
      submission timed on the closest file length wins. A manual "Skip 85s" early in anything else.
- [x] **Subtitle sync offset.** Shift cues earlier or later in the player (sideloaded subtitles only;
      embedded tracks are rendered by Media3).
- [x] **Screen lock.** Ignore touches during playback until unlocked.
- [x] **Horizontal swipe to scrub** with a time preview.
- [x] **Fullscreen swipes.** Inline: swipe up for fullscreen, down to shrink into the mini player.
      Fullscreen: swipe down the middle to exit; brightness and volume moved to the outer thirds.
- [x] **Double-tap the middle** to play/pause (the sides still seek).
- [x] **Mini player swipes**: up to open the player, sideways to dismiss.
- [x] **Navbar scrub**: drag across the floating toolbar to switch tabs.
- [x] **Swipe up in fullscreen** opens this season's episodes in the player panel.
- [x] **Details gestures**: swipe sideways on episodes to change season; pull down past the top
      to close.
- [x] **Hold for 2x speed** while long-pressing.
- [x] **Stacking double-tap seek** (10s, 20s, 30s...).
- [x] **Configurable seek step** (5, 10, 15 or 30s in Settings).
- [x] **Sleep timer.** End of episode or after N minutes.
- [x] **Orientation lock** in fullscreen.
- [x] **Remembered audio and subtitle language** applied to every title (original vs. dub, subtitle
      language), instead of picking per episode.
- [x] **Chromecast.** Cast button in the player (Default Media Receiver). The phone runs a small LAN
      proxy (`CastMediaProxy`) so the TV gets the stream's Referer/Origin headers, CORS, rewritten HLS
      playlists, PNG-prefix stripping and WebVTT subtitles; downloads cast from the cache. Playback
      moves to the TV at the current position and back to the phone (paused) on disconnect; progress,
      mini player, up next, sleep timer, TV subtitles and volume keys work while casting.
- [x] **Picture-in-picture button** in the player's top bar, besides auto-entering on Home. The
      PiP window has play/pause plus two buttons picked in Settings (back, forward, next, skip intro).
- [ ] **Seekbar thumbnail previews.** Only when the stream ships trick-play images; low priority.

Fixed: the brightness gesture now starts from the current brightness, and leaving the player hands
brightness back to the system.

## Discovery

- [x] **Person pages.** Cast on Details opens a page with photo, bio and filmography.
- [x] **Filters and sorting** in search beyond genre: type, year, rating, original language.
- [ ] **In-app trailers.** Trailers currently open YouTube externally
      (`DetailsScreen.kt:913` and the `TrailerRail`).
- [ ] **Upcoming episodes calendar** for followed shows.
- [x] **"Not interested" / hide title** from Home rows (long-press; undo in Settings).

## Library and sync

- [x] **Backup and restore.** Watch Later, history/progress, hidden titles and settings as a JSON
      file; restoring merges.
- [ ] **Trakt / AniList / MAL sync** for history, progress and ratings (optional sign-in, OAuth; no
      user-supplied keys). Blocked on a decision: Trakt requires a registered client id/secret,
      which sits against the "no user-supplied API keys" rule in `CLAUDE.md`. AniList is keyless
      and would be the cheapest first target. Needs the maintainer's call.
- [x] **Mark watched / unwatched** by season (and per episode from its long-press menu).
- [x] **Mark a whole title watched / unwatched** (every aired episode, specials aside; unwatched asks first).
- [x] **Custom lists** beyond Watch Later: "Add to list" on Details, lists on the Saved tab, rename,
      delete, remove with undo; included in backups.
- [ ] **New episode notifications** for followed shows (needs WorkManager; `androidx.work` is not a
      dependency yet, and there is no "follow" concept in the app either).
- [x] **Profiles** with separate Watch Later, lists, history and progress; "Who's watching?" on
      launch, kids profiles (rated G/PG/TV-Y..TV-PG only, hold the avatar to leave). No PIN yet.

## Downloads

- [x] **Wi-Fi only** download setting (unmetered network requirement on the `DownloadManager`).
- [x] **Download a whole season** in one action ("Download season" on Details).
- [x] **Default download quality** setting (480p, 720p or 1080p).
- [x] **Storage view**: space used, free space, delete all.
- [x] **Download subtitles** alongside the video for offline playback. Every download can get a
      subtitle sheet (the stream's own, OpenSubtitles, SubSource; filterable by language, SDH and
      "More releases"), and the languages picked in Settings are saved automatically with each new
      download. Files live in `filesDir/subtitles/<download id>/` and the player lists them first,
      online or offline.

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
- [ ] **Localization.** `strings.xml` still holds only 2 entries (the app name and one other);
      essentially all UI text is hard-coded in Compose. Large sweep across every screen.
- [x] **Tablet and foldable layouts.** `Adaptive.kt` provides the width class (`byWidth`,
      `isCompactWidth`, `isExpandedWidth`, `CenteredListBox`); `AppNavigation` swaps the floating
      toolbar for a `WideNavigationRail` on non-compact widths, and every list screen sizes itself
      from the width class.
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
