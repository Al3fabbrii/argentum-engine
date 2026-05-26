package com.wingedsheep.engine.handlers.effects.drawing

import com.wingedsheep.engine.core.CardsRevealedEvent
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.DrawRevealDiscardUnlessEffect
import kotlin.reflect.KClass

/**
 * Executor for [DrawRevealDiscardUnlessEffect] — "Draw a card and reveal it. If it isn't
 * [filter], discard it." (Sindbad).
 *
 * Draws one card via the shared [DrawCardPrimitive], reveals it to all players, then evaluates
 * the drawn card against the effect's filter using base state (the card is in hand, a
 * non-battlefield zone, so no projection is required). If it doesn't match, the card is
 * discarded via [ZoneTransitionService.discardCard] so the canonical `CardsDiscardedEvent`
 * fires (discard triggers, madness, client log) rather than a bare zone change.
 */
class DrawRevealDiscardUnlessExecutor(
    cardRegistry: CardRegistry
) : EffectExecutor<DrawRevealDiscardUnlessEffect> {

    override val effectType: KClass<DrawRevealDiscardUnlessEffect> = DrawRevealDiscardUnlessEffect::class

    private val primitive = DrawCardPrimitive(cardRegistry)
    private val predicateEvaluator = PredicateEvaluator()

    override fun execute(
        state: GameState,
        effect: DrawRevealDiscardUnlessEffect,
        context: EffectContext
    ): EffectResult {
        val playerIds = context.resolvePlayerTargets(effect.target, state)
        if (playerIds.isEmpty()) {
            return EffectResult.error(state, "No valid player for draw")
        }

        var currentState = state
        val allEvents = mutableListOf<GameEvent>()
        val sourceName = context.sourceId?.let { currentState.getEntity(it)?.get<CardComponent>()?.name }
        for (playerId in playerIds) {
            val drawResult = primitive.drawOne(currentState, playerId, emptyLibraryReason = "Draw from empty library")
            currentState = drawResult.state
            allEvents.addAll(drawResult.events)

            val drawnCardId = drawResult.drawnCardId ?: continue

            // "reveal it" — show the drawn card to all players whether it's kept or discarded.
            val drawnCard = currentState.getEntity(drawnCardId)?.get<CardComponent>()
            allEvents.add(
                CardsRevealedEvent(
                    revealingPlayerId = playerId,
                    cardIds = listOf(drawnCardId),
                    cardNames = listOf(drawnCard?.name ?: "Unknown"),
                    imageUris = listOf(drawnCard?.imageUri),
                    source = sourceName,
                )
            )

            val matches = predicateEvaluator.matches(
                currentState,
                currentState.projectedState,
                drawnCardId,
                effect.filter,
                PredicateContext.fromEffectContext(context),
            )
            if (!matches) {
                val discardResult = ZoneTransitionService.discardCard(currentState, playerId, drawnCardId)
                currentState = discardResult.state
                allEvents.addAll(discardResult.events)
            }
        }
        return EffectResult.success(currentState, allEvents)
    }
}
