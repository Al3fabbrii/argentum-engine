package com.wingedsheep.engine.mechanics.combat

import com.wingedsheep.engine.mechanics.layers.ProjectedState
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.AttachmentsComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AssignDamageEqualToToughness
import com.wingedsheep.sdk.scripting.ConditionalStaticAbility
import com.wingedsheep.sdk.scripting.StaticAbility
import com.wingedsheep.sdk.scripting.filters.unified.Scope
import com.wingedsheep.sdk.scripting.predicates.ControllerPredicate
import com.wingedsheep.engine.state.components.identity.ControllerComponent

/**
 * Helpers for calculating the amount of combat damage a creature assigns.
 *
 * Most creatures assign damage equal to their power, but some cards
 * (Doran the Siege Tower, Bark of Doran) substitute toughness — either
 * always or only when toughness exceeds power.
 */
internal object CombatDamageUtils {

    /**
     * Returns the combat damage amount that [creatureId] assigns this step.
     *
     * Defaults to the creature's projected power. If the creature (or any
     * permanent attached to it) has [AssignDamageEqualToToughness] and that
     * ability's condition holds, returns projected toughness instead.
     */
    fun getAssignedCombatDamage(
        state: GameState,
        projected: ProjectedState,
        creatureId: EntityId,
        cardRegistry: CardRegistry?,
    ): Int {
        val power = projected.getPower(creatureId) ?: 0
        if (cardRegistry == null) return power

        val toughness = projected.getToughness(creatureId) ?: 0
        return if (assignsDamageAsToughness(state, creatureId, cardRegistry, power, toughness)) {
            toughness.coerceAtLeast(0)
        } else {
            power
        }
    }

    private fun assignsDamageAsToughness(
        state: GameState,
        creatureId: EntityId,
        cardRegistry: CardRegistry,
        power: Int,
        toughness: Int,
    ): Boolean {
        // The creature itself (e.g., Doran the Siege Tower, filter scope = Self)
        val selfCardId = state.getEntity(creatureId)?.get<CardComponent>()?.cardDefinitionId
        if (selfCardId != null) {
            val abilities = cardRegistry.getCard(selfCardId)?.staticAbilities.orEmpty()
            if (matches(abilities, Scope.Self, power, toughness)) return true
        }

        // Equipment/Aura attached to the creature (e.g., Bark of Doran, filter scope = AttachedTo)
        val attachments = state.getEntity(creatureId)?.get<AttachmentsComponent>()?.attachedIds.orEmpty()
        for (attachId in attachments) {
            val attachCardId = state.getEntity(attachId)?.get<CardComponent>()?.cardDefinitionId ?: continue
            val abilities = cardRegistry.getCard(attachCardId)?.staticAbilities.orEmpty()
            if (matches(abilities, Scope.AttachedTo, power, toughness)) return true
        }

        // Global permanents with Scope.Battlefield (e.g., Tapestry Warden: "creatures you control")
        // The source may equal the creature (e.g., Tapestry Warden applying to itself).
        val creatureController = state.getEntity(creatureId)?.get<ControllerComponent>()?.playerId
        for (permanentId in state.getBattlefield()) {
            val permCardId = state.getEntity(permanentId)?.get<CardComponent>()?.cardDefinitionId ?: continue
            val abilities = cardRegistry.getCard(permCardId)?.staticAbilities.orEmpty()
            if (matchesBattlefield(state, permanentId, creatureId, creatureController, abilities, power, toughness)) return true
        }

        return false
    }

    private fun matchesBattlefield(
        state: GameState,
        sourceId: EntityId,
        creatureId: EntityId,
        creatureController: EntityId?,
        abilities: List<StaticAbility>,
        power: Int,
        toughness: Int,
    ): Boolean {
        val sourceController = state.getEntity(sourceId)?.get<ControllerComponent>()?.playerId
        for (ability in abilities) {
            val unwrapped = if (ability is ConditionalStaticAbility) ability.ability else ability
            if (unwrapped !is AssignDamageEqualToToughness) continue
            if (unwrapped.filter.scope !is Scope.Battlefield) continue
            // Honor excludeSelf: skip if this ability excludes the source and creature is the source
            if (unwrapped.filter.excludeSelf && sourceId == creatureId) continue
            if (unwrapped.onlyWhenToughnessGreaterThanPower && toughness <= power) continue
            // Only the controllerPredicate is evaluated; CardPredicate/StatePredicate
            // restrictions in baseFilter (e.g. subtype filters) are not checked here.
            // Tapestry Warden uses AllCreaturesYouControl which has no card predicates,
            // so this is correct for all current uses.
            when (unwrapped.filter.baseFilter.controllerPredicate) {
                ControllerPredicate.ControlledByYou -> if (creatureController != sourceController) continue
                ControllerPredicate.ControlledByOpponent -> if (creatureController == sourceController) continue
                null, ControllerPredicate.ControlledByAny -> { /* applies to all */ }
                else -> continue
            }
            return true
        }
        return false
    }

    private fun matches(
        abilities: List<StaticAbility>,
        expectedScope: Scope,
        power: Int,
        toughness: Int,
    ): Boolean {
        for (ability in abilities) {
            val unwrapped = if (ability is ConditionalStaticAbility) ability.ability else ability
            if (unwrapped !is AssignDamageEqualToToughness) continue
            if (unwrapped.filter.scope != expectedScope) continue
            if (unwrapped.onlyWhenToughnessGreaterThanPower && toughness <= power) continue
            return true
        }
        return false
    }
}
