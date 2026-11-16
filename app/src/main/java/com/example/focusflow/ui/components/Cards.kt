package com.example.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.focusflow.ui.theme.FFShapes
import com.example.focusflow.ui.theme.Spacing
import androidx.compose.ui.unit.dp

/** The standard card: surface fill, 16dp corners, a subtle border and no shadow. Pass [onClick] to make it tappable. */
@Composable
fun FFCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = Spacing.l,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val base = if (onClick != null) modifier.ffClickable(FFShapes.card, onClick = onClick) else modifier
    Column(
        base.background(cs.surface, FFShapes.card)
            .border(1.dp, cs.outlineVariant, FFShapes.card)
            .padding(padding),
        content = content
    )
}
