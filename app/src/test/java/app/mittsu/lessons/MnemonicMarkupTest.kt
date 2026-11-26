package app.mittsu.lessons

import app.mittsu.lessons.MnemonicMarkup.Run
import app.mittsu.lessons.MnemonicMarkup.Tag
import org.junit.Assert.assertEquals
import org.junit.Test

class MnemonicMarkupTest {
    @Test fun splitsTaggedRuns() {
        assertEquals(
            listOf(Run("The ", null), Run("ground", Tag.RADICAL), Run(" says ", null), Run("いち", Tag.READING), Run(".", null)),
            MnemonicMarkup.parse("The <radical>ground</radical> says <reading>いち</reading>."),
        )
    }

    @Test fun unknownTagsAreDroppedAndEntitiesDecoded() {
        assertEquals(listOf(Run("a & b <c>", null)), MnemonicMarkup.parse("<span class=\"x\">a &amp; b &lt;c&gt;</span>"))
    }

    @Test fun nestedTagsKeepTheInnermostKnownTag() {
        assertEquals(listOf(Run("big", Tag.KANJI), Run("!", Tag.EMPHASIS)), MnemonicMarkup.parse("<b><kanji>big</kanji>!</b>"))
    }

    @Test fun plainTextIsUntouched() {
        assertEquals(listOf(Run("Just text", null)), MnemonicMarkup.parse("Just text"))
        assertEquals(emptyList<Run>(), MnemonicMarkup.parse(""))
    }
}
