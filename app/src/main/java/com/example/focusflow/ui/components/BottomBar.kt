package com.example.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.focusflow.ui.theme.FF

private data class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val items = listOf(
    NavItem("Home", Icons.Outlined.Home, Icons.Filled.Home),
    NavItem("Plan", Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle),
    NavItem("Focus", Icons.Outlined.Timer, Icons.Filled.Timer),
    NavItem("Insights", Icons.Outlined.Insights, Icons.Filled.Insights)
)

/** The five-destination bottom bar: icon and label, the selected one in green, compact height. */
@Composable
fun FFBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(cs.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp).selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            items.forEachIndexed { i, item ->
                val sel = i == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .selectable(selected = sel, role = Role.Tab, onClick = { onSelect(i) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.height(28.dp).width(52.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (sel) cs.primaryContainer else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (sel) item.selectedIcon else item.icon, item.label,
                            tint = if (sel) cs.primary else cs.onSurfaceVariant, modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        item.label, style = MaterialTheme.typography.labelSmall,
                        color = if (sel) FF.colors.primaryText else cs.onSurfaceVariant, maxLines = 1
                    )
                }
            }
        }
    }
}
