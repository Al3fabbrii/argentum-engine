package com.wingedsheep.sdk.model

import kotlinx.serialization.Serializable

/**
 * Represents a deck of Magic cards for game initialization.
 *
 * A deck is a list of card definition IDs (typically card names) that will be
 * instantiated into entity IDs when the game starts.
 *
 * ## Usage
 * ```kotlin
 * val deck = Deck(
 *     cards = listOf("Mountain", "Mountain", "Lightning Bolt", "Goblin Guide", ...)
 * )
 * ```
 *
 * The engine uses the [CardDefinition.name] to look up card definitions from
 * registered card sets.
 */
@Serializable
data class Deck(
    /**
     * Card names in the main deck (the library at game start).
     * Duplicates are allowed (e.g., 4x Lightning Bolt). Does NOT include the commander —
     * for Commander/Brawl decks the commander begins in the command zone (CR 903.6a),
     * not in the library.
     */
    val cards: List<String>,
    /**
     * Optional commander card name. Set for Commander/Brawl/Standard Brawl decks; null
     * otherwise. The commander is stored separately because it begins the game in the
     * command zone, not the library.
     */
    val commander: String? = null,
) {
    /**
     * Total number of cards in the deck (library + command zone).
     */
    val size: Int get() = cards.size + (if (commander != null) 1 else 0)

    /**
     * Check if the deck is empty.
     */
    val isEmpty: Boolean get() = cards.isEmpty() && commander == null

    /**
     * Count occurrences of a specific card in the main deck (library).
     * Does not include the commander — use [size] or check [commander] directly.
     */
    fun countOf(cardName: String): Int = cards.count { it == cardName }

    /**
     * Get unique card names in the deck (main deck + commander, if any).
     */
    fun uniqueCards(): Set<String> = cards.toSet() + listOfNotNull(commander)

    companion object {
        /**
         * Create an empty deck.
         */
        val EMPTY = Deck(emptyList())

        /**
         * Create a deck from card name/count pairs.
         */
        fun of(vararg entries: Pair<String, Int>): Deck {
            val cards = entries.flatMap { (name, count) ->
                List(count) { name }
            }
            return Deck(cards)
        }

        /**
         * Create a simple test deck with basic lands and vanilla creatures.
         */
        fun testDeck(landName: String, landCount: Int, creatures: List<Pair<String, Int>>): Deck {
            val cards = mutableListOf<String>()
            repeat(landCount) { cards.add(landName) }
            creatures.forEach { (name, count) ->
                repeat(count) { cards.add(name) }
            }
            return Deck(cards)
        }
    }
}
