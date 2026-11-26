package app.mittsu.quiz

/** Normalisation used to decide whether two answers are "the same" answer. */
object AnswerText {
    private val leadingFiller = Regex("^(to |a |an |the )+")
    private val punctuation = Regex("[^\\p{L}\\p{N} ]")
    private val spaces = Regex("\\s+")

    /** "To Eat" and "eat", "The Sun" and "sun" are treated as the same meaning. */
    fun normaliseMeaning(text: String): String =
        text.lowercase().trim().replace(punctuation, " ").replace(spaces, " ").trim().replace(leadingFiller, "").trim()

    /** Katakana is folded to hiragana so ジン and じん count as the same reading. */
    fun normaliseReading(text: String): String = buildString {
        for (c in text.trim()) {
            append(if (c in 'ァ'..'ヶ') (c - 0x60) else c)
        }
    }

    fun isKana(text: String): Boolean = text.isNotEmpty() && text.all {
        it in 'ぁ'..'ゟ' || it in '゠'..'ヿ' || it == 'ー'
    }

    fun words(meaning: String): Set<String> = normaliseMeaning(meaning).split(' ').filter { it.length > 2 }.toSet()

    fun isVerbMeaning(meaning: String): Boolean = meaning.trim().lowercase().startsWith("to ")

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
