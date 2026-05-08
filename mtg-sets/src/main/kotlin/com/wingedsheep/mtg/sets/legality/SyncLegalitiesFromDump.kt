package com.wingedsheep.mtg.sets.legality

import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.DeckFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import java.nio.file.Files
import java.nio.file.Paths
import java.util.SortedMap

/**
 * Offline sibling of [SyncLegalities] that reads a Scryfall bulk-data dump (the "All Cards" or
 * "Default Cards" JSON download) instead of hitting the live API. Useful for full refreshes
 * without rate-limit headaches.
 *
 * Run: `./gradlew :mtg-sets:syncLegalityFromDump --args="/path/to/all-cards-YYYYMMDDhhmmss.json"`.
 *
 * For each unique card name in [MtgSetCatalog] we union the legality entries across every
 * matching printing in the dump. We can't just take the first printing — tokens, art series,
 * and playtest reprints share the same name as real cards but carry empty/`not_legal`
 * legalities, so picking one arbitrarily corrupts the result. The union over all printings
 * with identical names is safe because Scryfall's per-format legality is a property of the
 * card (oracle id), not of any individual printing.
 *
 * Names not present in the dump are stamped with [DUMP_NOT_FOUND_MARKER] (same convention
 * [LegalityData] already filters out at load time).
 *
 * The dump is a top-level JSON array, decoded as a streamed sequence so we never hold the
 * full 2GB+ in memory.
 */
private const val DUMP_NOT_FOUND_MARKER = "__NOT_ON_SCRYFALL__"
private val dumpMapSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

@OptIn(ExperimentalSerializationApi::class)
fun main(args: Array<String>) {
    val dumpArg = args.firstOrNull()
        ?: error("Usage: syncLegalityFromDump <path-to-scryfall-bulk-cards.json>")
    val dumpPath = Paths.get(dumpArg)
    require(Files.exists(dumpPath)) { "Dump not found: $dumpPath" }

    val wantedNames = MtgSetCatalog.all
        .flatMap { set -> set.cards + set.basicLands + (set.basicLandsFallback?.basicLands ?: emptyList()) }
        .map { it.name }
        .toSortedSet()
    println("Scanning $dumpPath for ${wantedNames.size} unique card names...")

    val resolved = sortedMapOf<String, MutableSet<DeckFormat>>()
    val parser = Json { ignoreUnknownKeys = true; isLenient = false }

    var scanned = 0L
    Files.newInputStream(dumpPath).use { stream ->
        val cards = parser.decodeToSequence(stream, ScryfallDumpCard.serializer())
        for (card in cards) {
            scanned++
            if (scanned % 200_000 == 0L) {
                println("  scanned $scanned printings (matched ${resolved.size}/${wantedNames.size})")
            }
            val name = card.name ?: continue
            // Split / DFC / adventure cards in the dump use "Front // Back". Our card definitions
            // use the front-face name only, so try the front face as a fallback match.
            val frontFace = name.substringBefore(" // ")
            val matched = when {
                name in wantedNames -> name
                frontFace != name && frontFace in wantedNames -> frontFace
                else -> continue
            }
            val bucket = resolved.getOrPut(matched) { mutableSetOf() }
            for (format in DeckFormat.entries) {
                if (card.legalities[format.scryfallKey] == "legal") bucket += format
            }
        }
    }
    println("Finished scanning $scanned printings. Resolved ${resolved.size}/${wantedNames.size}.")

    val output: SortedMap<String, List<String>> = sortedMapOf()
    for ((name, formats) in resolved) {
        output[name] = formats.sortedBy { it.ordinal }.map { it.name }
    }
    val missing = wantedNames - resolved.keys
    for (name in missing) {
        output[name] = listOf(DUMP_NOT_FOUND_MARKER)
    }

    val outPath = Paths.get("mtg-sets/src/main/resources/legalities.json")
    Files.createDirectories(outPath.parent)
    val serialized = Json { prettyPrint = true; prettyPrintIndent = "  " }
        .encodeToString(dumpMapSerializer, output)
    Files.writeString(outPath, serialized)
    println("Wrote ${output.size} entries to $outPath (${missing.size} marked not-found).")
    if (missing.isNotEmpty()) {
        println("Sample missing names: ${missing.take(10).joinToString(", ")}")
    }
}

@Serializable
private data class ScryfallDumpCard(
    val name: String? = null,
    val legalities: Map<String, String> = emptyMap(),
)
