package com.wingedsheep.engine.handlers.effects.permanent.counters

import com.wingedsheep.engine.core.CountersRemovedEvent
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.RemoveAllCountersEffect
import kotlin.reflect.KClass

/**
 * Executor for [RemoveAllCountersEffect].
 *
 * Mandatory removal of every counter kind currently on the target. Emits one
 * [CountersRemovedEvent] per counter kind cleared.
 */
class RemoveAllCountersExecutor : EffectExecutor<RemoveAllCountersEffect> {

    override val effectType: KClass<RemoveAllCountersEffect> = RemoveAllCountersEffect::class

    override fun execute(
        state: GameState,
        effect: RemoveAllCountersEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target)
            ?: return EffectResult.success(state, emptyList())

        val targetEntity = state.getEntity(targetId)
            ?: return EffectResult.success(state, emptyList())

        val counters = targetEntity.get<CountersComponent>() ?: return EffectResult.success(state, emptyList())
        val present = counters.counters.entries.filter { it.value > 0 }
        if (present.isEmpty()) return EffectResult.success(state, emptyList())

        val newState = state.updateEntity(targetId) { container ->
            container.with(CountersComponent())
        }

        val entityName = targetEntity.get<CardComponent>()?.name ?: ""
        val events = present.map { (type, amount) ->
            CountersRemovedEvent(targetId, counterTypeToString(type), amount, entityName)
        }

        return EffectResult.success(newState, events)
    }
}
