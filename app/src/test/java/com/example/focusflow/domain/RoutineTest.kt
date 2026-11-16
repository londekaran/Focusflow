package com.example.focusflow.domain

import org.junit.Assert.*
import org.junit.Test

class RoutineTest {
    private val work = DayBlock("Work", 9 * 60, 17 * 60, autoFocus = true, allowed = setOf("wa"))
    private val meeting = DayBlock("Meeting", 12 * 60, 13 * 60, allowed = setOf("zoom"))
    private val leisure = DayBlock("Leisure", 20 * 60, 22 * 60, blockOn = false, focusOn = false)
    private val night = DayBlock("Night", 22 * 60, 6 * 60)

    @Test fun findsTheRunningBlockIncludingAcrossMidnight() {
        val all = listOf(work, leisure, night)
        assertEquals("Work", Routine.at(all, 10 * 60)!!.name)
        assertNull(Routine.at(all, 18 * 60))
        assertEquals("Night", Routine.at(all, 23 * 60)!!.name)
        assertEquals("Night", Routine.at(all, 3 * 60)!!.name)
    }

    @Test fun overlappingBlocksUseTheOneThatStartedLast() {
        val all = listOf(work, meeting)
        assertEquals("Work", Routine.at(all, 11 * 60)!!.name)
        assertEquals("Meeting", Routine.at(all, 12 * 60 + 30)!!.name)
        assertEquals("Work", Routine.at(all, 13 * 60)!!.name)   // back to the outer routine when the inner one ends
    }

    @Test fun blockingFollowsTheActiveRoutine() {
        val apps = setOf("wa", "insta", "zoom")
        val all = listOf(work, meeting, leisure)
        assertEquals(setOf("insta", "zoom"), Routine.watchedNow(all, 10 * 60, apps))
        assertEquals(setOf("wa", "insta"), Routine.watchedNow(all, 12 * 60 + 30, apps))
        assertTrue(Routine.watchedNow(all, 21 * 60, apps).isEmpty())   // focus off: nothing restricted
    }

    @Test fun savesAndLoadsEveryField() {
        val back = Routine.decode(Routine.encode(listOf(work, leisure)))
        assertEquals(listOf(work, leisure), back)
    }

    @Test fun oldSavedDataKeepsItsMeaning() {
        // Saved before "focus mode" existed: 7 fields. Blocking off meant no focus.
        val old = "Leisure\t1200\t1320\t25\tfalse\t\tfalse"
        assertFalse(Routine.decode(old).single().focusOn)
        assertTrue(Routine.decode("Work\t540\t1020\t25\ttrue\twa\tfalse").single().focusOn)
    }

    // ---- routine list order ----

    @Test fun routinesAreListedByStartTimeWhateverTheStoredOrder() {
        val gym = DayBlock("Gym", 6 * 60, 7 * 60)
        val stored = listOf(work, leisure, gym, night)
        assertEquals(listOf("Gym", "Work", "Leisure", "Night"), Routine.sortedByStart(stored).map { it.name })
        // the saved list is not touched: alarms refer to routines by position
        assertEquals(listOf("Work", "Leisure", "Gym", "Night"), stored.map { it.name })
    }

    @Test fun changingAStartTimeMovesTheRoutine() {
        val gym = DayBlock("Gym", 6 * 60, 7 * 60)
        val moved = gym.copy(start = 10 * 60, end = 11 * 60)
        assertEquals(listOf("Work", "Gym"), Routine.sortedByStart(listOf(moved, work)).map { it.name })
    }

    @Test fun equalStartsAreOrderedByEndThenName() {
        val a = DayBlock("b", 9 * 60, 12 * 60)
        val b = DayBlock("A", 9 * 60, 12 * 60)
        val c = DayBlock("c", 9 * 60, 10 * 60)
        assertEquals(listOf("c", "A", "b"), Routine.sortedByStart(listOf(a, b, c)).map { it.name })
    }

    @Test fun displayOrderGivesPositionsInTheSavedList() {
        val gym = DayBlock("Gym", 6 * 60, 7 * 60)
        assertEquals(listOf(1, 0), Routine.displayOrder(listOf(work, gym)))
    }

    // ---- blocking follows the routine the session belongs to ----

    @Test fun blockingForASessionUsesThatRoutineNotTheClock() {
        val apps = setOf("wa", "insta", "zoom")
        // The clock says Leisure (blocking off), but the session was started for Work.
        assertEquals(setOf("insta", "zoom"), Routine.watchedFor(work, apps))
        assertEquals(setOf("wa", "insta"), Routine.watchedFor(meeting, apps))   // Meeting allows zoom
        assertTrue(Routine.watchedNow(listOf(work, leisure), 21 * 60, apps).isEmpty())
    }

    @Test fun blockingOffOrFocusOffMeansNothingIsWatched() {
        val apps = setOf("wa", "insta")
        assertTrue(Routine.watchedFor(work.copy(blockOn = false), apps).isEmpty())
        assertTrue(Routine.watchedFor(work.copy(focusOn = false), apps).isEmpty())
    }

    // ---- manual start for a routine ----

    private fun sec(h: Int, m: Int = 0, s: Int = 0) = h * 3600 + m * 60 + s

    @Test fun manualFocusInProgressRunsUntilTheRoutineEnds() {
        // Work 09:00-17:00, now 15:30:00 -> 90 minutes
        assertEquals(90, Routine.manualFocusMinutes(work, sec(15, 30)))
        // 15:30:10 -> 89 min 50 s left -> rounded up to 90
        assertEquals(90, Routine.manualFocusMinutes(work, sec(15, 30, 10)))
        // last minute of the routine -> at least 1 minute
        assertEquals(1, Routine.manualFocusMinutes(work, sec(16, 59, 30)))
    }

    @Test fun manualFocusOutsideTheRoutineUsesItsWholeLength() {
        assertEquals(8 * 60, Routine.manualFocusMinutes(work, sec(7)))
        assertEquals(8 * 60, Routine.manualFocusMinutes(work, sec(17)))   // the end itself is outside
    }

    @Test fun manualFocusAcrossMidnight() {
        // Night 22:00-06:00
        assertEquals(7 * 60, Routine.manualFocusMinutes(night, sec(23)))   // 23:00 -> 06:00
        assertEquals(3 * 60, Routine.manualFocusMinutes(night, sec(3)))    // after midnight: 03:00 -> 06:00
        assertEquals(8 * 60, Routine.manualFocusMinutes(night, sec(12)))   // not running: its whole length
    }

    @Test fun routineWithoutLengthCannotBeStartedByHand() {
        assertNull(Routine.manualFocusMinutes(DayBlock("Empty", 600, 600), sec(10)))
        assertEquals(0, Routine.lengthMin(DayBlock("Empty", 600, 600)))
    }

    @Test fun secondsLeftHandlesBothSidesOfMidnight() {
        assertEquals(7 * 3600, Routine.secondsLeft(night, sec(23)))
        assertEquals(3 * 3600, Routine.secondsLeft(night, sec(3)))
        assertNull(Routine.secondsLeft(night, sec(12)))
        assertEquals(8 * 60, Routine.lengthMin(night))
    }
}
