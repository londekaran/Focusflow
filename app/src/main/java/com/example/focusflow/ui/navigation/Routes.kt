package com.example.focusflow.ui.navigation

object Routes {
    const val HOME = "home"
    const val TASKS = "tasks"
    const val PLAN = "plan"
    const val FOCUS = "focus"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val DISTRACTIONS = "distractions"
    const val APPS = "distraction_apps"
    const val ACCOUNT = "account"
    const val PLANS = "plans"
    const val ABOUT = "about"
    const val TERMS = "terms"
    const val PRIVACY = "privacy"
    const val DETAILS = "task/{id}"
    const val EDIT = "edit/{id}"

    fun details(id: Long) = "task/$id"

    /** Pass 0 to create a new task. */
    fun edit(id: Long) = "edit/$id"

    /** The four destinations of the bottom bar, in order. */
    val tabs = listOf(HOME, PLAN, FOCUS, INSIGHTS)

    /** Maps a notification or shortcut target such as "review" to a bottom-bar destination. */
    fun tabFor(target: String): String? = when (target) {
        "home" -> HOME
        "tasks", "plan" -> PLAN
        "focus" -> FOCUS
        "review", "insights" -> INSIGHTS
        else -> null
    }
}
