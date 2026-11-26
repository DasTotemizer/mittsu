package app.mittsu.quiz

/** Short text shown after an answer. */
object Explanation {
    fun after(question: Question, choice: Int): String {
        val s = question.subject
        val chars = s.characters ?: ""
        val correct = question.isCorrect(choice)
        val chosen = question.options.getOrNull(choice)
        if (question.isSelfCheck) {
            return "${if (question.type == QuestionType.MEANING) "Meaning" else "Reading"}: " +
                (if (question.type == QuestionType.MEANING) s.primaryMeaning else s.primaryReading) + "."
        }
        return when (question.type) {
            QuestionType.MEANING -> buildString {
                append("$chars means ${s.primaryMeaning}.")
                val others = (s.meanings.drop(1) + s.whitelistMeanings).distinct()
                if (others.isNotEmpty()) append(" Also accepted: ${others.take(4).joinToString(", ")}.")
                if (!correct && chosen?.fromSubject != null) {
                    append(" ${chosen.text} is ${chosen.fromSubject.characters ?: "another subject"}.")
                }
            }
            QuestionType.READING -> buildString {
                append("$chars is read ${s.primaryReading}")
                s.primaryReadingType?.let { append(" (${readingTypeLabel(it)})") }
                append(".")
                val others = s.acceptedReadings.drop(1)
                if (others.isNotEmpty()) append(" Also accepted: ${others.joinToString(", ")}.")
                if (!correct && chosen?.fromSubject != null) {
                    append(" ${chosen.text} is the reading of ${chosen.fromSubject.characters}.")
                }
            }
        }
    }

    fun readingTypeLabel(type: String) = when (type) {
        "onyomi" -> "on'yomi"
        "kunyomi" -> "kun'yomi"
        "nanori" -> "nanori"
        else -> type
    }
}
