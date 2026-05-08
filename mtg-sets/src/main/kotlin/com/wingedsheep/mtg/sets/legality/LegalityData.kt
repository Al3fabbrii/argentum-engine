package com.wingedsheep.mtg.sets.legality

import com.wingedsheep.sdk.core.DeckFormat
import com.wingedsheep.sdk.model.CardDefinition
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Resolves [DeckFormat] legality for cards by name, sourced from the bundled
 * `legalities.json` resource. Populated by [SyncLegalities] hitting the Scryfall API.
 *
 * Cards not present in the resource (custom test cards, scenario-only entries) resolve to
 * an empty set — i.e. legal in no format. Callers should treat that as "format restrictions
 * cannot be enforced for this card" and let it through unless the format is set.
 */
object LegalityData {

    private val byName: Map<String, Set<DeckFormat>> by lazy { load() }

    /** Returns the formats in which the named card is currently legal. */
    fun forCard(name: String): Set<DeckFormat> = byName[name] ?: emptySet()

    /**
     * Returns a copy of [card] with [CardDefinition.legalFormats] populated from the resource.
     * Existing legalFormats on the card take precedence — the resource only fills the gap when
     * the field is empty (the default).
     */
    fun stamp(card: CardDefinition): CardDefinition =
        if (card.legalFormats.isNotEmpty()) card
        else card.copy(legalFormats = forCard(card.name))

    /** True iff the resource was loaded with at least one entry. */
    val isLoaded: Boolean get() = byName.isNotEmpty()

    private fun load(): Map<String, Set<DeckFormat>> {
        val stream = LegalityData::class.java.classLoader
            .getResourceAsStream("legalities.json") ?: return emptyMap()
        val raw: Map<String, List<String>> = stream.use { input ->
            val text = input.readBytes().toString(Charsets.UTF_8)
            Json { ignoreUnknownKeys = true }
                .decodeFromString(MapSerializer(String.serializer(), ListSerializer(String.serializer())), text)
        }
        return raw.mapValues { (_, names) ->
            // The sync script writes a sentinel for cards Scryfall returns 404 on (custom test cards,
            // tokens). It is not a real format — drop it. valueOf() also tolerates legacy entries.
            names.asSequence()
                .filter { it != "__NOT_ON_SCRYFALL__" }
                .mapNotNullTo(mutableSetOf()) { runCatching { DeckFormat.valueOf(it) }.getOrNull() }
        }
    }
}
