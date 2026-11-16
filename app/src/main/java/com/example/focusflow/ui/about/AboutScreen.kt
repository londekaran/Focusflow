package com.example.focusflow.ui.about

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflow.ui.components.FocusFlowLogo

private const val DEV_NAME = "Karan N Londe"
private const val DEV_EMAIL = "karan.n.londe@gmail.com"

/** Reads a string from the manifest's meta-data (the developer details live there), with a fallback. */
private fun meta(ctx: Context, key: String, fallback: String): String = try {
    ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA)
        .metaData?.getString(key) ?: fallback
} catch (e: Exception) { fallback }

private fun versionName(ctx: Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
} catch (e: Exception) { "1.0" }

private val FEATURES = listOf(
    "Tasks with priority, routine, status, due date, start time and estimated duration",
    "A daily timetable built around your routine (work, study, exercise, leisure) with focus and app rules for each part",
    "Tasks and planner in one place, with an optional view of your phone's calendar",
    "Focus timer with breaks and a session report",
    "Distraction tracking and optional blocking of apps you choose, plus an optional adult-content blocker",
    "Reminders, a morning plan and a daily review",
    "Optional voice assistant that speaks reminders and status checks",
    "Insights on your focus time and progress"
)

@Composable
private fun LegalFrame(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(cs.background).systemBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Close, "Close", tint = cs.onSurface,
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose).padding(10.dp)
            )
            Text(
                title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp).semantics { heading() }
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = content)
    }
}

@Composable
fun AboutScreen(onClose: () -> Unit, onTerms: () -> Unit, onPrivacy: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val version = remember { versionName(ctx) }
    val status = remember { meta(ctx, "com.focusflow.VERSION_STATUS", "") }
    val developer = remember { meta(ctx, "com.focusflow.DEVELOPER", DEV_NAME) }
    val email = remember { meta(ctx, "com.focusflow.DEVELOPER_EMAIL", DEV_EMAIL) }

    LegalFrame("About", onClose) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FocusFlowLogo(64.dp)
            Text("AMU", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text("Adaptive Mindful Understanding", fontSize = 14.sp, color = cs.onSurfaceVariant)
            Text(
                "Version $version" + if (status.isNotBlank()) " ($status)" else "",
                fontSize = 14.sp, color = cs.onSurfaceVariant
            )
        }
        Heading("What it is")
        Body(
            "AMU (Adaptive Mindful Understanding) helps you plan your day around your own routine, focus on one task at a time, and finish what matters. " +
                "It brings tasks, planning, a focus timer, reminders and insights into one app that keeps your data on your phone."
        )
        Heading("Key features")
        FEATURES.forEach { f ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Check, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                Text(f, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
            }
        }
        Heading("Developer")
        Body(developer)
        Heading("Contact and support")
        Body(email)
        Heading("Acknowledgements")
        Body("The interface uses the Inter typeface, under its open font licence. The app is built with Android Jetpack libraries.")
        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = cs.outlineVariant)
        LinkRow("Terms & Conditions", onTerms)
        LinkRow("Privacy Policy", onPrivacy)
        Text(
            "© 2026 $developer. All rights reserved.",
            fontSize = 12.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
        )
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Text(
        text, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 12.dp)
    )
}

@Composable
private fun Heading(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp).semantics { heading() }
    )
}

@Composable
private fun Body(text: String) {
    Text(text, fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private class Section(val title: String, val body: String)

@Composable
private fun LegalText(intro: String, sections: List<Section>) {
    Body(intro)
    sections.forEachIndexed { i, s ->
        Heading("${i + 1}. ${s.title}")
        Body(s.body)
    }
    Spacer(Modifier.height(32.dp))
}

private val TERMS_INTRO =
    "These terms apply to your use of the AMU app. AMU is currently a beta release, so features may change."

private val TERMS = listOf(
    Section("Acceptance of terms",
        "By installing or using AMU you agree to these terms. If you do not agree, please stop using the app and uninstall it."),
    Section("User responsibilities",
        "Use the app lawfully and for its intended purpose of personal planning and focus. You are responsible for the information you enter, " +
            "for the permissions you grant on your phone, and for keeping your phone secure. AMU is a productivity aid; " +
            "reminders and timers depend on your phone's settings and battery management, so do not rely on them alone for time-critical or safety-critical matters."),
    Section("Account and profile usage",
        "Sign-in is currently a preview. The email you enter and your profile details (name, phone, birthday, gender, occupation, organisation, " +
            "main purpose and picture) are optional or lightly checked and are kept only on your phone. No account is created on a server and your password is not saved. " +
            "Please enter only details that are yours to share."),
    Section("Subscription terms",
        "The Free and Pro plans shown in the app are a preview. No payment is requested or taken, and Pro features are not available yet. " +
            "Choosing a plan only records your choice on this phone. If paid plans are introduced later, their price, billing and cancellation terms " +
            "will be shown clearly before you pay, and these terms will be updated."),
    Section("Intellectual property",
        "The app, its design, name and content belong to its developer, Karan N Londe. You receive a personal, non-exclusive, non-transferable right to use it. " +
            "You may not copy, modify, distribute or reverse engineer it except where the law allows. The tasks and notes you enter remain yours. " +
            "The Inter typeface is used under its own open licence."),
    Section("Limitation of liability",
        "The app is provided \"as is\", without warranties of any kind. To the extent the law allows, the developer is not liable for missed or delayed reminders, " +
            "lost data (for example if you delete data, uninstall the app or lose your phone), or any indirect or consequential loss arising from use of the app. " +
            "Nothing here limits rights you have under law that cannot be excluded."),
    Section("Changes to terms",
        "These terms may be updated as the app changes. The latest version is always available in the app under Settings > About and legal. " +
            "Continuing to use the app after an update means you accept the updated terms."),
    Section("Contact information", "Questions about these terms: $DEV_EMAIL (Developer: $DEV_NAME).")
)

private val PRIVACY_INTRO =
    "AMU is designed to keep your information on your phone. The only time it uses the internet is the optional adult-content blocker, which looks up website names; your tasks, plans and settings are never sent to the developer or to anyone else."

private val PRIVACY = listOf(
    Section("Information collected",
        "AMU stores what you enter or generate while using it: tasks and their details, reschedule history, focus sessions, breaks, " +
            "interruptions from distracting apps, your list of chosen distracting apps, and your settings (such as reminder times and work hours). " +
            "There are no analytics, advertising or tracking tools in the app."),
    Section("Account and profile information",
        "During setup or in Settings you can enter an email, name, phone number, birthday, gender, occupation, organisation, your main purpose (Work, Study or Personal) " +
            "and a profile picture. Only the name is required. Sign-in is a preview: the email is kept on your phone, no password is saved, and nothing is sent to Google or any server. " +
            "Your chosen plan (Free or Pro) is also just remembered on the phone. A profile picture you choose is cropped and saved in the app's private storage."),
    Section("App permissions and their purpose",
        "Notifications: show reminders, the focus timer and voice prompts.\n" +
            "Exact alarms and start on boot: deliver reminders on time and restore them after a restart.\n" +
            "Foreground service: keep the focus timer, breaks and spoken reminders running reliably.\n" +
            "Calendar (read only, optional): show your calendar events in the planner and find free time. Events are read on the phone and are not copied elsewhere.\n" +
            "Usage access (optional): see which of your chosen apps were opened during a focus session. Only the apps you selected are tracked, and only during sessions.\n" +
            "Display over other apps (optional): show a warning over a distracting app you chose, during a focus session.\n" +
            "Each permission can be turned off in Android settings at any time; the related feature then stops. " +
            "The app never reads your messages, keystrokes or passwords."),
    Section("Data storage and handling",
        "Your data is stored in the app's private storage on your phone (a local database and settings file). " +
            "The app does not add its own encryption on top of what Android provides for device storage. " +
            "Android's own device backup may include app data if you have backup turned on in your phone's settings; that is controlled by Android and your Google account settings, not by AMU."),
    Section("Third-party integrations",
        "AMU has no third-party accounts, SDKs or servers. It uses these services already on your phone: your calendar app's data (if you allow it), " +
            "the phone's text-to-speech engine to speak reminders, and, if you turn on the adult-content blocker, a family-safe DNS service that receives website names only. " +
            "AMU does not send it anything else."),
    Section("Data sharing and security",
        "AMU does not share your data with anyone. " +
            "Because the data stays on your phone, its security depends on your phone's lock screen and updates. Use a screen lock, and note that anyone who can open your unlocked phone can open the app."),
    Section("User privacy rights",
        "You can view and edit your profile and settings in the app, change or remove your profile picture, and turn permissions off at any time. " +
            "Because the developer holds no copy of your data, there is nothing to request from the developer; you control all of it directly on your phone."),
    Section("Data deletion",
        "Settings > Privacy > \"Delete all my data\" permanently deletes your tasks, history, focus sessions, interruptions, break records and chosen apps. " +
            "Your settings and profile details are kept; you can edit or remove them yourself, and you can remove your picture in Settings > Profile. " +
            "Uninstalling the app removes all of its data from the phone. Copies held by Android backup, if enabled, are managed in your phone's backup settings."),
    Section("Policy updates",
        "This policy may change as the app changes. The latest version is always in the app under Settings > About and legal."),
    Section("Contact information", "Privacy questions: $DEV_EMAIL (Developer: $DEV_NAME).")
)

@Composable
fun TermsScreen(onClose: () -> Unit) = LegalFrame("Terms & Conditions", onClose) { LegalText(TERMS_INTRO, TERMS) }

@Composable
fun PrivacyScreen(onClose: () -> Unit) = LegalFrame("Privacy Policy", onClose) { LegalText(PRIVACY_INTRO, PRIVACY) }
