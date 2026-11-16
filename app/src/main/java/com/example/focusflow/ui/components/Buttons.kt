package com.example.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.focusflow.ui.theme.FF
import com.example.focusflow.ui.theme.FFShapes

@Composable
private fun ButtonBase(
    text: String, icon: ImageVector?, modifier: Modifier, bg: Color, fg: Color,
    enabled: Boolean, onClick: () -> Unit
) {
    Row(
        modifier.height(52.dp)
            .ffClickable(FFShapes.button, enabled = enabled, onClick = onClick)
            .background(bg)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** Main action of a screen: green fill. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    ButtonBase(
        text, icon, modifier,
        bg = if (enabled) cs.primary else FF.colors.border,
        fg = if (enabled) cs.onPrimary else cs.onSurfaceVariant,
        enabled = enabled, onClick = onClick
    )
}

/** Supporting action: soft green, or a neutral surface when [neutral] is true. */
@Composable
fun SecondaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: ImageVector? = null, enabled: Boolean = true, neutral: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    ButtonBase(
        text, icon, modifier,
        bg = if (neutral) cs.surfaceVariant else cs.primaryContainer,
        fg = if (!enabled) cs.onSurfaceVariant else if (neutral) cs.onSurface else cs.onPrimaryContainer,
        enabled = enabled, onClick = onClick
    )
}

/** Delete and other destructive actions: red text on a soft red surface. Confirm important ones with a dialog. */
@Composable
fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    ButtonBase(
        text, icon, modifier,
        bg = FF.colors.errorContainer,
        fg = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        enabled = enabled, onClick = onClick
    )
}
