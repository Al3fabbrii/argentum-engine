package com.wingedsheep.gameserver.deck

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.core.DeckFormat
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain as shouldContainString

class DeckValidatorTest : FunSpec({

    val registry = CardRegistry().apply {
        register(PortalSet.cards)
        register(PortalSet.basicLands)
    }
    val validator = DeckValidator(registry)

    test("empty deck reports too-few-cards error") {
        val result = validator.validate(emptyMap())
        result.valid shouldBe false
        result.totalCards shouldBe 0
        result.errors.map { it.code } shouldContain "TOO_FEW_CARDS"
    }

    test("legal 40-card mono-mountain deck validates") {
        val deck = mapOf("Mountain" to 40)
        val result = validator.validate(deck)
        result.valid shouldBe true
        result.totalCards shouldBe 40
        result.errors.shouldBeEmpty()
    }

    test("unknown card name produces UNKNOWN_CARD error") {
        val deck = mapOf("Not A Real Card" to 4, "Mountain" to 36)
        val result = validator.validate(deck)
        result.valid shouldBe false
        val unknown = result.errors.single { it.code == "UNKNOWN_CARD" }
        unknown.cardName shouldBe "Not A Real Card"
    }

    test("five copies of a non-basic produces TOO_MANY_COPIES") {
        val deck = mapOf("Bog Imp" to 5, "Mountain" to 35)
        val result = validator.validate(deck)
        result.valid shouldBe false
        val tooMany = result.errors.single { it.code == "TOO_MANY_COPIES" }
        tooMany.cardName shouldBe "Bog Imp"
    }

    test("twenty copies of a basic land is fine") {
        val deck = mapOf("Mountain" to 20, "Forest" to 20)
        val result = validator.validate(deck)
        result.valid shouldBe true
    }

    test("collector-number variants of basics stack toward the same name and stay legal") {
        // Two Plains variants both summing to >4 must NOT trigger TOO_MANY_COPIES because Plains is basic.
        val plainsVariants = registry.getCardsByName("Plains")
        if (plainsVariants.size >= 2) {
            val v1 = plainsVariants[0].metadata.collectorNumber!!
            val v2 = plainsVariants[1].metadata.collectorNumber!!
            val setCode = plainsVariants[0].setCode
            val key1 = if (setCode != null) "Plains#$setCode-$v1" else "Plains#$v1"
            val key2 = if (setCode != null) "Plains#$setCode-$v2" else "Plains#$v2"
            val deck = mapOf(key1 to 20, key2 to 20)
            val result = validator.validate(deck)
            result.valid shouldBe true
        }
    }

    test("zero/negative entries are ignored, not counted toward total") {
        val deck = mapOf("Mountain" to 40, "Forest" to 0)
        val result = validator.validate(deck)
        result.totalCards shouldBe 40
        result.valid shouldBe true
    }

    test("under-40 deck is flagged") {
        val deck = mapOf("Mountain" to 30)
        val result = validator.validate(deck)
        result.valid shouldBe false
        result.errors.map { it.code } shouldContain "TOO_FEW_CARDS"
    }

    // ---------------------------------------------------------------------------
    // Commander format
    // ---------------------------------------------------------------------------

    test("Commander rejects a deck that isn't exactly 100 cards") {
        // 99 mountains: legal in any other format, illegal under Commander's exact-100 rule.
        val deck = mapOf("Mountain" to 99)
        val result = validator.validate(deck, DeckFormat.COMMANDER)
        result.valid shouldBe false
        result.errors.map { it.code } shouldContain "TOO_FEW_CARDS"
    }

    test("Commander accepts an all-basics 100-card deck") {
        // Commander is a singleton format but basics override the cap, so 100 mountains is legal
        // (ignoring legality data for this test card pool).
        val deck = mapOf("Mountain" to 100)
        val result = validator.validate(deck, DeckFormat.COMMANDER)
        // The Portal test pool doesn't tag legalFormats so format-legality errors don't appear;
        // the only thing this test asserts is "no copy-cap or size error".
        result.errors.none { it.code == "TOO_MANY_COPIES" } shouldBe true
        result.errors.none { it.code == "TOO_FEW_CARDS" || it.code == "TOO_MANY_CARDS" } shouldBe true
    }

    test("Commander rejects two copies of a non-basic non-override card") {
        val deck = mapOf("Bog Imp" to 2, "Mountain" to 98)
        val result = validator.validate(deck, DeckFormat.COMMANDER)
        result.valid shouldBe false
        val tooMany = result.errors.single { it.code == "TOO_MANY_COPIES" }
        tooMany.cardName shouldBe "Bog Imp"
        // Singleton message is more informative than the raw 4-of variant.
        tooMany.message shouldContainString "singleton"
    }

    test("non-Commander formats keep the 4-of cap, not singleton") {
        val deck = mapOf("Bog Imp" to 4, "Mountain" to 56)
        val result = validator.validate(deck, DeckFormat.MODERN)
        // Modern accepts 4-of so no copy-cap error fires here, even though Commander would reject.
        result.errors.none { it.code == "TOO_MANY_COPIES" } shouldBe true
    }

    // ---------------------------------------------------------------------------
    // Per-card "any number" override (parser-level)
    // ---------------------------------------------------------------------------

    test("parser detects 'any number of cards named' override") {
        val rule = DeckValidator.parseDeckSizeOverride(
            oracleText = "A deck can have any number of cards named Relentless Rats.",
            cardName = "Relentless Rats"
        )
        rule shouldBe DeckValidator.Companion.OverrideRule(cap = Int.MAX_VALUE, named = "Relentless Rats")
    }

    test("parser detects 'up to seven cards named' override and parses the word") {
        val rule = DeckValidator.parseDeckSizeOverride(
            oracleText = "A deck can have up to seven cards named Seven Dwarves.",
            cardName = "Seven Dwarves"
        )
        rule shouldBe DeckValidator.Companion.OverrideRule(cap = 7, named = "Seven Dwarves")
    }

    test("parser ignores override that names a different card") {
        val rule = DeckValidator.parseDeckSizeOverride(
            oracleText = "A deck can have any number of cards named Persistent Petitioners.",
            cardName = "Bog Imp"
        )
        rule shouldBe null
    }
})
