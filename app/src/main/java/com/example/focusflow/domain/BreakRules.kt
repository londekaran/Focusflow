package com.example.focusflow.domain

object BreakRules {
    val tips = listOf(
        "Stand up and stretch your shoulders and back.",
        "Take a short walk, even just around the room.",
        "Drink a glass of water.",
        "Rest your eyes: look at something far away."
    )

    fun tip(seed: Int): String = tips[Math.floorMod(seed, tips.size)]
}
