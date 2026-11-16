package com.example.focusflow.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object FFShapes {
    val card = RoundedCornerShape(16.dp)
    val small = RoundedCornerShape(12.dp)
    val button = RoundedCornerShape(12.dp)
    val chip = RoundedCornerShape(10.dp)
}

internal val FFMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)
