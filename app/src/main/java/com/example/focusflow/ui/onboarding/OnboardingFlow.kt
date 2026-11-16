package com.example.focusflow.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.example.focusflow.ui.about.PrivacyScreen
import com.example.focusflow.ui.about.TermsScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * First-run flow: sign in or sign up, profile details, subscription, then [onFinished].
 * The step survives rotation; Back moves one step back and, on the first step, leaves the app.
 * The caller saves "onboarding done" in [onFinished], so the flow never shows again.
 */
@Composable
fun OnboardingFlow(onFinished: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(0) }
    var legal by rememberSaveable { mutableStateOf("") }   // "", "terms" or "privacy"
    BackHandler(enabled = step > 0 && legal.isEmpty()) { step-- }
    if (legal.isNotEmpty()) {
        if (legal == "terms") TermsScreen { legal = "" } else PrivacyScreen { legal = "" }
        return
    }
    when (step) {
        0 -> AuthScreen(onDone = { step = 1 }, onTerms = { legal = "terms" }, onPrivacy = { legal = "privacy" })
        1 -> ProfileSetupScreen(onDone = { step = 2 })
        2 -> PersonaStep(onDone = { step = 3 })
        3 -> RoutineTimesStep(onDone = { step = 4 })
        4 -> RoutineFocusStep(onDone = { step = 5 })
        else -> SubscriptionStep(onDone = onFinished)
    }
}
