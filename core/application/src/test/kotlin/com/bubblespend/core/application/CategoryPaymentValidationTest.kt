package com.bubblespend.core.application

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CategoryPaymentValidationTest {
    @Test
    fun rejectsInvalidNormalizedNames() {
        listOf("", "   ", "x".repeat(41)).forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { NameValidation.validate(name) }
        }
        assertEquals("Food Shop", NameValidation.validate("  Food Shop  ").displayName)
        assertEquals(
            NameValidation.validate("food   SHOP").uniquenessKey,
            NameValidation.validate(" Food shop ").uniquenessKey,
        )
    }

    @Test
    fun preservesTrimmedDisplayWhitespaceAndValidatesLengthBeforeUniquenessCollapse() {
        val name = NameValidation.validate("  Food   Shop  ")
        assertEquals("Food   Shop", name.displayName)
        assertEquals("food shop", name.uniquenessKey)

        val fortyCharacters = "A" + " ".repeat(38) + "B"
        assertEquals(fortyCharacters, NameValidation.validate(fortyCharacters).displayName)
        assertThrows(IllegalArgumentException::class.java) {
            NameValidation.validate("A" + " ".repeat(39) + "B")
        }
    }

    @Test
    fun rejectsUnsupportedTokens() {
        CategoryVisualValidation.validate("food", "red")
        assertThrows(IllegalArgumentException::class.java) {
            CategoryVisualValidation.validate("missing", "red")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CategoryVisualValidation.validate("food", "invisible")
        }
    }
}
