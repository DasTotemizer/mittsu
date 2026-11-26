package app.mittsu.domain.model

/** Real mode talks to WaniKani. Practice mode never does. */
enum class StudyMode { REAL, PRACTICE }

enum class ThemePreference { SYSTEM, LIGHT, DARK }

enum class Difficulty(val label: String) { EASY("Easy"), NORMAL("Normal"), HARD("Hard") }

enum class ReviewManagementMode(val label: String, val description: String) {
    STANDARD("Standard", "Show every review that is due, like WaniKani does."),
    DAILY_BALANCE("Daily Balance", "Work towards a daily target, oldest reviews first."),
    GENTLE_RECOVERY("Gentle Recovery", "Ease back in after time away with a gradual plan."),
}
