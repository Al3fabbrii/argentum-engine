package com.wingedsheep.gameserver.deck

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.core.DeckFormat
import com.wingedsheep.sdk.model.CardDefinition
import org.springframework.stereotype.Component

/**
 * Validates a deck list against the card registry and constructed-format rules.
 *
 * The format-agnostic baseline is: every card resolves, no banned/illegal cards, ≥ [DEFAULT_MIN_SIZE]
 * cards, ≤ 4 copies of any non-basic. Pass a [DeckFormat] to layer in format-specific rules:
 *
 *   - **Commander** (CR 903.5b): exact 100-card deck, singleton (max 1 of each non-basic). Cards
 *     whose oracle text contains "A deck can have any number of cards named X" override the
 *     singleton rule; cards saying "A deck can have up to N cards named X" cap at N (so e.g.
 *     Seven Dwarves caps at 7, Nazgûl at 9).
 *   - **Brawl**: 100-card singleton, same shape as Commander (Scryfall key `brawl`). The "any
 *     number of cards named X" oracle override applies here too, keeping Relentless Rats /
 *     Persistent Petitioners decks legal.
 *   - **Standard Brawl**: 60-card singleton restricted to the Standard pool (Scryfall key
 *     `standardbrawl`). Identical singleton + override rules as Brawl, just smaller.
 *   - **Standard / Pioneer / Modern / Pauper / Legacy / Vintage / Premodern**: 60-card minimum,
 *     4-of (current default). Format-specific banned-list enforcement comes from the per-card
 *     legality data Scryfall publishes — `card.legalFormats` already encodes "is currently legal
 *     in this format", so a banned card simply isn't in the set.
 *
 * Anything blocking (unknown card, count violation, illegal-in-format) becomes an error and
 * flips `valid = false`. Warnings are reserved for "unusual but allowed" (currently unused).
 */
@Component
class DeckValidator(
    private val cardRegistry: CardRegistry
) {

    fun validate(
        deckList: Map<String, Int>,
        format: DeckFormat? = null,
    ): DeckValidationResult {
        val errors = mutableListOf<DeckValidationIssue>()
        val warnings = mutableListOf<DeckValidationIssue>()

        // Filter out zero/negative counts up front; treat them as if they weren't submitted.
        val sanitized = deckList.filterValues { it > 0 }
        val totalCards = sanitized.values.sum()
        val profile = profileFor(format)

        // Group entries by their *base* card name so collector-number variants stack toward the copies cap.
        val countsByBaseName = mutableMapOf<String, Int>()
        for ((entry, count) in sanitized) {
            val card = cardRegistry.getCard(entry)
            if (card == null) {
                errors += DeckValidationIssue(
                    code = "UNKNOWN_CARD",
                    message = "Unknown card: \"$entry\"",
                    cardName = entry
                )
                continue
            }
            countsByBaseName.merge(card.name, count, Int::plus)
        }

        for ((cardName, count) in countsByBaseName) {
            val card = cardRegistry.getCard(cardName) ?: continue
            val limit = copyLimitFor(card, profile)
            if (limit != null && count > limit) {
                errors += DeckValidationIssue(
                    code = "TOO_MANY_COPIES",
                    message = copyLimitMessage(cardName, count, limit, profile),
                    cardName = cardName
                )
            }
            // Format legality. Cards with no recorded legality (custom test cards, missing Scryfall
            // data) are accepted silently — only an explicit "this card is not in $format" rejects.
            if (format != null && card.legalFormats.isNotEmpty() && format !in card.legalFormats) {
                errors += DeckValidationIssue(
                    code = "NOT_LEGAL_IN_FORMAT",
                    message = "$cardName is not legal in ${format.displayName}",
                    cardName = cardName
                )
            }
        }

        when {
            profile.exactSize != null && totalCards != profile.exactSize -> {
                errors += DeckValidationIssue(
                    code = if (totalCards < profile.exactSize) "TOO_FEW_CARDS" else "TOO_MANY_CARDS",
                    message = "${profile.formatLabel ?: "Deck"} requires exactly ${profile.exactSize} cards (have $totalCards)",
                    cardName = null
                )
            }
            totalCards < profile.minSize -> {
                errors += DeckValidationIssue(
                    code = "TOO_FEW_CARDS",
                    message = "Deck has $totalCards cards — minimum is ${profile.minSize}",
                    cardName = null
                )
            }
        }

        return DeckValidationResult(
            valid = errors.isEmpty(),
            totalCards = totalCards,
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Returns the maximum number of copies of [card] this format allows. `null` means
     * "unlimited" (basic lands, plus Commander cards saying "any number"). The default per-format
     * cap kicks in otherwise.
     */
    private fun copyLimitFor(card: CardDefinition, profile: FormatProfile): Int? {
        if (card.typeLine.isBasicLand) return null
        // Per-card override from oracle text — "A deck can have any number / up to N cards named X".
        // Applies in every format, but is most relevant under Commander's singleton rule.
        val override = parseDeckSizeOverride(card.oracleText, card.name)
        if (override != null) return override.cap
        return profile.maxCopiesNonBasic
    }

    private fun copyLimitMessage(cardName: String, count: Int, limit: Int, profile: FormatProfile): String {
        val basis = profile.formatLabel?.let { " ($it)" } ?: ""
        return when (limit) {
            1 -> "$count copies of $cardName — singleton allows only 1$basis"
            else -> "$count copies of $cardName — limit is $limit for non-basic cards$basis"
        }
    }

    private data class FormatProfile(
        val minSize: Int,
        val exactSize: Int? = null,
        val maxCopiesNonBasic: Int,
        val formatLabel: String? = null,
    )

    private fun profileFor(format: DeckFormat?): FormatProfile = when (format) {
        DeckFormat.COMMANDER, DeckFormat.BRAWL -> FormatProfile(
            // 100-card singleton. Commander has additional structural rules (color-identity,
            // partner pairs, designated commander) that the deck submission surface here can't
            // enforce — those still need to be checked at game-start time.
            minSize = SINGLETON_HUNDRED_DECK_SIZE,
            exactSize = SINGLETON_HUNDRED_DECK_SIZE,
            maxCopiesNonBasic = 1,
            formatLabel = format.displayName,
        )
        DeckFormat.STANDARD_BRAWL -> FormatProfile(
            // 60-card singleton, Standard pool. Per-card legality is sourced from Scryfall's
            // `standardbrawl` legality field; the construction shape is enforced here.
            minSize = STANDARD_BRAWL_DECK_SIZE,
            exactSize = STANDARD_BRAWL_DECK_SIZE,
            maxCopiesNonBasic = 1,
            formatLabel = format.displayName,
        )
        else -> FormatProfile(
            minSize = DEFAULT_MIN_SIZE,
            maxCopiesNonBasic = DEFAULT_MAX_COPIES_NON_BASIC,
            formatLabel = format?.displayName,
        )
    }

    companion object {
        // Default constructed minimum for every non-Commander format. Pre-format submissions (no
        // format specified) also use this so the picker stays permissive when a player just
        // wants to mash 60 cards together for a casual game.
        const val DEFAULT_MIN_SIZE = 40
        const val DEFAULT_MAX_COPIES_NON_BASIC = 4
        // Shared by Commander (CR 903.5b) and Brawl (also a 100-card singleton format). Named
        // generically so a future 100-card singleton variant can reuse it without renaming.
        const val SINGLETON_HUNDRED_DECK_SIZE = 100
        const val STANDARD_BRAWL_DECK_SIZE = 60

        // "A deck can have any number of cards named <Name>" → unlimited.
        // "A deck can have up to <N> cards named <Name>"     → cap at N.
        // Both phrasings are matched case-insensitively against the card's own name to defend
        // against errata/typos in registered oracle text.
        private val ANY_NUMBER_RE = Regex(
            """A deck can have any number of cards named ([^.]+)\.""",
            RegexOption.IGNORE_CASE,
        )
        private val UP_TO_RE = Regex(
            """A deck can have up to (one|two|three|four|five|six|seven|eight|nine|ten|\d+) cards named ([^.]+)\.""",
            RegexOption.IGNORE_CASE,
        )

        /**
         * Detects a per-card deck-size override in [oracleText]. Returns null if no override is
         * declared, otherwise an [OverrideRule] with the cap (Int.MAX_VALUE = unlimited) and the
         * card name the rule references. Callers should still confirm the rule names *this* card
         * — it's not an override if the text mentions a different card.
         */
        fun parseDeckSizeOverride(oracleText: String, cardName: String): OverrideRule? {
            if (oracleText.isBlank()) return null
            ANY_NUMBER_RE.find(oracleText)?.let { match ->
                val named = match.groupValues[1].trim()
                if (named.equals(cardName, ignoreCase = true)) {
                    return OverrideRule(cap = Int.MAX_VALUE, named = named)
                }
            }
            UP_TO_RE.find(oracleText)?.let { match ->
                val word = match.groupValues[1].trim()
                val named = match.groupValues[2].trim()
                if (named.equals(cardName, ignoreCase = true)) {
                    val cap = wordToInt(word) ?: return null
                    return OverrideRule(cap = cap, named = named)
                }
            }
            return null
        }

        data class OverrideRule(val cap: Int, val named: String)

        private fun wordToInt(token: String): Int? = when (token.lowercase()) {
            "one" -> 1; "two" -> 2; "three" -> 3; "four" -> 4; "five" -> 5
            "six" -> 6; "seven" -> 7; "eight" -> 8; "nine" -> 9; "ten" -> 10
            else -> token.toIntOrNull()
        }
    }
}

data class DeckValidationResult(
    val valid: Boolean,
    val totalCards: Int,
    val errors: List<DeckValidationIssue>,
    val warnings: List<DeckValidationIssue>
)

data class DeckValidationIssue(
    val code: String,
    val message: String,
    val cardName: String?
)
