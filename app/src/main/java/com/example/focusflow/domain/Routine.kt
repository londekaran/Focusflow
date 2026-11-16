package com.example.focusflow.domain

/** One block of the day. [allowed] are apps that stay usable while [blockOn] is true. Minutes after midnight. */
data class DayBlock(
    val name: String, val start: Int, val end: Int,
    val focusMin: Int = 25, val blockOn: Boolean = true, val allowed: Set<String> = emptySet(),
    val autoFocus: Boolean = false,
    /** Focus mode for this routine. Off = no focus sessions and no app restrictions while it runs. */
    val focusOn: Boolean = true
)

/** Plain Kotlin daily-routine rules (no Android code). */
object Routine {
    val PERSONAS = listOf("Student", "Working employee", "Self-employed", "Other")

    fun defaults(persona: String): List<DayBlock> = when (persona) {
        "Student" -> listOf(
            DayBlock("School", 8 * 60, 14 * 60 + 30, 25, true, setOf("com.google.android.apps.classroom", "com.whatsapp")),
            DayBlock("Homework", 16 * 60, 17 * 60 + 30, 25),
            DayBlock("Study", 18 * 60, 20 * 60, 45),
            DayBlock("Exercise", 6 * 60, 7 * 60, 25, false, focusOn = false),
            DayBlock("Leisure", 20 * 60 + 30, 22 * 60, 25, false, focusOn = false)
        )
        else -> listOf(
            DayBlock("Work", 9 * 60, 17 * 60, 25, true, setOf("com.whatsapp", "com.linkedin.android", "com.microsoft.teams")),
            DayBlock("Gym / Exercise", 6 * 60, 7 * 60, 25, false, focusOn = false),
            DayBlock("Personal work", 18 * 60, 19 * 60 + 30, 25),
            DayBlock("Leisure", 20 * 60, 22 * 60, 25, false, focusOn = false)
        )
    }

    /** True when [min] falls inside [b] (blocks may cross midnight). */
    fun contains(b: DayBlock, min: Int): Boolean =
        if (b.start <= b.end) min in b.start until b.end else (min >= b.start || min < b.end)

    /**
     * The block running at [min], or null. When routines overlap, the one that started most recently wins
     * (it is the more specific plan); a shorter block wins a tie, then the earlier one in the list.
     */
    fun at(blocks: List<DayBlock>, min: Int): DayBlock? =
        blocks.filter { contains(it, min) }
            .minWithOrNull(compareBy({ (min - it.start + 1440) % 1440 }, { (it.end - it.start + 1440) % 1440 }))

    /** Apps to watch right now: [watched] minus the current block's allowed apps; nothing when blocking is off. */
    fun watchedNow(blocks: List<DayBlock>, min: Int, watched: Set<String>, manualSession: Boolean = false): Set<String> {
        val b = at(blocks, min) ?: return watched
        // A session the user started by hand always blocks: they asked for focus, whatever routine it is.
        if (!b.focusOn || !b.blockOn) return if (manualSession) watched else emptySet()
        return watched - b.allowed
    }

    /**
     * The one block that decides blocking for a session that belongs to routine [b]: the distracting apps minus
     * the apps [b] allows, or nothing at all when focus or blocking is switched off for [b]. Unlike [watchedNow]
     * this never looks at the clock, so a session started for one routine cannot pick up another routine's rules.
     */
    fun watchedFor(b: DayBlock, watched: Set<String>): Set<String> =
        if (!b.focusOn || !b.blockOn) emptySet() else watched - b.allowed

    /**
     * Routines in the order they are shown everywhere: earliest start first (then earlier end, then name).
     * Only for display. The saved order is left alone because reminder alarms refer to routines by position.
     * A routine that crosses midnight is placed by the time it starts.
     */
    fun sortedByStart(blocks: List<DayBlock>): List<DayBlock> = displayOrder(blocks).map { blocks[it] }

    /** The positions of [blocks] in display order. Lets an editor show sorted rows while still changing the saved list by position. */
    fun displayOrder(blocks: List<DayBlock>): List<Int> =
        blocks.indices.sortedWith(compareBy<Int>({ blocks[it].start }, { blocks[it].end }, { blocks[it].name.lowercase() }))

    /** Length of [b] in minutes, counting across midnight. 0 when start and end are the same minute. */
    fun lengthMin(b: DayBlock): Int = ((b.end - b.start) % 1440 + 1440) % 1440

    /** Seconds from [nowSec] (seconds after midnight) until [b] ends, or null when [b] is not running at that moment. */
    fun secondsLeft(b: DayBlock, nowSec: Int): Int? {
        if (!contains(b, nowSec / 60)) return null
        return ((b.end * 60 - nowSec) % 86400 + 86400) % 86400
    }

    /**
     * How long a focus session started by hand for [b] should run, in whole minutes: what is left of the routine
     * when it is in progress, otherwise its full length. Null when the routine has no length at all.
     */
    fun manualFocusMinutes(b: DayBlock, nowSec: Int): Int? {
        val left = secondsLeft(b, nowSec)
        if (left != null) return ((left + 59) / 60).coerceAtLeast(1)
        return lengthMin(b).takeIf { it > 0 }
    }

    /** Earliest start and latest end of the timetable (the day's window); [defStart]/[defEnd] when there is none. */
    fun window(blocks: List<DayBlock>, defStart: Int, defEnd: Int): Pair<Int, Int> {
        val ok = blocks.filter { it.start < it.end }
        if (ok.isEmpty()) return defStart to defEnd
        val s = ok.minOf { it.start }; val e = ok.maxOf { it.end }
        return if (e - s >= 60) s to e else defStart to defEnd
    }

    /** Name of the routine running at [min], or null. */
    fun currentName(blocks: List<DayBlock>, min: Int): String? = at(blocks, min)?.name

    fun encode(blocks: List<DayBlock>): String = blocks.joinToString("\n") {
        listOf(it.name.replace('\t', ' ').replace('\n', ' '), it.start, it.end, it.focusMin, it.blockOn, it.allowed.joinToString(","), it.autoFocus, it.focusOn)
            .joinToString("\t")
    }

    fun decode(s: String): List<DayBlock> = s.lines().mapNotNull { line ->
        val p = line.split("\t")
        if (p.size < 6) return@mapNotNull null
        val st = p[1].toIntOrNull() ?: return@mapNotNull null
        val en = p[2].toIntOrNull() ?: return@mapNotNull null
        DayBlock(p[0], st, en, p[3].toIntOrNull() ?: 25, p[4].toBoolean(), p[5].split(",").filter { it.isNotBlank() }.toSet(), p.getOrNull(6).toBoolean(), p.getOrNull(7)?.toBooleanStrictOrNull() ?: p[4].toBoolean())
    }
}
