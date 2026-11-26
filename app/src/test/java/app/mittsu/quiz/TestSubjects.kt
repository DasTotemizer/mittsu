package app.mittsu.quiz

import app.mittsu.domain.model.SubjectType

/** Hand-written test fixtures modelled on early WaniKani subjects (test data only). */
object TestSubjects {
    private fun radical(id: Long, level: Int, chars: String, vararg meanings: String) =
        QuizSubject(id, SubjectType.RADICAL, level, chars, meanings.toList())

    private fun kanji(
        id: Long, level: Int, chars: String, meanings: List<String>, accepted: List<String>,
        other: List<String> = emptyList(), type: String = "onyomi", whitelist: List<String> = emptyList(),
        similar: List<Long> = emptyList(), components: List<Long> = emptyList(),
    ) = QuizSubject(id, SubjectType.KANJI, level, chars, meanings, whitelist, accepted, other, type,
        visuallySimilarIds = similar, componentIds = components)

    private fun vocab(id: Long, level: Int, chars: String, meanings: List<String>, readings: List<String>, pos: List<String> = listOf("noun"), whitelist: List<String> = emptyList()) =
        QuizSubject(id, SubjectType.VOCABULARY, level, chars, meanings, whitelist, readings, partsOfSpeech = pos)

    val radicals = listOf(
        radical(1, 1, "一", "Ground"),
        radical(2, 1, "丨", "Stick"),
        radical(3, 1, "口", "Mouth"),
        radical(4, 1, "大", "Big"),
        radical(5, 1, "人", "Person"),
        radical(6, 1, "山", "Mountain"),
        radical(7, 2, "木", "Tree"),
        radical(8, 2, "火", "Fire"),
        radical(9, 3, "犬", "Dog"),
    )

    val ichi = kanji(440, 1, "一", listOf("One"), listOf("いち"), other = listOf("ひと", "かず"), whitelist = listOf("1"))
    val san = kanji(442, 1, "三", listOf("Three"), listOf("さん"), other = listOf("み"), whitelist = listOf("3"))
    val yama = kanji(450, 1, "山", listOf("Mountain"), listOf("さん"), other = listOf("やま"))
    val dai = kanji(453, 1, "大", listOf("Big", "Large"), listOf("たい", "だい"), other = listOf("おお"), similar = listOf(460, 461), components = listOf(4))
    val inu = kanji(460, 2, "犬", listOf("Dog"), listOf("けん"), other = listOf("いぬ"), similar = listOf(453, 461), components = listOf(4))
    val futoi = kanji(461, 2, "太", listOf("Fat"), listOf("たい"), other = listOf("ふと"), similar = listOf(453, 460), components = listOf(4))
    val hi = kanji(470, 2, "火", listOf("Fire"), listOf("か"), other = listOf("ひ"))
    val shita = kanji(471, 1, "下", listOf("Below", "Down", "Under"), listOf("か", "げ"), other = listOf("した", "さ", "くだ", "お"))
    val ue = kanji(472, 1, "上", listOf("Above", "Up", "Over"), listOf("じょう"), other = listOf("うえ", "あ", "のぼ"))
    val hito = kanji(473, 1, "人", listOf("Person"), listOf("にん", "じん"), other = listOf("ひと"))
    val kuchi = kanji(474, 1, "口", listOf("Mouth"), listOf("こう", "く"), other = listOf("くち"))
    val ki = kanji(475, 2, "木", listOf("Tree", "Wood"), listOf("もく", "ぼく"), other = listOf("き"))
    val juu = kanji(476, 1, "十", listOf("Ten"), listOf("じゅう"), other = listOf("とお"), whitelist = listOf("10"))
    val hachi = kanji(477, 1, "八", listOf("Eight"), listOf("はち"), other = listOf("や"), whitelist = listOf("8"))
    val kyuu = kanji(478, 2, "九", listOf("Nine"), listOf("く", "きゅう"), other = listOf("ここの"), whitelist = listOf("9"))
    val kanji = listOf(ichi, san, yama, dai, inu, futoi, hi, shita, ue, hito, kuchi, ki, juu, hachi, kyuu)

    val hitotsu = vocab(2467, 1, "一つ", listOf("One Thing"), listOf("ひとつ"), listOf("numeral"), whitelist = listOf("1 Thing"))
    val futatsu = vocab(2468, 1, "二つ", listOf("Two Things"), listOf("ふたつ"), listOf("numeral"))
    val ookii = vocab(2470, 1, "大きい", listOf("Big", "Large"), listOf("おおきい"), listOf("い adjective"))
    val jinkou = vocab(2471, 1, "人口", listOf("Population"), listOf("じんこう"))
    val hitori = vocab(2472, 1, "一人", listOf("Alone", "One Person"), listOf("ひとり"))
    val otona = vocab(2473, 1, "大人", listOf("Adult"), listOf("おとな"))
    val yamaVocab = vocab(2474, 1, "山", listOf("Mountain"), listOf("やま"))
    val kakou = vocab(2475, 2, "下降", listOf("Descent"), listOf("かこう"))
    val kakouKatakana = vocab(2476, 3, "カコウ", listOf("Test Katakana Word"), listOf("カコウ"))
    val hiVocab = vocab(2477, 2, "火", listOf("Fire", "Flame"), listOf("ひ"))
    val kuchiVocab = vocab(2478, 1, "口", listOf("Mouth"), listOf("くち"))
    val ue2 = vocab(2479, 1, "上", listOf("Above", "Up", "Over"), listOf("うえ"))
    val arigatou = QuizSubject(9001, SubjectType.KANA_VOCABULARY, 3, "ありがとう", listOf("Thank You"), partsOfSpeech = listOf("expression"))
    val vocabulary = listOf(hitotsu, futatsu, ookii, jinkou, hitori, otona, yamaVocab, kakou, kakouKatakana, hiVocab, kuchiVocab, ue2, arigatou)

    val all = radicals + kanji + vocabulary
}
