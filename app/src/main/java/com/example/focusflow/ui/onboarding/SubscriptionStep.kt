package com.example.focusflow.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.ui.account.FREE_FEATURES
import com.example.focusflow.ui.account.PRO_FEATURES
import com.example.focusflow.ui.account.PlanCard
import com.example.focusflow.ui.components.PrimaryButton
import com.example.focusflow.ui.theme.Spacing

/** Step 3. Dummy plans: the choice is only remembered on this phone. Skip keeps the Free plan. */
@Composable
fun SubscriptionStep(onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val s = remember { ServiceLocator.settings(ctx) }
    var plan by rememberSaveable { mutableStateOf(s.plan) }

    Column(
        Modifier.fillMaxSize().background(cs.background).systemBarsPadding().padding(horizontal = Spacing.screen)
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Choose your plan", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
            Text(
                "Preview only. No payment is taken and nothing is charged. You can change this later in Settings.",
                fontSize = 13.sp, color = cs.onSurfaceVariant
            )
            PlanCard(
                "Free", "Basic", "₹0", "forever", FREE_FEATURES, highlight = false,
                current = plan == "free", button = "Select Free", currentLabel = "Selected"
            ) { plan = "free" }
            PlanCard(
                "Pro", "Coming soon", "₹99", "per month", PRO_FEATURES, highlight = true,
                current = plan == "pro", button = "Select Pro", currentLabel = "Selected"
            ) { plan = "pro" }
        }
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PrimaryButton("Continue", onClick = { s.plan = plan; onDone() }, modifier = Modifier.fillMaxWidth())
            TextButton({ onDone() }) { Text("Skip for now") }
        }
    }
}
