package app.mittsu.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Mittsu palette, drawn from traditional Japanese colour names.
 *
 * Shu (vermilion) marks real WaniKani work. Ai (indigo) marks practice, so the two
 * modes can never be confused at a glance.
 */
object Washi {
    // Neutrals
    val Paper = Color(0xFFF7F2E8)      // washi paper
    val PaperDeep = Color(0xFFEDE5D6)
    val Card = Color(0xFFFFFCF6)
    val Ink = Color(0xFF15171C)        // sumi ink
    val InkSoft = Color(0xFF2A2D35)
    val InkCard = Color(0xFF1E2128)
    val InkLine = Color(0xFF353944)
    val Stone = Color(0xFF6E6A63)      // nezumi grey
    val StoneLight = Color(0xFFA39E94)
    val Line = Color(0xFFE0D7C6)

    // Accents
    val Shu = Color(0xFFC8442E)        // vermilion: real reviews
    val ShuBright = Color(0xFFE8664E)
    val ShuMist = Color(0xFFF6DDD5)
    val Ai = Color(0xFF2C4A7A)         // indigo: practice
    val AiBright = Color(0xFF7FA3DB)
    val AiMist = Color(0xFFDCE5F2)
    val Matcha = Color(0xFF5E7F45)     // correct
    val MatchaBright = Color(0xFF94BC74)
    val Kincha = Color(0xFFC0912F)     // golden tea: highlights
    val KinchaBright = Color(0xFFE2B85A)
    val Sakura = Color(0xFFE6B3B9)
    val Fuji = Color(0xFF8C6BA8)       // wisteria: holiday

    // SRS stage groups (original palette, not WaniKani's)
    val SrsApprentice = Color(0xFFD9775F)
    val SrsGuru = Color(0xFF8C6BA8)
    val SrsMaster = Color(0xFF2C4A7A)
    val SrsEnlightened = Color(0xFF3F8FA3)
    val SrsBurned = Color(0xFF4A4740)
}
