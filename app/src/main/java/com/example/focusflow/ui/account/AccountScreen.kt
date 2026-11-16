package com.example.focusflow.ui.account

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflow.data.ServiceLocator
import com.example.focusflow.ui.Chip
import com.example.focusflow.ui.card
import com.example.focusflow.ui.components.PrimaryButton
import com.example.focusflow.ui.components.ProfileAvatar
import com.example.focusflow.ui.components.SecondaryButton
import com.example.focusflow.ui.fieldColors
import com.example.focusflow.ui.theme.FFShapes

internal fun validEmail(e: String) = Patterns.EMAIL_ADDRESS.matcher(e.trim()).matches()
internal fun isGmail(e: String): Boolean {
    val x = e.trim().lowercase()
    return validEmail(x) && (x.endsWith("@gmail.com") || x.endsWith("@googlemail.com"))
}

/**
 * A dummy sign-in for future use. AMU sends nothing anywhere:
 * the email is only remembered on this phone and the password is never stored.
 */
@Composable
fun AccountScreen(onClose: () -> Unit, onPlans: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val s = remember { ServiceLocator.settings(ctx) }
    var email by remember { mutableStateOf(s.accountEmail) }
    var provider by remember { mutableStateOf(s.accountProvider) }
    var signUp by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var googleDialog by remember { mutableStateOf(false) }
    var googleInput by remember { mutableStateOf("") }
    var googleError by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onClose)

    fun signIn(e: String, p: String) {
        s.accountEmail = e.trim(); s.accountProvider = p
        email = e.trim(); provider = p
        input = ""; pass = ""; error = null
    }

    Column(Modifier.fillMaxSize().background(cs.background).systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Close, "Close", tint = cs.onSurface,
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose).padding(10.dp)
            )
            Text("Account", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (email.isBlank()) {
                Text("Sign in to AMU", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Accounts are a preview for now. Signing in doesn't sync or back up anything yet.",
                    fontSize = 14.sp, color = cs.onSurfaceVariant
                )

                SecondaryButton("Continue with Google", onClick = { googleInput = ""; googleError = null; googleDialog = true }, modifier = Modifier.fillMaxWidth())

                Text("or use your email", fontSize = 13.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Sign in", selected = !signUp) { signUp = false; error = null }
                    Chip("Create account", selected = signUp) { signUp = true; error = null }
                }
                Field(input, { input = it; error = null }, "Email", password = false)
                Field(pass, { pass = it; error = null }, "Password", password = true)
                error?.let { Text(it, fontSize = 13.sp, color = cs.error) }
                PrimaryButton(
                    if (signUp) "Create account" else "Sign in",
                    onClick = {
                        when {
                            !validEmail(input) -> error = "Enter a valid email address."
                            pass.length < 6 -> error = "Use a password with at least 6 characters."
                            else -> signIn(input, "email")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Demo only: no account is created, nothing is sent anywhere, and your password is never saved.",
                    fontSize = 12.sp, color = cs.onSurfaceVariant
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().card().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileAvatar(56.dp, s.userName.ifBlank { email }, s.photoVersion)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(email, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(
                            "Signed in with ${if (provider == "google") "Google" else "email"}  ·  demo",
                            fontSize = 13.sp, color = cs.onSurfaceVariant
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().card().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Subscription", fontSize = 16.sp)
                        Text("${planLabel(s.plan)} plan", fontSize = 13.sp, color = cs.onSurfaceVariant)
                    }
                    TextButton(onPlans) { Text("View plans") }
                }
                SecondaryButton("Sign out", onClick = {
                    s.accountEmail = ""; s.accountProvider = ""
                    email = ""; provider = ""
                }, modifier = Modifier.fillMaxWidth(), neutral = true)
                Text(
                    "Demo only: nothing about this account leaves your phone.",
                    fontSize = 12.sp, color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (googleDialog) {
        AlertDialog(
            onDismissRequest = { googleDialog = false },
            containerColor = cs.surface,
            title = { Text("Choose a Google account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Demo only: type any Gmail address. Nothing is sent to Google.", fontSize = 14.sp, color = cs.onSurfaceVariant)
                    Field(googleInput, { googleInput = it; googleError = null }, "Gmail address", password = false)
                    googleError?.let { Text(it, fontSize = 13.sp, color = cs.error) }
                }
            },
            confirmButton = {
                TextButton({
                    if (isGmail(googleInput)) { signIn(googleInput, "google"); googleDialog = false }
                    else googleError = "Enter a Gmail address, like name@gmail.com."
                }) { Text("Continue") }
            },
            dismissButton = { TextButton({ googleDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
internal fun Field(value: String, onChange: (String) -> Unit, placeholder: String, password: Boolean) {
    TextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        shape = FFShapes.small, colors = fieldColors(),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Email,
            imeAction = ImeAction.Next
        )
    )
}
