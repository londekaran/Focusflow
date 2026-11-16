package com.example.focusflow.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focusflow.FocusState
import com.example.focusflow.data.FocusRepository
import com.example.focusflow.data.FocusSession
import com.example.focusflow.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FocusViewModel(private val repo: FocusRepository, private val settings: SettingsStore) : ViewModel() {
    val sessions: StateFlow<List<FocusSession>> =
        repo.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _focusMin = MutableStateFlow(settings.focusMin)
    val focusMin: StateFlow<Int> = _focusMin

    fun setFocusMin(m: Int) {
        settings.focusMin = m
        _focusMin.value = m
    }

    fun breakMin(): Int = settings.breakMin
    fun setBreakMin(m: Int) { settings.breakMin = m }
    fun breakEnabled(): Boolean = settings.breakEnabled
    fun setBreakEnabled(on: Boolean) { settings.breakEnabled = on }

    init {
        // If the app was killed during a session, close that session record (unless a session is running right now).
        if (!FocusState.running.value) viewModelScope.launch { repo.closeOrphans() }
    }
}
