package com.example.focusflow.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.ui.card
import com.example.focusflow.ui.components.PrimaryButton
import com.example.focusflow.ui.components.SecondaryButton
import com.example.focusflow.ui.theme.FFShapes

fun planLabel(plan: String): String = if (plan == "pro") "Pro" else "Free"

internal val FREE_FEATURES = listOf(
    "Tasks, daily plan and weekly planner",
    "Focus timer and breaks",
    "Distraction tracking and app blocking",
    "Reminders and daily review",
    "Basic insights"
)

internal val PRO_FEATURES = listOf(
    "Everything in Free",
    "Hands-free AI assistant: talk to AMU without touching your phone",
    "Smart plan suggestions for your week",
    "Deeper insights and focus reports",
    "Priority support"
)

/** Two demo plans. Nothing is charged and nothing leaves the phone; the choice is just remembered. */
@Composable
fun PlansScreen(onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val s = remember { ServiceLocator.settings(ctx) }
    var plan by remember { mutableStateOf(s.plan) }

    BackHandler(onBack = onClose)

    Column(Modifier.fillMaxSize().background(cs.background).systemBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Close, "Close", tint = cs.onSurface,
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose).padding(10.dp)
            )
            Text("Subscription", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Preview only. No payment is taken and nothing is charged. Pro features aren't available yet.",
                fontSize = 13.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
            )

            PlanCard(
                title = "Free", badge = "Basic", price = "₹0", period = "forever",
                features = FREE_FEATURES, highlight = false, current = plan == "free",
                button = "Use Free"
            ) { plan = "free"; s.plan = "free" }

            PlanCard(
                title = "Pro", badge = "Coming soon", price = "₹99", period = "per month",
                features = PRO_FEATURES, highlight = true, current = plan == "pro",
                button = "Choose Pro"
            ) { plan = "pro"; s.plan = "pro" }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun PlanCard(
    title: String, badge: String, price: String, period: String, features: List<String>,
    highlight: Boolean, current: Boolean, button: String, currentLabel: String = "Current plan", onSelect: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().card()
            .then(if (highlight) Modifier.border(2.dp, cs.primary, FFShapes.card) else Modifier)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                badge, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = if (highlight) cs.onPrimaryContainer else cs.onSurfaceVariant,
                modifier = Modifier.clip(CircleShape)
                    .background(if (highlight) cs.primaryContainer else cs.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
            Text(price, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(" $period", fontSize = 14.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        features.forEach { f ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Check, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Text(f, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        if (current) {
            SecondaryButton(currentLabel, onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false, neutral = true)
        } else if (highlight) {
            PrimaryButton(button, onClick = onSelect, modifier = Modifier.fillMaxWidth())
        } else {
            SecondaryButton(button, onClick = onSelect, modifier = Modifier.fillMaxWidth())
        }
    }
}
