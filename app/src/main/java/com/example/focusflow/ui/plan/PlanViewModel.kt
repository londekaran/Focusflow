package com.example.focusflow.ui.plan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focusflow.data.SettingsStore
import com.example.focusflow.domain.CalEvent
import com.example.focusflow.system.CalendarReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

class PlanViewModel(private val settings: SettingsStore, private val app: Context) : ViewModel() {
    private val _start = MutableStateFlow(settings.dayStartMin)
    val workStart: StateFlow<Int> = _start

    private val _end = MutableStateFlow(settings.dayEndMin)
    val workEnd: StateFlow<Int> = _end

    private val _calendarOn = MutableStateFlow(settings.calendarEnabled)
    val calendarOn: StateFlow<Boolean> = _calendarOn

    private val _events = MutableStateFlow<List<CalEvent>>(emptyList())
    /** Events of the day being looked at. */
    val events: StateFlow<List<CalEvent>> = _events

    private val _monthEvents = MutableStateFlow<List<CalEvent>>(emptyList())
    /** Events of the month shown in the calendar grid. */
    val monthEvents: StateFlow<List<CalEvent>> = _monthEvents

    /** Re-reads the day's window from the routine timetable (call after it changes). */
    fun refreshHours() {
        _start.value = settings.dayStartMin
        _end.value = settings.dayEndMin
    }

    fun setCalendarOn(on: Boolean) {
        settings.calendarEnabled = on
        _calendarOn.value = on
        if (!on) {
            _events.value = emptyList()
            _monthEvents.value = emptyList()
        }
    }

    private fun usable() = _calendarOn.value && CalendarReader.hasPermission(app)

    /** Loads the calendar events of one day (only if the calendar is switched on and permission is granted). */
    fun loadEvents(date: LocalDate) {
        if (!usable()) {
            _events.value = emptyList()
            return
        }
        viewModelScope.launch {
            _events.value = withContext(Dispatchers.IO) { CalendarReader.eventsOn(app, date) }
        }
    }

    fun loadMonth(month: YearMonth) {
        if (!usable()) {
            _monthEvents.value = emptyList()
            return
        }
        viewModelScope.launch {
            _monthEvents.value = withContext(Dispatchers.IO) {
                CalendarReader.eventsBetween(app, month.atDay(1), month.plusMonths(1).atDay(1))
            }
        }
    }
}
