package com.example.focusflow.ui.distraction

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focusflow.data.DistractingApp
import com.example.focusflow.data.DistractionRepository
import com.example.focusflow.data.Interruption
import com.example.focusflow.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(val pkg: String, val label: String)

/** Apps that are commonly distracting; shown first in the picker (only if installed). Nothing is pre-selected. */
val SUGGESTED = setOf(
    "com.instagram.android", "com.facebook.katana", "com.twitter.android", "com.snapchat.android",
    "com.google.android.youtube", "com.reddit.frontpage", "com.zhiliaoapp.musically", "com.whatsapp",
    "com.netflix.mediaclient", "com.pinterest", "com.linkedin.android", "org.telegram.messenger",
    "com.discord", "com.instagram.barcelona", "com.facebook.orca"
)

class DistractionViewModel(
    private val repo: DistractionRepository,
    private val settings: SettingsStore,
    private val appContext: Context
) : ViewModel() {

    val apps: StateFlow<List<DistractingApp>> =
        repo.apps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val interruptions: StateFlow<List<Interruption>> =
        repo.interruptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _monitoring = MutableStateFlow(settings.monitoringEnabled)
    val monitoring: StateFlow<Boolean> = _monitoring

    private val _liveAlerts = MutableStateFlow(settings.liveAlerts)
    val liveAlerts: StateFlow<Boolean> = _liveAlerts

    private val _blockApps = MutableStateFlow(settings.blockApps)
    val blockApps: StateFlow<Boolean> = _blockApps

    private val _installed = MutableStateFlow<List<AppInfo>>(emptyList())
    val installed: StateFlow<List<AppInfo>> = _installed

    fun setMonitoring(on: Boolean) {
        settings.monitoringEnabled = on
        _monitoring.value = on
    }

    fun setLiveAlerts(on: Boolean) {
        settings.liveAlerts = on
        _liveAlerts.value = on
    }

    fun setBlockApps(on: Boolean) {
        settings.blockApps = on
        _blockApps.value = on
    }

    fun loadInstalled() {
        viewModelScope.launch {
            _installed.value = withContext(Dispatchers.Default) { queryLaunchableApps(appContext) }
        }
    }

    fun setApp(info: AppInfo, on: Boolean) {
        viewModelScope.launch {
            if (on) repo.add(DistractingApp(info.pkg, info.label)) else repo.remove(info.pkg)
        }
    }
}

@Suppress("DEPRECATION")
private fun queryLaunchableApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != ctx.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}
