# AMU – update notes (all seven parts, one coordinated change)

**Not built or run.** The environment this was done in has no Android SDK, Gradle or Kotlin compiler and no
network, so nothing below was compiled or tested on a device. Every file was reviewed by hand and the new pure
logic (ordering, manual-start duration, per-routine blocking) has unit tests in `RoutineTest.kt`, whose expected
values were cross-checked with an independent port. Please run **Build → Make Project** and
`./gradlew testDebugUnitTest` first.

## What changed

| Part | Change |
|---|---|
| 1 Welcome screen | `AuthScreen`: everything centred horizontally; content centred vertically when it fits (scrolls on small screens / with keyboard); content width capped at 480 dp for tablets; logo 56 → 72 dp; shows "AMU – Adaptive Mind Full Understanding" under the title. |
| 2 Routine order | New `Routine.sortedByStart` / `displayOrder`. Used by Plan → Routine (stable keys + animated re-ordering after add/edit/time change), both onboarding routine steps, the task editor's routine list and the Tasks filter menu. The *saved* order is untouched on purpose: reminder alarms address routines by position (`ReminderScheduler.routineId`, `ReminderReceiver`). |
| 3 Focus & apps cleanup | "Focus Timer" stepper and "Keep these apps unblocked" chips removed from the onboarding step **and** from every routine's editor in Plan → Routine. Removed `ALLOWABLE`, the onboarding `focusMin` write and the unused imports. |
| 4 Manual routine focus | "Start Focus for This Routine" button in each routine's Focus section → `RoutineFocus.startManual`. No task needed. Remaining time if the routine is running, otherwise its full length; handles midnight. Hard deadline (`endAt`) ends the session at the routine's end even if paused. Opens the Focus tab. Reuses the existing start path, so repeated taps / alarms can't create a second session. |
| 5 App blocking | Root cause fixed in `FocusService.refreshWatched()` (see below). |
| 6 Focus page | Idle: timer left, scroll selector right. Running/break: selector folds away, same timer animates to centre. Stop/finish restores it. "Enter minutes" button, dialog and state removed. One timer, observing `FocusState`. |
| 7 Task cards | New `ExpandableTaskCard` (routine-card look). Three-dot menu removed from task cards. Tap to expand/collapse, one open at a time, actions inside the card. |

## Root cause of "timer runs but apps are not blocked" (Part 5)

`FocusService` decided what to block with `Routine.watchedNow(blocks, <current clock minute>, …)`: it looked at
whichever routine the **clock** said was running, never at the routine the session was started for. If that
routine had blocking/focus off (e.g. Leisure) or none was running, the session was judged by the wrong rules and
the watched set came out empty, so the overlay never appeared and no interruptions were recorded. The routine
name was already passed to the service (`block` extra) but only used for display and for stale-session checks.

Fix: when a session carries a routine name, `refreshWatched()` looks that routine up by name in the saved
routines every second and uses `Routine.watchedFor(routine, globalDistractingApps)` =
**global distracting apps − that routine's allowed apps**, or nothing if its focus/blocking is off. This is used
by manual routine focus, scheduled whole-routine focus, automatic task focus, and a task started from the Focus
tab (which now passes its routine). A plain start with no routine behaves exactly as before.

Also hardened: monitoring is skipped when there is nothing to watch; `finish()` now clears the watched set and
the overlay path checks `finishing`, closing a race where an in-flight check could re-draw the overlay just after
the session ended; stopping/finishing/routine-end all go through the same `finish()`.

## Files modified
`FocusService.kt`, `domain/Routine.kt`, `system/RoutineFocus.kt`, `ui/focus/FocusScreen.kt`,
`ui/plan/RoutineTab.kt`, `ui/plan/PlanScreen.kt`, `ui/navigation/AppNav.kt`, `ui/onboarding/AuthScreen.kt`,
`ui/onboarding/RoutineSteps.kt`, `ui/tasks/TasksScreen.kt`, `ui/tasks/TaskEditScreen.kt`,
`ui/components/TaskRow.kt` (menu removed, `relDate` made `internal`), test `RoutineTest.kt`.

## New files
`ui/tasks/ExpandableTaskCard.kt`, this `CHANGES.md`.

## Database / migrations
None. No entity, DAO or schema changed; existing tasks, routines, sessions and interruptions are untouched.
Routine settings are stored in the same format.

## Decisions you may want to review
1. **Allowed apps can no longer be edited.** Per your Part 3 the UI is gone, but `DayBlock.allowed` is still
   saved and still used by blocking (Part 5's formula needs it), so defaults (e.g. Work allows WhatsApp, LinkedIn,
   Teams) and any earlier choices keep working.
2. **Tagline text** uses your wording "Adaptive Mind Full Understanding". The About screen still says
   "Adaptive Mindful Understanding"; I left it alone because you didn't ask to change it.
3. **Task cards have a "Details" action** in addition to Edit / Ongoing / Pending / Delete. Tapping a card used
   to open Task Details (description, per-task reschedule history, Start Focus Session); with tap now expanding
   the card, that screen would otherwise be unreachable. The completion checkbox also stays on the card.
4. **Manual routine focus outside the routine's hours** runs for the routine's full length from now (there is no
   "remaining" to calculate). Ending it by hand suppresses the automatic whole-routine restart for that occurrence,
   like ending an automatic session does.
5. The duration wheel is still 1–240 min (it grows to show a longer saved default). With "Enter minutes" gone,
   values above 240 can only come from Settings → Default session length or a task's estimate.
6. When the session ends at a routine's end it is saved with reason `routine_end` (already shown as
   "routine ended" in the session report) and counts the time actually focused.

## Not done / limitations
- Build, device and theme checks (Part 9) could not be run here; see the note at the top.
- If a routine's end time is edited *during* a manual routine session, that session keeps the end it started with.
