package app.mittsu.lessons

/**
 * WaniKani mnemonics use light markup such as `<radical>Ground</radical>` or `<reading>いち</reading>`.
 * This splits a mnemonic into plain and tagged runs so the UI can colour them. Unknown tags are
 * dropped and their text kept; nothing is ever rendered as HTML.
 */
object MnemonicMarkup {
    enum class Tag { RADICAL, KANJI, VOCABULARY, READING, MEANING, JA, EMPHASIS }

    data class Run(val text: String, val tag: Tag?)

    private val tagPattern = Regex("<(/?)([a-zA-Z]+)[^>]*>")

    fun parse(text: String): List<Run> {
        val runs = mutableListOf<Run>()
        val stack = ArrayDeque<Tag?>()
        var last = 0
        fun emit(until: Int) {
            if (until > last) {
                val chunk = text.substring(last, until)
                val tag = stack.lastOrNull { it != null }
                if (runs.isNotEmpty() && runs.last().tag == tag) runs[runs.lastIndex] = Run(runs.last().text + chunk, tag)
                else runs += Run(chunk, tag)
            }
        }
        for (m in tagPattern.findAll(text)) {
            emit(m.range.first)
            last = m.range.last + 1
            val closing = m.groupValues[1] == "/"
            val tag = tagFor(m.groupValues[2])
            if (closing) { if (stack.isNotEmpty()) stack.removeLast() } else stack.addLast(tag)
        }
        emit(text.length)
        return runs.map { it.copy(text = it.text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")) }
    }

    private fun tagFor(name: String): Tag? = when (name.lowercase()) {
        "radical" -> Tag.RADICAL
        "kanji" -> Tag.KANJI
        "vocabulary", "vocab" -> Tag.VOCABULARY
        "reading" -> Tag.READING
        "meaning" -> Tag.MEANING
        "ja" -> Tag.JA
        "b", "strong", "i", "em" -> Tag.EMPHASIS
        else -> null
    }
}
