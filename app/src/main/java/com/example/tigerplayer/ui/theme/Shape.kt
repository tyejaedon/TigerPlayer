package com.example.tigerplayer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ModernShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),   // Perfect for album art
    large = RoundedCornerShape(24.dp),    // Perfect for bottom sheets and large cards
    extraLarge = RoundedCornerShape(32.dp)
)