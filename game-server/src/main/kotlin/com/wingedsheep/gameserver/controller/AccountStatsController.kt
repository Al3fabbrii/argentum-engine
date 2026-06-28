package com.wingedsheep.gameserver.controller

import com.wingedsheep.gameserver.auth.AuthSupport
import com.wingedsheep.gameserver.persistence.MatchResultRepository
import com.wingedsheep.gameserver.persistence.RatingHistoryRepository
import com.wingedsheep.gameserver.persistence.UserRatingRepository
import com.wingedsheep.gameserver.ranking.Elo
import com.wingedsheep.gameserver.ranking.RankedMode
import com.wingedsheep.gameserver.ranking.RatingTier
import com.wingedsheep.gameserver.stats.CardStat
import com.wingedsheep.gameserver.stats.GameDecks
import com.wingedsheep.gameserver.stats.GameHistoryEntry
import com.wingedsheep.gameserver.stats.HeadToHead
import com.wingedsheep.gameserver.stats.StatBucket
import com.wingedsheep.gameserver.stats.StatsQueryService
import com.wingedsheep.gameserver.stats.UserTournamentEntry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Stats for the signed-in user, computed on demand from the match-history tables (no denormalized
 * stats table). Lives under /api/stats alongside the admin dashboard stats. Only mounted when
 * accounts are enabled.
 */
@RestController
@RequestMapping("/api/stats")
@ConditionalOnProperty(name = ["accounts.enabled"], havingValue = "true")
class AccountStatsController(
    private val matchResults: MatchResultRepository,
    private val statsQuery: StatsQueryService,
    private val userRatings: UserRatingRepository,
    private val ratingHistory: RatingHistoryRepository,
    private val authSupport: AuthSupport,
) {
    data class StatsDto(val games: Long, val wins: Long, val losses: Long, val winRate: Double)

    /** Current ranked standing in one mode. Unrated modes report the starting rating / Provisional. */
    data class RatingDto(
        val mode: String,
        val rating: Int,
        val tier: String,
        val provisional: Boolean,
        val gamesPlayed: Int,
        val wins: Int,
        val losses: Int,
        val draws: Int,
        val peakRating: Int,
    )

    /** One point on the rating-over-time chart. */
    data class RatingPointDto(
        val mode: String,
        val endedAt: String,
        val ratingAfter: Int,
        val delta: Int,
        val result: String,
    )

    /** All three ranked queues for the signed-in user, unplayed ones shown at the starting rating. */
    @GetMapping("/me/ratings")
    fun ratings(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?): List<RatingDto> {
        val userId = authSupport.requireUser(auth).userId
        val byMode = userRatings.findByUserId(userId).associateBy { it.mode }
        return RankedMode.entries.map { mode ->
            val row = byMode[mode.name]
            val rating = row?.rating ?: Elo.STARTING_RATING
            val games = row?.gamesPlayed ?: 0
            val tier = Elo.tier(rating, games)
            RatingDto(
                mode = mode.name,
                rating = Math.round(rating).toInt(),
                tier = tier.displayName,
                provisional = tier == RatingTier.PROVISIONAL,
                gamesPlayed = games,
                wins = row?.wins ?: 0,
                losses = row?.losses ?: 0,
                draws = row?.draws ?: 0,
                peakRating = Math.round(row?.peakRating ?: Elo.STARTING_RATING).toInt(),
            )
        }
    }

    /**
     * Rating-over-time points for the chart. Without [mode], returns every mode's history (the client
     * draws one line per mode); with [mode], just that queue. Oldest first.
     */
    @GetMapping("/me/ratings/history")
    fun ratingsHistory(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @RequestParam(required = false) mode: String?,
    ): List<RatingPointDto> {
        val userId = authSupport.requireUser(auth).userId
        val modes = mode
            ?.let { listOfNotNull(runCatching { RankedMode.valueOf(it.uppercase()) }.getOrNull()) }
            ?: RankedMode.entries
        return modes.flatMap { m ->
            ratingHistory.findByUserIdAndModeOrderByCreatedAtAsc(userId, m.name).map { row ->
                RatingPointDto(
                    mode = m.name,
                    endedAt = row.createdAt.toString(),
                    ratingAfter = Math.round(row.ratingAfter).toInt(),
                    delta = Math.round(row.delta).toInt(),
                    result = row.result,
                )
            }
        }
    }

    @GetMapping("/me")
    fun me(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?): StatsDto {
        val userId = authSupport.requireUser(auth).userId
        val games = matchResults.countGamesForUser(userId)
        val wins = matchResults.countWinsForUser(userId)
        return StatsDto(
            games = games,
            wins = wins,
            losses = games - wins,
            winRate = if (games > 0) wins.toDouble() / games else 0.0,
        )
    }

    @GetMapping("/me/colors")
    fun colors(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?): List<StatBucket> =
        statsQuery.colorBreakdown(authSupport.requireUser(auth).userId)

    @GetMapping("/me/sets")
    fun sets(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?): List<StatBucket> =
        statsQuery.setBreakdown(authSupport.requireUser(auth).userId)

    @GetMapping("/me/modes")
    fun modes(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?): List<StatBucket> =
        statsQuery.modeBreakdown(authSupport.requireUser(auth).userId)

    @GetMapping("/me/opponents")
    fun opponents(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?): List<HeadToHead> =
        statsQuery.headToHead(authSupport.requireUser(auth).userId)

    /** One page of game history. The total count (for the pager) is returned in `X-Total-Count`. */
    @GetMapping("/me/history")
    fun history(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @RequestParam(defaultValue = "25") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): ResponseEntity<List<GameHistoryEntry>> {
        val userId = authSupport.requireUser(auth).userId
        val page = statsQuery.recentGames(userId, limit.coerceIn(1, 100), offset.coerceAtLeast(0))
        return ResponseEntity.ok()
            .header("X-Total-Count", statsQuery.recentGamesCount(userId).toString())
            .body(page)
    }

    /** Both seats' decklists for one of the user's finished games, for the recent-games deck viewer. */
    @GetMapping("/me/history/{gameId}/decks")
    fun gameDecks(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable gameId: String,
    ): ResponseEntity<GameDecks> {
        val decks = statsQuery.decksForGame(authSupport.requireUser(auth).userId, gameId)
        return if (decks == null) ResponseEntity.notFound().build() else ResponseEntity.ok(decks)
    }

    /** Creature subtypes the user plays most, by total copies across all recorded decks. */
    @GetMapping("/me/creature-types")
    fun creatureTypes(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @RequestParam(defaultValue = "15") limit: Int,
    ): List<StatBucket> =
        statsQuery.creatureTypeBreakdown(authSupport.requireUser(auth).userId, limit.coerceIn(1, 50))

    /** Distribution of the user's cards across primary card types (Creature, Instant, Land, …). */
    @GetMapping("/me/card-types")
    fun cardTypes(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?): List<StatBucket> =
        statsQuery.cardTypeBreakdown(authSupport.requireUser(auth).userId)

    /** Mana-value curve of the user's nonland cards (buckets 0..6 then "7+"). */
    @GetMapping("/me/curve")
    fun curve(@RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?): List<StatBucket> =
        statsQuery.manaCurve(authSupport.requireUser(auth).userId)

    @GetMapping("/me/cards")
    fun cards(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @RequestParam(defaultValue = "30") limit: Int,
    ): List<CardStat> =
        statsQuery.topCardsForUser(authSupport.requireUser(auth).userId, limit.coerceIn(1, 200))

    @GetMapping("/me/tournaments")
    fun tournaments(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @RequestParam(defaultValue = "25") limit: Int,
    ): List<UserTournamentEntry> =
        statsQuery.tournamentHistory(authSupport.requireUser(auth).userId, limit.coerceIn(1, 100))
}
