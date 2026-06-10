package com.wingedsheep.engine.handlers.effects.composite

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.effects.PayManaCostEffect
import kotlin.reflect.KClass

/**
 * Executor for PayManaCostEffect.
 * Non-optional mana payment from the ability's controller: auto-taps lands and deducts the cost.
 * Shares the auto-tap/deduct core with [PayDynamicManaCostExecutor] via [payGenericFromPool].
 */
class PayManaCostExecutor(
    private val cardRegistry: CardRegistry
) : EffectExecutor<PayManaCostEffect> {

    override val effectType: KClass<PayManaCostEffect> = PayManaCostEffect::class

    override fun execute(
        state: GameState,
        effect: PayManaCostEffect,
        context: EffectContext
    ): EffectResult =
        payGenericFromPool(state, context.controllerId, effect.cost, cardRegistry)
}
