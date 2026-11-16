package com.example.focusflow.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflow.data.AuthRepository
import com.example.focusflow.data.DummyAuthRepository
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.ui.Chip
import com.example.focusflow.ui.account.Field
import com.example.focusflow.ui.account.isGmail
import com.example.focusflow.ui.account.validEmail
import com.example.focusflow.ui.components.FocusFlowLogo
import com.example.focusflow.ui.components.PrimaryButton
import com.example.focusflow.ui.components.SecondaryButton
import com.example.focusflow.ui.theme.Spacing

/** Step 1. Login or Sign Up, each with email or Google. Dummy: nothing is verified or sent anywhere. */
@Composable
fun AuthScreen(onDone: () -> Unit, onTerms: () -> Unit = {}, onPrivacy: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val auth: AuthRepository = remember { DummyAuthRepository(ServiceLocator.settings(ctx)) }
    var signUp by rememberSaveable { mutableStateOf(false) }
    var useEmail by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var googleDialog by remember { mutableStateOf(false) }
    var googleInput by remember { mutableStateOf("") }
    var googleError by remember { mutableStateOf<String?>(null) }

    fun finish(e: String, provider: String) {
        if (if (signUp) auth.signUp(e, provider) else auth.signIn(e, provider)) onDone()
    }

    // The whole page is centred horizontally and (when it fits) vertically. A small screen, a large font or the
    // keyboard simply makes it scroll instead of cutting anything off.
    BoxWithConstraints(Modifier.fillMaxSize().background(cs.background).systemBarsPadding().imePadding()) {
        val screenH = maxHeight
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 480.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).heightIn(min = screenH)
                .padding(horizontal = Spacing.screen, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FocusFlowLogo(72.dp)
            Spacer(Modifier.height(4.dp))
            Text("Welcome to AMU", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                "AMU – Adaptive Mind Full Understanding",
                fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Plan your day, focus, and finish what matters.",
                fontSize = 15.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Login", selected = !signUp) { signUp = false; error = null }
                Chip("Sign Up", selected = signUp) { signUp = true; error = null }
            }

            SecondaryButton(
                if (signUp) "Sign up with Google" else "Continue with Google",
                onClick = { googleInput = ""; googleError = null; googleDialog = true },
                modifier = Modifier.fillMaxWidth()
            )
            if (!useEmail) {
                SecondaryButton(
                    if (signUp) "Sign up with Email" else "Continue with Email",
                    onClick = { useEmail = true }, modifier = Modifier.fillMaxWidth(), neutral = true
                )
            } else {
                Text("or use your email", fontSize = 13.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Field(email, { email = it; error = null }, "Email", password = false)
                Field(pass, { pass = it; error = null }, "Password", password = true)
                error?.let { Text(it, fontSize = 13.sp, color = cs.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                PrimaryButton(
                    if (signUp) "Create account" else "Login",
                    onClick = {
                        when {
                            !validEmail(email) -> error = "Enter a valid email address."
                            pass.length < 6 -> error = "Use a password with at least 6 characters."
                            else -> finish(email, "email")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                "Preview only: no account is created, nothing is sent anywhere, and your password is never saved.",
                fontSize = 12.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Text(
                "By continuing you agree to the", fontSize = 12.sp, color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onTerms) { Text("Terms & Conditions") }
                TextButton(onPrivacy) { Text("Privacy Policy") }
            }
        }
    }

    if (googleDialog) {
        AlertDialog(
            onDismissRequest = { googleDialog = false },
            containerColor = cs.surface,
            title = { Text("Choose a Google account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Preview only: type any Gmail address. Nothing is sent to Google.", fontSize = 14.sp, color = cs.onSurfaceVariant)
                    Field(googleInput, { googleInput = it; googleError = null }, "Gmail address", password = false)
                    googleError?.let { Text(it, fontSize = 13.sp, color = cs.error) }
                }
            },
            confirmButton = {
                TextButton({
                    if (isGmail(googleInput)) { googleDialog = false; finish(googleInput, "google") }
                    else googleError = "Enter a Gmail address, like name@gmail.com."
                }) { Text("Continue") }
            },
            dismissButton = { TextButton({ googleDialog = false }) { Text("Cancel") } }
        )
    }
}
