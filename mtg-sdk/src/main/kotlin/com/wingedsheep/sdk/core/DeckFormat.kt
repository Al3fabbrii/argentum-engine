package com.wingedsheep.sdk.core

import kotlinx.serialization.Serializable

/**
 * Constructed deck-construction formats whose legality data is sourced from Scryfall.
 *
 * A card's [com.wingedsheep.sdk.model.CardDefinition.legalFormats] is the set of formats in
 * which the card is currently legal (i.e. not banned and printed in a legal set). Format-
 * specific deck-construction rules beyond per-card legality (Commander singleton/100, Vintage
 * restricted-list, etc.) are enforced separately by the deck validator.
 */
@Serializable
enum class DeckFormat {
    STANDARD,
    PIONEER,
    MODERN,
    LEGACY,
    VINTAGE,
    COMMANDER,
    PAUPER,
    PREMODERN;

    /** Lower-case identifier matching Scryfall's `legalities.<key>` field. */
    val scryfallKey: String get() = name.lowercase()

    /** Human-readable label for UI surfaces. */
    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        fun fromScryfallKey(key: String): DeckFormat? =
            entries.firstOrNull { it.scryfallKey == key.lowercase() }
    }
}
