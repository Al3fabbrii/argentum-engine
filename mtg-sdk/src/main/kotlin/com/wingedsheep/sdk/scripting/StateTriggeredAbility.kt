package com.wingedsheep.sdk.scripting

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.conditions.Condition
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.text.TextReplaceable
import com.wingedsheep.sdk.scripting.text.TextReplacer
import kotlinx.serialization.Serializable

/**
 * A state-triggered ability per CR 603.8: it triggers whenever its [condition] becomes true,
 * rather than in response to a [GameEvent].
 *
 * Per 603.8a–d:
 * - The condition is checked each time a player would receive priority and after the stack
 *   is empty / nothing else is happening.
 * - Once the ability has triggered, it does not trigger again until the condition has been
 *   false at some point afterward (the "latch"). The engine tracks this per
 *   (entityId, abilityId) via [com.wingedsheep.engine.state.components.battlefield.StateTriggerLatchesComponent]
 *   in the rules-engine module.
 * - When it does trigger, it goes onto the stack as a normal triggered ability and resolves
 *   under stack rules.
 *
 * Authored on cards like Dandân ("When you control no Islands, sacrifice this creature"),
 * Force Bubble ("when there are four or more depletion counters on ~, sacrifice it"), etc.
 * Use the [com.wingedsheep.sdk.dsl.CardBuilder.stateTriggeredAbility] DSL block; don't
 * instantiate directly.
 */
@Serializable
data class StateTriggeredAbility(
    val id: AbilityId,
    /** The state predicate (CR 603.8). Evaluated against the source permanent's context. */
    val condition: Condition,
    val effect: Effect,
    val activeZone: Zone = Zone.BATTLEFIELD,
    /** Optional human-readable override; otherwise auto-generated from condition + effect. */
    val descriptionOverride: String? = null
) : TextReplaceable<StateTriggeredAbility> {

    val description: String
        get() = descriptionOverride ?: buildString {
            append("when ")
            append(condition.description.removePrefix("if ").removePrefix("If "))
            append(", ")
            append(effect.description.replaceFirstChar { it.lowercase() })
            append(".")
        }

    override fun applyTextReplacement(replacer: TextReplacer): StateTriggeredAbility {
        val newCondition = condition.applyTextReplacement(replacer)
        val newEffect = effect.applyTextReplacement(replacer)
        return if (newCondition !== condition || newEffect !== effect)
            copy(condition = newCondition, effect = newEffect) else this
    }

    companion object {
        fun create(
            condition: Condition,
            effect: Effect,
            activeZone: Zone = Zone.BATTLEFIELD,
            descriptionOverride: String? = null
        ): StateTriggeredAbility = StateTriggeredAbility(
            id = AbilityId.generate(),
            condition = condition,
            effect = effect,
            activeZone = activeZone,
            descriptionOverride = descriptionOverride
        )
    }
}
