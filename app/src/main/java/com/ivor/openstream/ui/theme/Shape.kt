package com.ivor.openstream.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Material 3 Expressive Shape Tokens (as defined in shapes.md)
// These define the standard corner radius scale, generally larger/more rounded than M3.

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // Mini-player controls
    small = RoundedCornerShape(12.dp),       // Chips
    medium = RoundedCornerShape(20.dp),      // Search list items, Album cards
    large = RoundedCornerShape(28.dp),       // Floating toolbars, Bottom sheets
    extraLarge = RoundedCornerShape(36.dp)   // Main player background, Hero sections
)

// Note: Usage of expressive polygons (MaterialShapes.Sunny, MaterialShapes.Cookie, etc.)
// should be imported directly from androidx.compose.material3.MaterialShapes
// as per the project documentation. No manual re-definition here to avoid duplication.
