package com.autorally

enum class MKind { DELIVER, COINS, CLEAN, BOOSTS }

/** One objective per track, so every track plays differently. */
class Mission(val kind: MKind, val target: Int, val title: String)

val MISSIONS: Map<String, Mission> = mapOf(
    "village" to Mission(MKind.DELIVER, 75, "Deliver the parcel in 1:15"),
    "country" to Mission(MKind.COINS, 60, "Collect 60 coins"),
    "city" to Mission(MKind.CLEAN, 0, "Finish without a crash"),
    "highway" to Mission(MKind.DELIVER, 100, "Rush the parcel in 1:40"),
    "mountain" to Mission(MKind.BOOSTS, 5, "Grab 5 Nitro boosts")
)

class Achievement(val id: String, val name: String, val desc: String, val reward: Int)

val ACHIEVEMENTS = listOf(
    Achievement("first", "First Finish", "Finish any race", 20),
    Achievement("coins100", "Coin Hoarder", "Collect 100 coins in total", 30),
    Achievement("clean", "Smooth Driver", "Finish a race with no crash", 30),
    Achievement("boost10", "Nitro Fan", "Grab 10 boosts in total", 30),
    Achievement("mission1", "On a Mission", "Complete a mission", 40),
    Achievement("garage", "Garage Owner", "Own 3 vehicles", 50),
    Achievement("tracks", "Explorer", "Unlock 3 tracks", 50),
    Achievement("aiwin", "Beat the Bots", "Win an AI race", 50),
    Achievement("mission5", "Mission Master", "Complete all 5 missions", 100),
    Achievement("expert", "Expert Slayer", "Win an AI race on Expert", 150)
)

/** AI difficulty: speed factor, how far ahead they look, mistake chance, reward multiplier. */
enum class Diff(val label: String, val speed: Float, val look: Float, val err: Float, val reward: Float, val blurb: String) {
    EASY("EASY", 0.86f, 120f, 0.35f, 0.6f, "Relaxed rivals who make mistakes."),
    MEDIUM("MEDIUM", 0.94f, 170f, 0.18f, 1f, "A fair fight. Rivals dodge most obstacles."),
    HARD("HARD", 1.0f, 220f, 0.07f, 1.5f, "Quick, sharp rivals. Few mistakes."),
    EXPERT("EXPERT", 1.05f, 260f, 0f, 2f, "Near-perfect rivals. Good luck!")
}
