# Extension marketplace

OpenStream no longer hard-codes its sources. Sources are **extensions** published by
**repositories**, browsed in an in-app marketplace, and installed per user.

- Screen: `presentation/marketplace/MarketplaceScreen.kt` (Browse / Installed / Repositories)
- Catalog logic: `data/extensions/`
- Runtime binding: `data/streaming/ExtensionProviderRegistry.kt`
- Official catalog: [`extensions/index.json`](../extensions/index.json), also bundled at
  `app/src/main/assets/extensions/official-repo.json`

## How comparable apps do it

| App | Distribution unit | Discovery | Popularity signal |
| --- | --- | --- | --- |
| **Stremio** | Add-on **manifest** at an HTTP URL (`id`, `version`, `name`, `resources`, `types`, `idPrefixes`, `catalogs`, `behaviorHints`). The client owns the runtime; the add-on only answers protocol requests. | Official add-on catalog in-app, plus community directories that list, search, categorise and rank add-ons. | Community directory ranking / votes — not an app-internal count. |
| **CloudStream** | `.cs3` plugin **binaries** referenced from `repo.json` → `pluginLists` → `plugins.json` entries (`internalName`, `url`, `version`, `apiVersion`, `status`, `authors`, `tvTypes`, `iconUrl`, `fileSize`, `fileHash`). | Users paste a repository URL; the app lists every plugin from every linked list. | `status` health flag and curated repo lists; no install counts. |
| **Mihon / Aniyomi** | Extension **APKs** described by a flat `index.min.json` (`name`, `pkg`, `apk`, `lang`, `code`, `version`, `nsfw`, `sources[]`). | Users add repository URLs; extensions are filtered by language and NSFW flag. | None — plain alphabetical listing. |

Sources: [Stremio manifest format](https://stremio.github.io/stremio-addon-sdk/api/responses/manifest.html),
[Stremio add-on protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md),
[CloudStream JSON repositories](https://recloudstream.github.io/csdocs/devs/create-your-own-json-repository/),
[CloudStream `plugins.json`](https://github.com/recloudstream/extensions),
[Aniyomi/Mihon repository publishing](https://deepwiki.com/yuzono/aniyomi-extensions/7.5-repository-publishing),
[Stremio community add-on directory](https://stremio-addons.net/addons).

## What OpenStream took from each

- **From Stremio:** extensions are *declarative data*, not code. A manifest selects an engine that
  already ships in the APK and configures it. A third-party repository therefore cannot execute
  anything on the device — the biggest safety problem with the CloudStream/Mihon binary model.
- **From CloudStream:** the repository shape (`name`, `description`, `manifestVersion`,
  `extensionLists` indirection), the `status` health codes (0 down, 1 ok, 2 slow, 3 beta), the
  `apiVersion` compatibility gate, and "paste a URL to add a repository".
- **From Mihon/Aniyomi:** a bare JSON array is also a valid index, and language is a first-class
  filter.
- **New here:** store-style discovery — top charts, popularity/trending/rating/recency sorts,
  category chips, search, update badges with *Update all*, and a per-device reliability signal.

## Repository format (`manifestVersion: 1`)

```json
{
  "manifestVersion": 1,
  "name": "OpenStream Official",
  "description": "Sources maintained alongside the app",
  "website": "https://github.com/Ivorisnoob/OpenStream",
  "extensionLists": ["https://example.com/more-extensions.json"],
  "extensions": [
    {
      "id": "yoru",
      "name": "Yoru",
      "description": "Primary route with the broadest episode coverage.",
      "version": "1.1.0",
      "versionCode": 2,
      "apiVersion": 1,
      "authors": ["OpenStream"],
      "language": "Multi",
      "iconUrl": "https://example.com/yoru.png",
      "tags": ["movies", "series", "fast"],
      "status": 1,
      "nsfw": false,
      "installs": 0,
      "installsLast7Days": 0,
      "rating": 0,
      "ratingCount": 0,
      "updatedAt": "2026-08-29",
      "homepage": "https://example.com",
      "fallback": false,
      "installedByDefault": true,
      "engine": {
        "type": "vidking-direct",
        "endpoint": "cdn/sources-with-title",
        "priority": 0,
        "language": null,
        "qualityFilter": null
      }
    }
  ]
}
```

Accepted document shapes:

1. the object above (inline `extensions`, and/or `extensionLists` pointing at more files);
2. `{ "extensions": [ … ] }`;
3. a bare `[ … ]` array of entries.

A malformed entry is skipped rather than failing the whole index. Repository URLs are normalised —
a `github.com/…/blob/…` link becomes a `raw.githubusercontent.com` link, and a bare project link
falls back to `main/extensions/index.json`.

### Engines

| `engine.type` | Backed by | Required fields |
| --- | --- | --- |
| `vidking-direct` | `VidkingDirectProvider` | `endpoint`; optional `language`, `qualityFilter`, `priority` |
| `vidking-webview` | `VidkingWebViewProvider` (slow compatibility resolver) | none |

Anything else parses but is listed as **Unsupported** and cannot be installed, which is how the
catalog stays forward-compatible: publishing an entry for a future engine does not break old builds.
`apiVersion` above `EXTENSION_API_VERSION` is gated the same way.

### Ranking

`MarketplaceRanker` blends publisher signals with local evidence:

- `installs` (log-scaled) and `rating` when the publisher reports them;
- `status`, as a multiplier — a source flagged **down** sinks below a healthy one;
- `engine.priority` as curation order, so a repository that publishes **no** telemetry still ranks
  sensibly. The official repository publishes no install counts, because the app collects none;
- the device's own success rate per extension, recorded by `StreamingRepositoryImpl` on every
  resolve and decayed so recent behaviour dominates.

Trending additionally weights `installsLast7Days` and how recently the entry was updated.

## Runtime

`ExtensionProviderRegistry` turns installed + enabled manifests into `StreamProvider`s at resolve
time, so adding a source is a data change in an index — no Dagger module edit, no app release.
Provider ids are unchanged (`vidking-<extension id>`), so saved server preferences and existing
download records keep working, and the pre-marketplace on/off state is migrated on first launch.

## Publishing a repository

1. Host a JSON document in one of the shapes above (GitHub raw is fine).
2. In the app: **Settings → Extension marketplace → Repositories → Add repository**.
3. Bump `versionCode` when an entry changes; installed users get an **Update** badge.
