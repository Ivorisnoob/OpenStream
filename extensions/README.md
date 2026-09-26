# Contributing sources

OpenStream finds streams through **extensions**: entries in a JSON catalog that tell the app which
built-in engine to use and where to point it. Extensions are data, not code, so adding a source never
runs anything new on a user's phone, and most additions need no Kotlin at all.

This folder holds the **official catalog**, [`index.json`](index.json). The app fetches it from
GitHub and also ships a copy inside the APK. For the format reference and how the marketplace ranks
entries, see [`docs/EXTENSIONS.md`](../docs/EXTENSIONS.md).

## Two ways to contribute

| You want to… | Do this |
| --- | --- |
| Add or fix a source for everyone | Open a pull request against `extensions/index.json` (steps below) |
| Share sources without waiting for review | Host your own repository and have users add it in **Settings → Extension marketplace → Repositories → Add repository** |

A personal repository uses the same format. Any public JSON URL works, including GitHub raw links;
`github.com/…/blob/…` links are converted automatically.

## Adding a source to the official catalog

### 1. Pick an engine

The engine decides how the app talks to the source. Only these three exist; anything else is shown
as "Needs a newer app version".

| `engine.type` | Use it for | Fields |
| --- | --- | --- |
| `vidking-direct` | A route of the Vidking API (`api.speedracelight.com/<route>/sources-with-title`) | `endpoint` (e.g. `cdn/sources-with-title`), optional `language`, `qualityFilter` |
| `web-embed` | Any web player page the app can load in a hidden browser and record the video requests of | `movieUrl` and/or `tvUrl` |
| `vidking-webview` | The built-in Vidking page fallback; there is already one, don't add another | none |

`web-embed` URL templates must be `https://` and can use these placeholders:

| Placeholder | Replaced with |
| --- | --- |
| `{tmdbId}` | The TMDB id of the movie or series |
| `{imdbId}` | The IMDb id (`tt…`); the source is skipped for titles that have none |
| `{season}` / `{episode}` | Season and episode numbers (TV only) |

```json
"engine": {
  "type": "web-embed",
  "movieUrl": "https://example-player.com/movie/{tmdbId}",
  "tvUrl": "https://example-player.com/tv/{tmdbId}/{season}/{episode}",
  "priority": 30
}
```

### 2. Write the entry

Add it to the `extensions` array in [`index.json`](index.json), before the `web-fallback` entry.

```json
{
  "id": "sage",
  "name": "Sage",
  "description": "Web player route with wide series coverage. Steps in when the main routes fail.",
  "version": "1.0.0",
  "versionCode": 1,
  "apiVersion": 1,
  "authors": ["your-github-name"],
  "language": "Multi",
  "tags": ["movies", "series", "fallback"],
  "status": 3,
  "updatedAt": "2026-09-26",
  "installedByDefault": false,
  "fallback": true,
  "engine": { "type": "web-embed", "movieUrl": "…", "tvUrl": "…", "priority": 30 }
}
```

Rules the catalog follows:

- **`id`** is permanent and unique. Installs, stats and saved preferences hang off it, so never
  rename it; to replace a source, add a new id and retire the old one.
- **`name`**: official routes are named after Valorant agents. Keep to that for official entries.
- **`description`**: one short sentence on what the source is good for. No hostnames or ads.
- **`language`**: `Multi`, or the language of the audio when the route is a dub (`English`,
  `Hindi`…). The Audio page uses this to offer the route as a language choice.
- **`status`**: `3` (Beta) for anything new, `1` (Online) once it has worked for a while, `2` (Slow),
  `0` (Down).
- **`fallback: true` is required for `web-embed`.** Web players load a whole page in a hidden
  browser, so they only run when the direct routes return nothing, or when the user asks for
  "Find more" sources.
- **`installedByDefault`**: leave it `false` unless a maintainer asks otherwise. A default is
  installed once per device and stays installed even if the flag is turned off later.
- **`priority`**: lower runs and ranks first. Direct routes use `0`–`9`, web players `20` and up.

### 3. Keep the bundled copy in sync

The APK ships the same catalog at `app/src/main/assets/extensions/official-repo.json`. After editing
`index.json`, copy it over byte for byte:

```sh
cp extensions/index.json app/src/main/assets/extensions/official-repo.json
```

### 4. Check it

```sh
./gradlew :app:testDebugUnitTest --tests "*OfficialCatalogTest*"
```

This fails if the two copies differ, an id is duplicated, an entry cannot run on this app version,
a description is missing, or a web player is not marked as a fallback.

Then try it in the app: install a debug build, open **Settings → Extension marketplace**, install
the source, and play a movie and a TV episode. Web players only run as a fallback, so to test one
on its own, disable the Vidking routes under **Installed** first. **Settings → Sources** in the
player shows which source each stream came from.

### 5. Open a pull request

Use a title like `extensions: add Sage (web player)` and include:

- which titles you tested (at least one movie and one episode) and whether they played;
- whether the source brings subtitles or dubs;
- anything you know about how stable the host is.

## Changing or retiring a source

- **Any change to an entry needs a higher `versionCode`**, or installed apps won't see it. The app
  compares its bundled copy with the published one entry by entry, and the higher `versionCode`
  wins.
- **To retire a source**, don't delete it: set `"status": 0`, bump `versionCode`, and say why in the
  description. A deleted entry would live on in older APKs that bundled it.

## Things that are not accepted

- Sources that exist mainly to serve ads, redirects or pop-ups.
- Hosts that need credentials, API keys or payment.
- Adult content in the official catalog.
- Entries that ask for a new engine type. Open an issue to discuss the engine first; engines are
  Kotlin code in `app/src/main/java/com/ivor/openstream/data/streaming/`.

## Good to know

Every source here depends on third-party sites that change without notice. When one breaks, the
fastest fix is usually a catalog change (a new URL template, a new route, or `status: 0`), not an
app release. That is the point of keeping sources in this file.
