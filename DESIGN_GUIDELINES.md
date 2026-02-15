# 🎨 OpenStream Design Guidelines

> *Applying Material 3 Expressive Principles to OpenStream*

This document outlines the design philosophy and specific guidelines for building new features and screens in OpenStream. It synthesizes the latest Material 3 Expressive research to create an app that is not only functional but emotionally engaging.

---

## 🌟 Core Philosophy

OpenStream aims to be **Vibrant, Personal, and Alive**.

*   **Vibrant:** Use bold colors and imagery to celebrate the content (anime).
*   **Personal:** The app should feel like a personal collection, adapting to the user's taste.
*   **Alive:** Motion should be physics-based, fluid, and responsive. Nothing should feel static or mechanical.

---

## 🛠️ Expressive Tactics for OpenStream

### 1. Shape & Containment
*   **Mix Shapes:** Don't use the same corner radius everywhere.
    *   **Large Containers (Cards, Dialogs):** Use `RoundedCornerShape(24.dp)` or `ExpressiveShapes.large`.
    *   **Interactive Elements (Buttons, Chips):** Use `RoundedCornerShape(50)` (Pill) or `ExpressiveShapes.medium`.
    *   **Images/Posters:** Use slightly smaller radii `RoundedCornerShape(12.dp)` to contrast with their containers.
*   **Shape Morphing:** Where possible, animate shape changes (e.g., a card expanding into a full screen details view).

### 2. Color & Typography
*   **Bold Headers:** Use `MaterialTheme.typography.headlineLarge` or `displayMedium` for screen titles. Don't be afraid of large text.
*   **Dynamic Color:** Rely on `MaterialTheme.colorScheme` which pulls from the user's wallpaper or the content's dominant color.
*   **Vibrant Accents:** Use `tertiaryContainer` for high-emphasis, playful elements like "New" tags or genre chips.

### 3. Motion
*   **Spring Animations:** Use `spring(dampingRatio = 0.8f, stiffness = 300f)` for natural movement. Avoid linear animations.
*   **Staggered Lists:** When loading a list (grid of posters), stagger the entry animation of items so they cascade in, rather than appearing all at once.
*   **Shared Axis:** Use `slideInVertically` + `fadeIn` for screen transitions to give a sense of depth and continuity.

### 4. Layout
*   **Immersive Headers:** For detail screens, allow the artwork to bleed into the status bar. Use a gradient scrim to ensure text legibility.
*   **Grid Layouts:** For content lists, use `LazyVerticalGrid` with adequate spacing (`Arrangement.spacedBy(12.dp)`).
*   **Edge-to-Edge:** Ensure all screens draw behind system bars (`WindowCompat.setDecorFitsSystemWindows(window, false)`).

---

## 📐 Component Guidelines

### Content Cards (Movies/Anime)
*   **Aspect Ratio:** 2:3 for posters.
*   **Elevation:** Use `CardDefaults.elevatedCardElevation()` for depth.
*   **Interaction:** Implement a scale-down effect on press (`Modifier.scale(0.95f)`) to provide tactile feedback.

### Lists & Grids
*   **Loading:** Use shimmering placeholders that match the shape of the content, not just generic spinners.
*   **Headers:** Section headers should be bold and have generous padding (`padding(vertical = 16.dp)`).

### Buttons
*   **Primary Actions:** `ExtendedFloatingActionButton` or large `Button` with an icon.
*   **Secondary Actions:** `FilledTonalButton` or `OutlinedButton`.
*   **Toggle Actions (Watch Later):** Use `IconToggleButton` with a clear state change animation (e.g., filled vs. outlined icon).

---

## 🚀 Implementation Checklist for New Screens

1.  [ ] **State Handling:** Define `Loading`, `Success`, `Error` states clearly.
2.  [ ] **Transition:** Apply enter/exit transitions for the screen content.
3.  [ ] **Typography:** Check if headers use Expressive type scales (larger, bolder).
4.  [ ] **Shapes:** Verify that shapes follow the "Mix Shapes" tactic.
5.  [ ] **Motion:** Ensure list items stagger in and interactive elements respond to touch.
6.  [ ] **Edge-to-Edge:** Verify content flows under system bars.

---

*This document serves as the source of truth for UI/UX decisions in OpenStream.*
