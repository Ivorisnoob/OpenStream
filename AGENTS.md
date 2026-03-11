# OpenAnime Agent Guide

This file is the operating manual for any agent working in this repository. Follow it strictly.

## Mission

OpenAnime is a native Android app for discovering and streaming anime using Jetpack Compose, Material 3 Expressive, Hilt, Retrofit, Room, and Media3.

The job is not just to make code compile. The job is to keep the app coherent, expressive, maintainable, and unmistakably premium.

## Source Of Truth

When documentation conflicts with code, trust the code.

Some repo docs are stale or use the old product name `OpenStream`. Do not propagate that drift.

For new work:
- Prefer the product name `OpenAnime` in code comments, docs, and user-facing copy unless the user explicitly asks for a rename.
- Verify dependencies in [gradle/libs.versions.toml](/E:/Ivors/Developement/open-source/OpenAnime/gradle/libs.versions.toml).
- Verify theme behavior in [Theme.kt](/E:/Ivors/Developement/open-source/OpenAnime/app/src/main/java/com/ivor/openanime/ui/theme/Theme.kt).
- Verify navigation in [AppNavigation.kt](/E:/Ivors/Developement/open-source/OpenAnime/app/src/main/java/com/ivor/openanime/presentation/navigation/AppNavigation.kt).
- Verify rules in [rules.md](/E:/Ivors/Developement/open-source/OpenAnime/rules.md).

## Non-Negotiable Rules

1. Use Material 3 Expressive first. If an expressive component exists, prefer it over a standard Material 3 substitute.
2. Do not invent APIs, components, or parameters. Check the actual library version before using a feature.
3. Do not create custom UI primitives that duplicate Material 3 functionality just because custom code feels easier.
4. Keep architecture boundaries intact. Presentation should not become a dumping ground for data or networking logic.
5. Do not introduce random visual styles. Every screen should feel like it belongs to the same expressive system.
6. Avoid “safe blandness”. This app should feel vivid, cinematic, and intentional, not like a default template.
7. Preserve edge-to-edge behavior and immersive layouts where the screen benefits from it.
8. If a doc says something broad and the implementation says something specific, follow the implementation.
9. Every meaningful change should leave the codebase more truthful than before. Fix stale naming and misleading comments when you touch them.
10. Compile before closing work whenever feasible.

## Tech Stack

- Kotlin 2.0.21
- Jetpack Compose
- Material 3 Expressive `1.5.0-alpha13`
- Hilt + KSP
- Retrofit + OkHttp + kotlinx serialization
- Coil 3
- Room
- Media3 ExoPlayer
- Single-activity navigation with Navigation Compose

## Current Codebase Shape

Main app entry:
- [OpenAnimeApp.kt](/E:/Ivors/Developement/open-source/OpenAnime/app/src/main/java/com/ivor/openanime/OpenAnimeApp.kt)
- [MainActivity.kt](/E:/Ivors/Developement/open-source/OpenAnime/app/src/main/java/com/ivor/openanime/MainActivity.kt)

Important layers:
- `data/remote`: TMDB and subtitle APIs plus DTOs
- `data/repository`: repository implementations
- `data/local`: Room database, entities, and DAOs
- `domain/repository`: repository interfaces
- `presentation`: screens, navigation, reusable components
- `ui/theme`: color, type, shape, and theme tokens

Important screens:
- Home
- Search
- Details
- Player
- Watch Later
- Downloads
- Watch History

## Architecture Rules

### Layering

- Keep API details in `data/remote`.
- Keep persistence details in `data/local`.
- Keep business-facing contracts in `domain/repository`.
- Keep orchestration in repositories and viewmodels.
- Keep composables focused on rendering state and forwarding user intent.

### ViewModels

- ViewModels own screen state.
- Prefer `StateFlow` for long-lived UI state.
- Keep UI state explicit: loading, success, error, empty, or a clear data class if multiple facets coexist.
- Do not make composables fetch directly from Retrofit, DAOs, or repositories.

### Navigation

- Routes live in [AppNavigation.kt](/E:/Ivors/Developement/open-source/OpenAnime/app/src/main/java/com/ivor/openanime/presentation/navigation/AppNavigation.kt).
- Reuse the existing route patterns and argument style.
- If a new screen is added, wire the route, typed arguments, and callbacks cleanly.

### Dependency Injection

- Use Hilt consistently.
- Add new versions only in [gradle/libs.versions.toml](/E:/Ivors/Developement/open-source/OpenAnime/gradle/libs.versions.toml).
- Do not hardcode versions in Gradle files.

## Material 3 Expressive Philosophy

This app should feel like anime artwork became an operating system surface.

The design target is not “clean enough”. The target is:
- bold hierarchy
- cinematic imagery
- tactile shapes
- rich surfaces
- meaningful motion
- strong containment
- obvious primary actions
- premium rhythm and spacing

### What “Expressive” Means Here

- Big text should look intentional, not merely large.
- Containers should create emphasis, not just separation.
- Motion should feel physical and directional, not decorative noise.
- Surfaces should have hierarchy. Flatness is rarely the right answer.
- Shape variation should create focus and energy without becoming chaotic.
- Artwork should do real visual work, especially on home, details, and player surfaces.

### Inspiration Anchors

When designing or refining a screen, think in terms of:
- streaming app hero moments
- anime key art as a layout driver
- editorial composition rather than plain forms on a page
- expressive motion that supports focus, not novelty
- premium Android-first craft, not generic cross-platform styling

### Design Tone

The UI should feel:
- vivid
- dramatic
- smooth
- warm
- premium
- legible

The UI must not feel:
- flat
- corporate
- timid
- overstuffed
- randomly colorful
- custom-for-the-sake-of-custom

## Material 3 Expressive Rules

1. Start with the theme and component system already present in [Theme.kt](/E:/Ivors/Developement/open-source/OpenAnime/app/src/main/java/com/ivor/openanime/ui/theme/Theme.kt), [Shape.kt](/E:/Ivors/Developement/open-source/OpenAnime/app/src/main/java/com/ivor/openanime/ui/theme/Shape.kt), and [Type.kt](/E:/Ivors/Developement/open-source/OpenAnime/app/src/main/java/com/ivor/openanime/ui/theme/Type.kt).
2. Prefer expressive components such as `MaterialExpressiveTheme`, `LoadingIndicator`, floating toolbars, connected button groups, and expressive button shapes when available.
3. Use `MaterialTheme.colorScheme` roles. Do not hardcode random colors into screens unless there is a strong artistic reason and it still harmonizes with the theme.
4. Favor surface containers over naked backgrounds when grouping content.
5. Use large, expressive typography for page anchors and section headers.
6. Mix shape sizes with purpose. Do not use one radius everywhere.
7. Let hero media breathe. Posters and backdrops should not be cramped by timid padding.
8. Keep touch targets accessible while maintaining visual precision.
9. Motion should emphasize hierarchy changes, focus shifts, and state changes.
10. Do not ship visually dead screens.

## Screen Design Standards

### Home

- Should feel editorial and content-led.
- Hero content must be visually dominant.
- Horizontal lists should read as curated rails, not leftover RecyclerView rows.

### Search

- Must feel fast, focused, and expressive.
- Search input is a hero control, not a tiny utility field.
- Empty states should feel designed, not accidental.

### Details

- The backdrop and metadata should create a strong first impression.
- Sections should feel intentionally sequenced.
- Primary actions must be obvious without overwhelming the art.

### Player

- Playback is the hero.
- Supporting metadata and next actions should be subordinate but still refined.
- Avoid clutter in the player surface.

### Secondary Screens

- Watch Later, Downloads, and History still need expressive hierarchy.
- Utility screens are not exempt from design quality.

## Motion Rules

- Prefer expressive motion already used in the codebase.
- Use directional transitions for screen-level changes.
- Use softer effect motion for opacity and subtle emphasis.
- Avoid gratuitous animation on every child element.
- Never make the UI feel sluggish for the sake of “premium”.

If adding motion:
- it should clarify state
- it should match the rest of the screen
- it should be short enough to keep the app feeling responsive

## Typography Rules

- Use the existing expressive typography scale.
- Display and headline styles are encouraged for screen anchors.
- Do not flatten hierarchy by using `bodyMedium` everywhere.
- Use bold weights deliberately. Heavy text is part of the brand here.
- Keep long-form reading comfortable with sensible line height.

## Shape Rules

- Reuse `ExpressiveShapes`.
- Small interactive controls, cards, hero containers, and overlays should not all share the same corner treatment.
- If introducing `MaterialShapes`, do it intentionally and sparingly.
- Shape experimentation must still feel premium, not gimmicky.

## Color Rules

- Let dynamic color work with the app, not against it.
- Use `primary`, `secondary`, and `tertiary` roles deliberately for emphasis.
- Use surface container tiers to create depth.
- Protect legibility over artwork with scrims and contrast-aware overlays.
- Avoid default purple bias unless the current theme explicitly supports it.

## Content And Copy Rules

- Keep copy concise and confident.
- Avoid generic filler labels like “Item”, “Content”, or “Section”.
- Titles, chip labels, and CTA text should feel product-grade.
- Use sentence case or title case consistently within a feature.

## Engineering Rules For UI Work

- Before creating a custom composable, check whether a Material 3 or expressive component already solves the problem.
- Reuse existing shared UI pieces such as [AnimeCard.kt](/E:/Ivors/Developement/open-source/OpenAnime/app/src/main/java/com/ivor/openanime/presentation/components/AnimeCard.kt) and [ExpressiveBackButton.kt](/E:/Ivors/Developement/open-source/OpenAnime/app/src/main/java/com/ivor/openanime/presentation/components/ExpressiveBackButton.kt) when appropriate.
- If an existing shared component is weak, improve it rather than cloning variants across screens.
- Keep modifier chains readable.
- Prefer stable, named helper composables when a screen becomes too long.
- Do not bury business logic in giant composable blocks.

## Media And Playback Constraints

- The player stack is a hybrid: hidden WebView extraction plus native Media3 playback.
- Treat playback changes as high-risk.
- Verify behavior on-device when touching player or WebView flows.
- If a streaming change relies on undocumented third-party behavior, call that out explicitly.

## Dependency And API Discipline

- Before adding a dependency, verify it exists and fits the current stack.
- Use the version catalog only.
- Prefer platform-consistent solutions before external libraries.
- Do not add a library just to avoid writing a small amount of straightforward code.

## Workflow For Agents

1. Read the relevant code before proposing a fix.
2. Check whether a similar pattern already exists in the app.
3. Preserve architecture boundaries.
4. Raise the visual quality bar if touching UI.
5. Compile after edits when possible.
6. If the change affects runtime behavior significantly, describe the risk plainly.

## Documentation Hygiene

If you touch documentation:
- keep product naming consistent with `OpenAnime`
- remove stale claims rather than stacking new contradictory notes on top
- keep docs shorter and truer rather than broader and vaguer

## Recommended Reference Files

- [rules.md](/E:/Ivors/Developement/open-source/OpenAnime/rules.md)
- [README.md](/E:/Ivors/Developement/open-source/OpenAnime/README.md)
- [Material3ExpressiveDesignGuide.md](/E:/Ivors/Developement/open-source/OpenAnime/Material3ExpressiveDesignGuide.md)
- [material-3-expressive-guide.md](/E:/Ivors/Developement/open-source/OpenAnime/material-3-expressive-guide.md)
- [Theme.kt](/E:/Ivors/Developement/open-source/OpenAnime/app/src/main/java/com/ivor/openanime/ui/theme/Theme.kt)
- [Shape.kt](/E:/Ivors/Developement/open-source/OpenAnime/app/src/main/java/com/ivor/openanime/ui/theme/Shape.kt)
- [Type.kt](/E:/Ivors/Developement/open-source/OpenAnime/app/src/main/java/com/ivor/openanime/ui/theme/Type.kt)
- [AppNavigation.kt](/E:/Ivors/Developement/open-source/OpenAnime/app/src/main/java/com/ivor/openanime/presentation/navigation/AppNavigation.kt)
- [PlayerScreen.kt](/E:/Ivors/Developement/open-source/OpenAnime/app/src/main/java/com/ivor/openanime/presentation/player/PlayerScreen.kt)

## Final Standard

Do not aim for “works”.

Aim for:
- correct
- expressive
- maintainable
- coherent
- device-appropriate
- unmistakably intentional
