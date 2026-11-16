package com.example.focusflow.domain

import com.example.focusflow.data.Task
import com.example.focusflow.data.TaskStatus
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

enum class VoiceKind { MORNING, WORK_START, INCOMING, DEADLINE, CHECKIN, ROUTINE_START, TASK_CHECK }

/** One thing the assistant will say at a given moment. [key] is unique, and is how its alarm is identified. */
data class VoiceEvent(val kind: VoiceKind, val taskId: Long, val atMs: Long, val key: String)

enum class VoiceOp { ONGOING, PENDING, DISCARD, DONE, EXTEND }

/** A button on a notification or in the status dialog. [minutes] is used by EXTEND. */
data class VoiceAction(val op: VoiceOp, val label: String, val minutes: Int = 0)

/** What to show and say for an event: notification title and text, the sentences to speak, buttons, and the screen to open. */
data class VoiceContent(
    val title: String, val text: String, val speech: List<String>,
    val actions: List<VoiceAction>, val route: String
)

/**
 * The voice workflow. A task's deadline is the end of its slot: start time plus its estimated duration
 * (30 minutes when it has no estimate). Plain Kotlin, so every rule here is unit tested.
 */
object VoicePlan {
    fun durationOf(t: Task): Int = t.estimateMin ?: Schedule.DEFAULT_TASK_MIN

    private fun at(date: LocalDate, min: Int, zone: ZoneId): Long =
        date.atStartOfDay(zone).plusMinutes(min.toLong()).toInstant().toEpochMilli()

    /**
     * All upcoming voice events for today and tomorrow, soonest first:
     * the briefing before work, the start of work, a status check every [checkEvery] minutes during work hours,
     * and for each scheduled task an "incoming" alert before it starts and a status question before its deadline.
     */
    fun events(
        tasks: List<Task>, today: LocalDate, nowMs: Long, zone: ZoneId,
        workStart: Int, workEnd: Int, briefingOffset: Int, incomingLead: Int, deadlineLead: Int, checkEvery: Int,
        blocks: List<DayBlock> = emptyList(), taskCheckEvery: Int = 0
    ): List<VoiceEvent> {
        val byRoutine = blocks.isNotEmpty()   // with a timetable, each routine gets its own briefing instead of the work-hours ones
        val out = ArrayList<VoiceEvent>()
        for (d in listOf(today, today.plusDays(1))) {
            val ds = d.toString()
            if (byRoutine) {
                blocks.forEachIndexed { i, b ->
                    out.add(VoiceEvent(VoiceKind.ROUTINE_START, i.toLong(), at(d, maxOf(0, b.start - briefingOffset), zone), "routine:$ds:$i"))
                }
            } else {
                out.add(VoiceEvent(VoiceKind.MORNING, 0, at(d, maxOf(0, workStart - briefingOffset), zone), "morning:$ds"))
                out.add(VoiceEvent(VoiceKind.WORK_START, 0, at(d, workStart, zone), "start:$ds"))
            }
            if (checkEvery > 0 && !byRoutine) {
                var m = workStart + checkEvery
                var n = 1
                while (m < workEnd) {
                    out.add(VoiceEvent(VoiceKind.CHECKIN, 0, at(d, m, zone), "checkin:$ds:$n"))
                    m += checkEvery
                    n++
                }
            }
            tasks.filter { it.date == ds && !it.done && it.status != TaskStatus.DISCARDED && it.startMin != null }.forEach { t ->
                val start = t.startMin ?: return@forEach
                val end = start + durationOf(t)
                if (t.status != TaskStatus.ONGOING) {
                    out.add(VoiceEvent(VoiceKind.INCOMING, t.id, at(d, maxOf(0, start - incomingLead), zone), "incoming:${t.id}"))
                }
                // While the task runs, ask for its status every [taskCheckEvery] minutes.
                if (taskCheckEvery > 0) {
                    var m = start + taskCheckEvery
                    var n = 1
                    while (m < end - deadlineLead) {
                        out.add(VoiceEvent(VoiceKind.TASK_CHECK, t.id, at(d, m, zone), "tcheck:${t.id}:$n"))
                        m += taskCheckEvery; n++
                    }
                }
                // Ask before the deadline, but never before the task is at least half way through.
                val ask = maxOf(end - deadlineLead, start + (end - start) / 2)
                out.add(VoiceEvent(VoiceKind.DEADLINE, t.id, at(d, ask, zone), "deadline:${t.id}"))
            }
        }
        return out.filter { it.atMs > nowMs }.sortedBy { it.atMs }
    }
}

/** Moving tasks when one runs long. */
object Replan {
    /**
     * Gives [taskId] [deltaMin] more minutes and pushes later tasks of the same day forward, but only as far as
     * needed: a task that still starts after the previous one ends stays where it is, so gaps absorb the delay.
     * Returns only the tasks that changed (the extended one first).
     */
    fun extend(tasks: List<Task>, taskId: Long, deltaMin: Int): List<Task> {
        val target = tasks.firstOrNull { it.id == taskId } ?: return emptyList()
        val extended = target.copy(estimateMin = VoicePlan.durationOf(target) + deltaMin)
        val targetStart = target.startMin ?: return listOf(extended)
        val changed = arrayListOf(extended)
        var previousEnd = targetStart + VoicePlan.durationOf(extended)
        val following = tasks
            .filter {
                it.id != taskId && it.date == target.date && !it.done && it.status != TaskStatus.DISCARDED &&
                    (it.startMin ?: -1) >= targetStart
            }
            .sortedWith(compareBy({ it.startMin }, { it.id }))
        for (t in following) {
            val start = t.startMin ?: continue
            val length = VoicePlan.durationOf(t)
            if (start < previousEnd) {
                val moved = minOf(previousEnd, 1439)
                changed.add(t.copy(startMin = moved))
                previousEnd = moved + length
            } else {
                previousEnd = start + length
            }
        }
        return changed
    }
}

/** The sentences and buttons for each event. */
object VoiceMessages {
    /** "9:30 AM", written out so a speech engine reads it naturally. */
    fun clock(min: Int): String {
        val m = ((min % 1440) + 1440) % 1440
        val h = m / 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return String.format(Locale.US, "%d:%02d %s", h12, m % 60, if (h < 12) "AM" else "PM")
    }

    private fun openTasks(tasks: List<Task>, today: LocalDate): List<Task> =
        tasks.filter { it.date == today.toString() && !it.done && it.status != TaskStatus.DISCARDED }
            .sortedWith(compareBy({ it.startMin ?: Int.MAX_VALUE }, { -it.priority }, { it.id }))

    private fun listSpeech(open: List<Task>): List<String> {
        val lines = ArrayList<String>()
        open.take(6).forEachIndexed { i, t ->
            val time = t.startMin?.let { " at ${clock(it)}" } ?: ""
            lines.add((if (i == 0) "First, " else "Then, ") + "${t.title}$time.")
        }
        if (open.size > 6) lines.add("And ${open.size - 6} more.")
        return lines
    }

    private fun listText(open: List<Task>): String {
        val shown = open.take(4).joinToString("\n") { (it.startMin?.let { m -> clock(m) + "  " } ?: "") + it.title }
        return if (open.size > 4) shown + "\n+${open.size - 4} more" else shown
    }

    /** The task being worked on now: an ongoing one, otherwise the one whose slot contains the current time. */
    fun currentTask(open: List<Task>, nowMin: Int): Task? =
        open.filter { it.status == TaskStatus.ONGOING }.minByOrNull { it.startMin ?: 0 }
            ?: open.firstOrNull { t -> t.startMin?.let { it <= nowMin && nowMin < it + VoicePlan.durationOf(t) } == true }

    fun build(kind: VoiceKind, taskId: Long, tasks: List<Task>, today: LocalDate, nowMin: Int, blocks: List<DayBlock> = emptyList()): VoiceContent? {
        val open = openTasks(tasks, today)
        val noTasks = "No tasks added—please add tasks."
        return when (kind) {
            VoiceKind.MORNING ->
                if (open.isEmpty()) VoiceContent("Good morning", "No tasks added. Please add tasks.", listOf(noTasks), emptyList(), "tasks")
                else VoiceContent(
                    "Today's plan", listText(open),
                    listOf("Good morning, these are your planned tasks for today.") + listSpeech(open), emptyList(), "plan"
                )

            VoiceKind.WORK_START ->
                if (open.isEmpty()) VoiceContent(
                    "Time to start", "No tasks added. Please add tasks.",
                    listOf("Good morning, let's start today's work.", noTasks), emptyList(), "tasks"
                )
                else VoiceContent(
                    "Time to start", listText(open),
                    listOf("Good morning, let's start today's work. Here are your tasks.") + listSpeech(open), emptyList(), "plan"
                )

            VoiceKind.ROUTINE_START -> {
                val b = blocks.getOrNull(taskId.toInt()) ?: return null
                val mine = open.filter { it.category == b.name }
                if (mine.isEmpty()) VoiceContent(
                    "${b.name} starts soon", "No tasks added for ${b.name}. Please add tasks.",
                    listOf("${b.name} starts at ${clock(b.start)}. No tasks are listed for it. Please add tasks."), emptyList(), "tasks"
                )
                else VoiceContent(
                    "${b.name} starts soon", listText(mine),
                    listOf("${b.name} starts at ${clock(b.start)}. Here are your tasks.") + listSpeech(mine), emptyList(), "plan"
                )
            }

            VoiceKind.TASK_CHECK -> {
                val t = open.firstOrNull { it.id == taskId && it.startMin != null } ?: return null
                val end = (t.startMin ?: 0) + VoicePlan.durationOf(t)
                VoiceContent(
                    "Status: ${t.title}", "Until ${clock(end)}. Is it ongoing or pending?",
                    listOf("Status check. Is ${t.title} ongoing or pending?"),
                    listOf(VoiceAction(VoiceOp.ONGOING, "Ongoing"), VoiceAction(VoiceOp.PENDING, "Pending")),
                    "status:checkin:${t.id}"
                )
            }

            VoiceKind.INCOMING -> {
                val t = open.firstOrNull { it.id == taskId && it.status != TaskStatus.ONGOING && it.startMin != null } ?: return null
                val start = t.startMin ?: return null
                val mins = start - nowMin
                val when_ = if (mins > 1) "starting in $mins minutes" else "starting now"
                val dur = VoicePlan.durationOf(t)
                val end = start + dur
                VoiceContent(
                    "Starting soon: ${t.title}", "${clock(start)} to ${clock(end)} ($dur min). Is it ongoing, pending, or discarded?",
                    listOf("Task incoming: ${t.title}, $when_. From ${clock(start)} to ${clock(end)}, $dur minutes."),
                    listOf(
                        VoiceAction(VoiceOp.ONGOING, "Ongoing"), VoiceAction(VoiceOp.PENDING, "Pending"),
                        VoiceAction(VoiceOp.DISCARD, "Discard")
                    ),
                    "status:incoming:${t.id}"
                )
            }

            VoiceKind.DEADLINE -> {
                val t = open.firstOrNull { it.id == taskId && it.startMin != null } ?: return null
                val end = (t.startMin ?: 0) + VoicePlan.durationOf(t)
                VoiceContent(
                    "Status: ${t.title}", "Ends at ${clock(end)}. Ongoing or pending? If ongoing, add more time.",
                    listOf("${t.title} ends soon. Is it ongoing or pending? If ongoing, you can add fifteen or thirty minutes."),
                    listOf(
                        VoiceAction(VoiceOp.EXTEND, "Ongoing +15m", 15), VoiceAction(VoiceOp.EXTEND, "Ongoing +30m", 30),
                        VoiceAction(VoiceOp.PENDING, "Pending")
                    ),
                    "status:deadline:${t.id}"
                )
            }

            VoiceKind.CHECKIN -> {
                val current = currentTask(open, nowMin)
                when {
                    open.isEmpty() -> VoiceContent(
                        "Status check", "You have no tasks scheduled. Please add tasks.",
                        listOf("Status check. You have no tasks scheduled. Please add tasks."), emptyList(), "tasks"
                    )
                    current != null -> VoiceContent(
                        "Status check: ${current.title}", "Are you still working on this?",
                        listOf("Status check. Are you still working on ${current.title}?"),
                        listOf(
                            VoiceAction(VoiceOp.DONE, "Done"), VoiceAction(VoiceOp.EXTEND, "+15 min", 15),
                            VoiceAction(VoiceOp.ONGOING, "Still working")
                        ),
                        "status:checkin:${current.id}"
                    )
                    else -> {
                        val next = open.firstOrNull { (it.startMin ?: -1) > nowMin }
                        val speech = arrayListOf("Status check. No task is active right now.")
                        if (next != null) speech.add("Next up: ${next.title} at ${clock(next.startMin ?: 0)}.")
                        VoiceContent(
                            "Status check", next?.let { "No task is active. Next: ${it.title} at ${clock(it.startMin ?: 0)}." }
                                ?: "No task is active right now.",
                            speech, emptyList(), "plan"
                        )
                    }
                }
            }
        }
    }
}
