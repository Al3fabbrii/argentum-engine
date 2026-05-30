package com.wingedsheep.engine.handlers.effects.library

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.ScriedEvent
import com.wingedsheep.engine.handlers.DynamicAmountEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.EmitScriedEventEffect
import kotlin.reflect.KClass

/**
 * Tail of the [com.wingedsheep.sdk.dsl.LibraryPatterns.scry] composite: emits a
 * [ScriedEvent] so "Whenever you scry" triggers (CR 701.18) fire exactly once per
 * scry, after the top/bottom moves have all resolved. The count carried is the
 * scry N parameter; this is fixed at composition time today (we don't shorten it
 * when the library held fewer cards — see CR 701.18a). Refine later if that case
 * comes up in practice.
 */
class EmitScriedEventExecutor(
    private val amountEvaluator: DynamicAmountEvaluator = DynamicAmountEvaluator()
) : EffectExecutor<EmitScriedEventEffect> {

    override val effectType: KClass<EmitScriedEventEffect> = EmitScriedEventEffect::class

    override fun execute(
        state: GameState,
        effect: EmitScriedEventEffect,
        context: EffectContext
    ): EffectResult {
        val playerId = context.controllerId
        val count = amountEvaluator.evaluate(state, effect.count, context).coerceAtLeast(0)
        val sourceName = context.sourceId
            ?.let { state.getEntity(it)?.get<CardComponent>()?.name }
            ?: "Scry"

        return EffectResult.success(
            state,
            listOf(ScriedEvent(playerId = playerId, count = count, sourceName = sourceName))
        )
    }
}
