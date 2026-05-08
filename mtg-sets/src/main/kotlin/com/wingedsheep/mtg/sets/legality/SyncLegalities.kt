package com.wingedsheep.mtg.sets.legality

import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.DeckFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration

/**
 * One-shot script: walks every unique card name in [MtgSetCatalog], queries Scryfall for its
 * format legalities, and writes the result to `mtg-sets/src/main/resources/legalities.json`.
 *
 * Run via `./gradlew :mtg-sets:syncLegality` (preferred) — the working directory is the repo root
 * so the output path resolves correctly. The output is keyed by card name so the engine can join
 * it onto registered cards regardless of set.
 *
 * Output shape:
 * ```
 * { "Lightning Bolt": ["MODERN", "LEGACY", "VINTAGE", "PAUPER", "PREMODERN"], ... }
 * ```
 *
 * Resume: re-running picks up where the previous run left off. An entry already present in the
 * output file is only re-fetched if it has an empty legalities list (i.e. the previous attempt
 * failed). Cards Scryfall returns 404 for are stamped with [SCRYFALL_NOT_FOUND_MARKER] so they're
 * skipped on resume.
 *
 * Rate limit: Scryfall recommends 50–100 ms between requests; bursts trigger HTTP 429. We pace at
 * 200 ms baseline and back off exponentially on 429s.
 */
private const val BASELINE_DELAY_MS = 200L
private const val MAX_RETRIES_ON_429 = 5
private const val SCRYFALL_NOT_FOUND_MARKER = "__NOT_ON_SCRYFALL__"

private val parser = Json { ignoreUnknownKeys = true }
private val mapSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

fun main() {
    val cardNames = MtgSetCatalog.all
        .flatMap { set -> set.cards + set.basicLands + (set.basicLandsFallback?.basicLands ?: emptyList()) }
        .map { it.name }
        .distinct()
        .sorted()

    val outPath = Paths.get("mtg-sets/src/main/resources/legalities.json")
    Files.createDirectories(outPath.parent)
    val existing = loadExisting(outPath)
    val results = sortedMapOf<String, List<String>>().apply { putAll(existing) }

    val toFetch = cardNames.filter { name ->
        val prior = existing[name]
        // Re-fetch if not present, OR present-but-empty (previous attempt failed and is worth retrying).
        // The "not found on Scryfall" sentinel keeps us from spamming 404s on resume.
        prior == null || prior.isEmpty()
    }
    val skipped = cardNames.size - toFetch.size
    println("Syncing legality: ${cardNames.size} unique cards (${skipped} already populated, ${toFetch.size} to fetch).")

    if (toFetch.isEmpty()) {
        println("Nothing to do.")
        return
    }

    val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    var failures = 0
    var i = 0
    for (name in toFetch) {
        i++
        val outcome = fetchWithBackoff(client, name)
        when (outcome) {
            is FetchResult.Ok -> results[name] = outcome.formats
            FetchResult.NotFound -> {
                results[name] = listOf(SCRYFALL_NOT_FOUND_MARKER)
                println("[$i/${toFetch.size}] $name → not on Scryfall (marker stored)")
            }
            FetchResult.Failed -> {
                results[name] = emptyList()
                failures++
                println("[$i/${toFetch.size}] $name → giving up (will retry on next run)")
            }
        }
        if (i % 50 == 0) {
            println("[$i/${toFetch.size}] processed; flushing to disk")
            writeOutput(outPath, results)
        }
        Thread.sleep(BASELINE_DELAY_MS)
    }

    writeOutput(outPath, results)
    println("Wrote ${results.size} entries to $outPath (${failures} hard failures this run)")
}

private fun loadExisting(path: java.nio.file.Path): Map<String, List<String>> {
    if (!Files.exists(path)) return emptyMap()
    return try {
        val text = Files.readString(path)
        parser.decodeFromString(mapSerializer, text)
    } catch (e: Exception) {
        println("Failed to parse existing $path (${e.message}); starting fresh")
        emptyMap()
    }
}

private fun writeOutput(path: java.nio.file.Path, results: Map<String, List<String>>) {
    val output = Json { prettyPrint = true; prettyPrintIndent = "  " }
        .encodeToString(mapSerializer, results)
    Files.writeString(path, output)
}

private sealed interface FetchResult {
    data class Ok(val formats: List<String>) : FetchResult
    data object NotFound : FetchResult
    data object Failed : FetchResult
}

private fun fetchWithBackoff(client: HttpClient, name: String): FetchResult {
    val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8)
    val url = "https://api.scryfall.com/cards/named?exact=$encoded"
    val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("User-Agent", "argentum-engine-legality-sync/1.0")
        .header("Accept", "application/json")
        .GET()
        .timeout(Duration.ofSeconds(15))
        .build()

    var attempt = 0
    var backoffMs = 2000L
    while (attempt <= MAX_RETRIES_ON_429) {
        try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            when (response.statusCode()) {
                200 -> {
                    val card = parser.decodeFromString<ScryfallCard>(response.body())
                    val legal = DeckFormat.entries
                        .filter { card.legalities[it.scryfallKey] == "legal" }
                        .map { it.name }
                    return FetchResult.Ok(legal)
                }
                404 -> return FetchResult.NotFound
                429 -> {
                    println("  $name: 429 rate-limited, backing off ${backoffMs}ms (attempt ${attempt + 1}/${MAX_RETRIES_ON_429 + 1})")
                    Thread.sleep(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(60_000)
                }
                else -> {
                    println("  $name: HTTP ${response.statusCode()}")
                    return FetchResult.Failed
                }
            }
        } catch (e: Exception) {
            println("  $name: ${e.message}")
            return FetchResult.Failed
        }
        attempt++
    }
    return FetchResult.Failed
}

@Serializable
private data class ScryfallCard(
    val legalities: Map<String, String> = emptyMap(),
)
