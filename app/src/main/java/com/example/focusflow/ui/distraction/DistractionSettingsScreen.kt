package com.example.focusflow.ui.distraction

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focusflow.UiTick
import com.example.focusflow.hasOverlayAccess
import com.example.focusflow.hasUsageAccess
import com.example.focusflow.ui.PillField
import com.example.focusflow.ui.SectionTitle
import com.example.focusflow.ui.card

@Composable
fun DistractionSettingsScreen(vm: DistractionViewModel, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val apps by vm.apps.collectAsState()
    val installed by vm.installed.collectAsState()
    val monitoring by vm.monitoring.collectAsState()
    val live by vm.liveAlerts.collectAsState()
    val block by vm.blockApps.collectAsState()
    val access = remember(UiTick.resume.intValue) { hasUsageAccess(ctx) }
    val overlay = remember(UiTick.resume.intValue) { hasOverlayAccess(ctx) }
    var query by remember { mutableStateOf("") }
    val selected = remember(apps) { apps.map { it.packageName }.toSet() }

    LaunchedEffect(Unit) { vm.loadInstalled() }
    BackHandler(onBack = onClose)

    val filtered = remember(installed, query) {
        installed.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    }
    val suggested = filtered.filter { it.pkg in SUGGESTED }
    val others = filtered.filter { it.pkg !in SUGGESTED }

    Column(
        Modifier.fillMaxSize().background(cs.background).systemBarsPadding().imePadding().padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Close, "Close", tint = cs.onSurface,
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose).padding(10.dp)
            )
            Text(
                "Distracting apps", fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        LazyColumn(Modifier.weight(1f)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    SwitchRow(
                        "Track app usage in focus sessions",
                        "Reads which of your chosen apps were open during a session.",
                        monitoring, true
                    ) { vm.setMonitoring(it) }
                    SwitchRow(
                        "Block distracting apps",
                        "Covers a chosen app with a full-screen warning during a focus session, so you can't use it.",
                        block && monitoring, monitoring
                    ) { vm.setBlockApps(it) }
                    SwitchRow(
                        "Live reminders",
                        "A notification when you open a chosen app mid-session. Used when blocking is off or not allowed.",
                        live && monitoring, monitoring
                    ) { vm.setLiveAlerts(it) }

                    Row(
                        Modifier.fillMaxWidth().card().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Usage access", fontSize = 16.sp)
                            Text(
                                if (access) "Allowed" else "Not allowed. Needed to see app usage.",
                                fontSize = 13.sp, color = cs.onSurfaceVariant
                            )
                        }
                        TextButton({ ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                            Text(if (access) "Manage" else "Allow")
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().card().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Display over other apps", fontSize = 16.sp)
                            Text(
                                if (overlay) "Allowed" else "Not allowed. Needed to block apps.",
                                fontSize = 13.sp, color = cs.onSurfaceVariant
                            )
                        }
                        TextButton({
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                            )
                        }) {
                            Text(if (overlay) "Manage" else "Allow")
                        }
                    }

                    Text(
                        "Android doesn't guarantee instant app-switch detection, so live reminders can arrive a few " +
                            "seconds late. The report after each session is read from Android's usage log and is more " +
                            "accurate. Only the apps you choose are recorded, and nothing leaves your phone. " +
                            "You can turn this off or revoke access at any time.",
                        fontSize = 13.sp, color = cs.onSurfaceVariant
                    )
                }
            }
            item {
                SectionTitle("Distracting apps  ·  ${selected.size} selected")
                PillField(query, { query = it }, "Search apps", {}, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            if (installed.isEmpty()) {
                item { Text("Loading apps…", fontSize = 14.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
            }
            if (suggested.isNotEmpty()) {
                item { SectionTitle("Suggested") }
                items(suggested, key = { it.pkg }) { a -> AppRow(a, a.pkg in selected) { vm.setApp(a, it) } }
            }
            if (others.isNotEmpty()) {
                item { SectionTitle("All apps") }
                items(others, key = { it.pkg }) { a -> AppRow(a, a.pkg in selected) { vm.setApp(a, it) } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().card().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 16.sp, color = if (enabled) cs.onSurface else cs.onSurfaceVariant)
            Text(subtitle, fontSize = 13.sp, color = cs.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun AppRow(app: AppInfo, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).clickable { onChange(!checked) }.padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(app.label, fontSize = 16.sp, maxLines = 1)
            Text(app.pkg, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
        }
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
