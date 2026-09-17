package com.matheo.flashcardcompanion

import com.matheo.flashcardcompanion.ui.plainPreview
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The stats list shows card text as plain prose. Real cards are dense with
 * LaTeX, and printing the delimiters raw was the bug this guards against.
 */
class PlainPreviewTest {

    @Test
    fun `strips inline maths delimiters and commands`() {
        assertEquals(
            "State the definition of a topology on a set X.",
            plainPreview("State the definition of a topology \\(\\tau\\) on a set \\(X\\).", 200),
        )
    }

    @Test
    fun `strips dollar delimiters`() {
        assertEquals(
            "What conditions on the price vector p R^n make it compact?",
            plainPreview(
                "What conditions on the price vector \$p\\in\\mathbb{R}^n\$ make it compact?",
                200,
            ),
        )
    }

    @Test
    fun `unwraps the common wrapper commands rather than dropping their content`() {
        assertEquals("the set R and the class C", plainPreview("the set \\mathbb{R} and the class \\mathcal{C}", 200))
    }

    @Test
    fun `strips markup the pipeline may carry`() {
        assertEquals("first second", plainPreview("first<br> second", 200))
    }

    @Test
    fun `truncates with an ellipsis`() {
        assertEquals("abcde…", plainPreview("abcdefghij", 5))
    }

    @Test
    fun `collapses the whitespace left behind`() {
        assertEquals("a b", plainPreview("a    \n  b", 200))
    }
}
