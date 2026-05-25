package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.DamageUtils
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.layers.ActiveFloatingEffect
import com.wingedsheep.engine.mechanics.layers.FloatingEffectData
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.FaceDownComponent
import com.wingedsheep.sdk.model.EntityId

class CombatContinuationResumer(
    private val services: com.wingedsheep.engine.core.EngineServices
) : ContinuationResumerModule {

    override fun resumers(): List<ContinuationResumer<*>> = listOf(
        resumer(DamageAssignmentContinuation::class) { state, continuation, response, _ ->
            resumeDamageAssignment(state, continuation, response)
        },
        resumer(CombatResolutionContinuation::class) { state, continuation, response, _ ->
            resumeCombatResolution(state, continuation, response)
        },
        resumer(AssignAsUnblockedContinuation::class) { state, continuation, response, _ ->
            resumeAssignAsUnblocked(state, continuation, response)
        },
        resumer(DamagePreventionContinuation::class, ::resumeDamagePrevention),
        resumer(BlockerOrderContinuation::class, ::resumeBlockerOrder),
        resumer(AttackerOrderContinuation::class, ::resumeAttackerOrder),
        resumer(DistributeDamageContinuation::class, ::resumeDistributeDamage),
        resumer(DeflectDamageSourceChoiceContinuation::class, ::resumeDeflectDamageSourceChoice),
        resumer(PreventDamageFromChosenSourceContinuation::class, ::resumePreventDamageFromChosenSource)
    )

    fun resumeDamageAssignment(
        state: GameState,
        continuation: DamageAssignmentContinuation,
        response: DecisionResponse
    ): ExecutionResult {
        val assignments = when (response) {
            is DistributionResponse -> response.distribution
            is DamageAssignmentResponse -> response.assignments
            else -> return ExecutionResult.error(state, "Expected distribution or damage assignment response")
        }

        val newState = state.updateEntity(continuation.attackerId) { container ->
            container.with(
                com.wingedsheep.engine.state.components.combat.DamageAssignmentComponent(
                    assignments
                )
            )
        }

        return services.combatManager.applyCombatDamage(newState, firstStrike = continuation.firstStrike)
    }

    fun resumeAssignAsUnblocked(
        state: GameState,
        continuation: AssignAsUnblockedContinuation,
        response: DecisionResponse
    ): ExecutionResult {
        if (response !is YesNoResponse) {
            return ExecutionResult.error(state, "Expected yes/no response for assign-as-unblocked decision")
        }

        val newState = if (response.choice) {
            // Player chose to assign damage to the defending player — store a manual assignment
            val projected = state.projectedState
            val power = projected.getPower(continuation.attackerId) ?: 0
            state.updateEntity(continuation.attackerId) { container ->
                container.with(
                    com.wingedsheep.engine.state.components.combat.DamageAssignmentComponent(
                        mapOf(continuation.defendingPlayerId to power)
                    )
                )
            }
        } else {
            // Player chose to assign to blockers normally — mark with empty assignment
            // so the pre-check doesn't re-ask; proposeDamageAssignments will auto-distribute
            state.updateEntity(continuation.attackerId) { container ->
                container.with(
                    com.wingedsheep.engine.state.components.combat.DamageAssignmentComponent(emptyMap())
                )
            }
        }

        return services.combatManager.applyCombatDamage(newState, firstStrike = continuation.firstStrike)
    }

    /**
     * Apply a [CombatResolutionResponse] (the combat-damage board).
     *
     * The current chooser is `continuation.pendingChoosers.first()`. We honor only the edges they
     * own (filtered by [DamageEdge.editableBy] on the cached `decisionShape`), bake those amounts
     * on top of the shape's current amounts, and:
     *
     * - if more choosers remain (CR 510.1c sequencing, or the CR 702.22j/k two-actor banding case),
     *   re-pause via [com.wingedsheep.engine.mechanics.combat.CombatManager.repauseCombatResolution]
     *   for the next chooser with the locked-in amounts shown;
     * - otherwise fold every edge into a per-source [DamageAssignmentComponent] (read straight off
     *   the cached edge objects — no edge-id parsing), apply any row-order overrides, and re-enter
     *   `applyCombatDamage` to run the damage pipeline.
     */
    fun resumeCombatResolution(
        state: GameState,
        continuation: CombatResolutionContinuation,
        response: DecisionResponse,
    ): ExecutionResult {
        if (response !is CombatResolutionResponse) {
            return ExecutionResult.error(state, "Expected combat resolution response for combat resolution decision")
        }

        val shape = continuation.decisionShape
        val submittingPlayer = continuation.pendingChoosers.firstOrNull()
        val remainingChoosers = continuation.pendingChoosers.drop(1)

        val edgeById = shape.edges.associateBy { it.id }
        val submittedByEdge = response.edges.associate { it.edgeId to it.amount }
        // Keep only edges this chooser owns; unknown ids and other-owner edges are dropped.
        val honoredByEdge = submittedByEdge.filter { (edgeId, _) ->
            submittingPlayer != null && edgeById[edgeId]?.editableBy == submittingPlayer
        }
        // Bake onto the shape's current amounts so the next chooser (if any) sees locked-in values.
        val accumulatedAmounts: Map<String, Int> = shape.edges.associate { it.id to it.amount } + honoredByEdge

        if (remainingChoosers.isNotEmpty()) {
            return services.combatManager.repauseCombatResolution(
                state = state,
                previous = shape,
                remainingChoosers = remainingChoosers,
                latestAmounts = accumulatedAmounts,
                firstStrike = continuation.firstStrike,
            )
        }

        // All choosers confirmed — write per-source DamageAssignmentComponents from the edge objects.
        val assignmentsBySource = mutableMapOf<EntityId, MutableMap<EntityId, Int>>()
        for ((edgeId, amount) in accumulatedAmounts) {
            val edge = edgeById[edgeId] ?: continue
            assignmentsBySource.getOrPut(edge.sourceId) { mutableMapOf() }[edge.targetId] = amount
        }

        var newState = state
        for ((sourceId, assignments) in assignmentsBySource) {
            newState = newState.updateEntity(sourceId) { container ->
                container.with(
                    com.wingedsheep.engine.state.components.combat.DamageAssignmentComponent(assignments)
                )
            }
        }
        for ((attackerId, order) in response.orderedBlockers) {
            newState = newState.updateEntity(attackerId) { container ->
                container.with(
                    com.wingedsheep.engine.state.components.combat.DamageAssignmentOrderComponent(order)
                )
            }
        }
        for ((blockerId, order) in response.orderedAttackers) {
            newState = newState.updateEntity(blockerId) { container ->
                container.with(
                    com.wingedsheep.engine.state.components.combat.AttackerOrderComponent(order)
                )
            }
        }

        return services.combatManager.applyCombatDamage(newState, firstStrike = continuation.firstStrike)
    }

    fun resumeDamagePrevention(
        state: GameState,
        continuation: DamagePreventionContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is DistributionResponse) {
            return ExecutionResult.error(state, "Expected distribution response for damage prevention")
        }

        val updatedEffects = state.floatingEffects.toMutableList()
        val shieldIndex = updatedEffects.indexOfFirst { it.id == continuation.shieldEffectId }
        val originalShield = if (shieldIndex >= 0) updatedEffects.removeAt(shieldIndex) else null

        val timestamp = originalShield?.timestamp ?: state.timestamp
        for ((sourceId, preventionAmount) in response.distribution) {
            if (preventionAmount <= 0) continue
            updatedEffects.add(
                ActiveFloatingEffect(
                    id = EntityId(java.util.UUID.randomUUID().toString()),
                    effect = FloatingEffectData(
                        layer = Layer.ABILITY,
                        modification = SerializableModification.PreventNextDamage(preventionAmount, onlyFromSource = sourceId),
                        affectedEntities = setOf(continuation.recipientId)
                    ),
                    duration = originalShield?.duration ?: com.wingedsheep.sdk.scripting.Duration.EndOfTurn,
                    sourceId = originalShield?.sourceId,
                    sourceName = originalShield?.sourceName,
                    controllerId = originalShield?.controllerId ?: continuation.recipientId,
                    timestamp = timestamp
                )
            )
        }

        val newState = state.copy(floatingEffects = updatedEffects)

        return services.combatManager.applyCombatDamage(newState, firstStrike = continuation.firstStrike)
    }

    fun resumeBlockerOrder(
        state: GameState,
        continuation: BlockerOrderContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is OrderedResponse) {
            return ExecutionResult.error(state, "Expected ordered response for blocker ordering")
        }

        var newState = state.updateEntity(continuation.attackerId) { container ->
            container.with(
                com.wingedsheep.engine.state.components.combat.DamageAssignmentOrderComponent(
                    response.orderedObjects
                )
            )
        }

        val events = mutableListOf<GameEvent>(
            BlockerOrderDeclaredEvent(continuation.attackerId, response.orderedObjects)
        )

        if (continuation.remainingAttackers.isNotEmpty()) {
            val nextAttacker = continuation.remainingAttackers.first()
            val nextRemaining = continuation.remainingAttackers.drop(1)

            val attackerContainer = newState.getEntity(nextAttacker)!!
            val attackerCard = attackerContainer.get<CardComponent>()!!
            val attackerIsFaceDown = attackerContainer.has<FaceDownComponent>()
            val attackerDisplayName = if (attackerIsFaceDown) "Face-down creature" else attackerCard.name
            val blockedComponent = attackerContainer.get<com.wingedsheep.engine.state.components.combat.BlockedComponent>()!!
            val blockerIds = blockedComponent.blockerIds

            val cardInfo = blockerIds.associateWith { blockerId ->
                val blockerContainer = newState.getEntity(blockerId)
                val isFaceDown = blockerContainer?.has<FaceDownComponent>() == true
                if (isFaceDown) {
                    SearchCardInfo(
                        name = "Morph",
                        manaCost = "{3}",
                        typeLine = "Creature"
                    )
                } else {
                    val blockerCard = blockerContainer?.get<CardComponent>()
                    SearchCardInfo(
                        name = blockerCard?.name ?: "Unknown",
                        manaCost = blockerCard?.manaCost?.toString() ?: "",
                        typeLine = blockerCard?.typeLine?.toString() ?: ""
                    )
                }
            }

            val decisionId = java.util.UUID.randomUUID().toString()
            val decision = OrderObjectsDecision(
                id = decisionId,
                playerId = continuation.attackingPlayerId,
                prompt = "Order damage assignment for $attackerDisplayName",
                context = DecisionContext(
                    sourceId = nextAttacker,
                    sourceName = attackerDisplayName,
                    phase = DecisionPhase.COMBAT
                ),
                objects = blockerIds,
                cardInfo = cardInfo
            )

            val nextContinuation = BlockerOrderContinuation(
                decisionId = decisionId,
                attackingPlayerId = continuation.attackingPlayerId,
                attackerId = nextAttacker,
                attackerName = attackerDisplayName,
                remainingAttackers = nextRemaining
            )

            newState = newState
                .withPendingDecision(decision)
                .pushContinuation(nextContinuation)

            events.add(
                DecisionRequestedEvent(
                    decisionId = decision.id,
                    playerId = continuation.attackingPlayerId,
                    decisionType = "ORDER_BLOCKERS",
                    prompt = decision.prompt
                )
            )

            return ExecutionResult.paused(newState, decision, events)
        }

        // After all blocker orders are done, check if any blockers need attacker ordering
        val blockersNeedingOrder = newState.findEntitiesWith<com.wingedsheep.engine.state.components.combat.BlockingComponent>()
            .filter { (_, blocking) -> blocking.blockedAttackerIds.size >= 2 }
            .map { it.first }
            .filter { blockerId ->
                newState.getEntity(blockerId)?.get<com.wingedsheep.engine.state.components.combat.AttackerOrderComponent>() == null
            }
        if (blockersNeedingOrder.isNotEmpty()) {
            return services.combatManager.createAttackerOrderDecision(
                newState,
                attackingPlayer = continuation.attackingPlayerId,
                firstBlocker = blockersNeedingOrder.first(),
                remainingBlockers = blockersNeedingOrder.drop(1),
                precedingEvents = events
            )
        }

        val activePlayer = newState.activePlayerId
        val stateWithPriority = if (activePlayer != null) {
            newState.withPriority(activePlayer)
        } else {
            newState
        }
        return checkForMore(stateWithPriority, events)
    }

    fun resumeAttackerOrder(
        state: GameState,
        continuation: AttackerOrderContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is OrderedResponse) {
            return ExecutionResult.error(state, "Expected ordered response for attacker ordering")
        }

        var newState = state.updateEntity(continuation.blockerId) { container ->
            container.with(
                com.wingedsheep.engine.state.components.combat.AttackerOrderComponent(
                    response.orderedObjects
                )
            )
        }

        val events = mutableListOf<GameEvent>(
            AttackerOrderDeclaredEvent(continuation.blockerId, response.orderedObjects)
        )

        if (continuation.remainingBlockers.isNotEmpty()) {
            val nextBlocker = continuation.remainingBlockers.first()
            val nextRemaining = continuation.remainingBlockers.drop(1)

            val blockerContainer = newState.getEntity(nextBlocker)!!
            val blockerCard = blockerContainer.get<CardComponent>()!!
            val blockerIsFaceDown = blockerContainer.has<FaceDownComponent>()
            val blockerDisplayName = if (blockerIsFaceDown) "Face-down creature" else blockerCard.name
            val blockingComponent = blockerContainer.get<com.wingedsheep.engine.state.components.combat.BlockingComponent>()!!
            val attackerIds = blockingComponent.blockedAttackerIds

            val cardInfo = attackerIds.associateWith { attackerId ->
                val attackerContainer = newState.getEntity(attackerId)
                val isFaceDown = attackerContainer?.has<FaceDownComponent>() == true
                if (isFaceDown) {
                    SearchCardInfo(
                        name = "Morph",
                        manaCost = "{3}",
                        typeLine = "Creature"
                    )
                } else {
                    val attackerCard = attackerContainer?.get<CardComponent>()
                    SearchCardInfo(
                        name = attackerCard?.name ?: "Unknown",
                        manaCost = attackerCard?.manaCost?.toString() ?: "",
                        typeLine = attackerCard?.typeLine?.toString() ?: ""
                    )
                }
            }

            val decisionId = java.util.UUID.randomUUID().toString()
            val decision = OrderObjectsDecision(
                id = decisionId,
                playerId = continuation.attackingPlayerId,
                prompt = "Order damage assignment for $blockerDisplayName's blocked attackers",
                context = DecisionContext(
                    sourceId = nextBlocker,
                    sourceName = blockerDisplayName,
                    phase = DecisionPhase.COMBAT
                ),
                objects = attackerIds,
                cardInfo = cardInfo
            )

            val nextContinuation = AttackerOrderContinuation(
                decisionId = decisionId,
                attackingPlayerId = continuation.attackingPlayerId,
                blockerId = nextBlocker,
                blockerName = blockerDisplayName,
                remainingBlockers = nextRemaining
            )

            newState = newState
                .withPendingDecision(decision)
                .pushContinuation(nextContinuation)

            events.add(
                DecisionRequestedEvent(
                    decisionId = decision.id,
                    playerId = continuation.attackingPlayerId,
                    decisionType = "ORDER_ATTACKERS",
                    prompt = decision.prompt
                )
            )

            return ExecutionResult.paused(newState, decision, events)
        }

        val activePlayer = newState.activePlayerId
        val stateWithPriority = if (activePlayer != null) {
            newState.withPriority(activePlayer)
        } else {
            newState
        }
        return checkForMore(stateWithPriority, events)
    }

    fun resumeDistributeDamage(
        state: GameState,
        continuation: DistributeDamageContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is DistributionResponse) {
            return ExecutionResult.error(state, "Expected distribution response for divided damage")
        }

        val distribution = response.distribution
        val events = mutableListOf<GameEvent>()
        var newState = state

        for ((targetId, damageAmount) in distribution) {
            if (damageAmount > 0) {
                val result = DamageUtils.dealDamageToTarget(
                    newState,
                    targetId,
                    damageAmount,
                    continuation.sourceId
                )

                if (!result.isSuccess) {
                    return ExecutionResult(newState, events, result.error)
                }

                newState = result.state
                events.addAll(result.events)
            }
        }

        return checkForMore(newState, events)
    }

    fun resumeDeflectDamageSourceChoice(
        state: GameState,
        continuation: DeflectDamageSourceChoiceContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is CardsSelectedResponse) {
            return ExecutionResult.error(state, "Expected cards selected response for source selection")
        }

        val chosenSourceId = response.selectedCards.firstOrNull()
            ?: return ExecutionResult.error(state, "No source selected")

        val deflectSourceId = continuation.sourceId ?: EntityId.generate()

        val context = EffectContext(
            sourceId = continuation.sourceId,
            controllerId = continuation.controllerId,
            opponentId = null
        )
        val newState = state.addFloatingEffect(
            layer = Layer.ABILITY,
            modification = SerializableModification.DeflectNextDamageFromSource(
                damageSourceId = chosenSourceId,
                deflectSourceId = deflectSourceId
            ),
            affectedEntities = setOf(continuation.controllerId),
            duration = com.wingedsheep.sdk.scripting.Duration.EndOfTurn,
            context = context
        )

        return checkForMore(newState, emptyList())
    }

    fun resumePreventDamageFromChosenSource(
        state: GameState,
        continuation: PreventDamageFromChosenSourceContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is CardsSelectedResponse) {
            return ExecutionResult.error(state, "Expected cards selected response for source selection")
        }

        val chosenSourceId = response.selectedCards.firstOrNull()
            ?: return ExecutionResult.error(state, "No source selected")

        val context = EffectContext(
            sourceId = continuation.sourceId,
            controllerId = continuation.controllerId,
            opponentId = null
        )
        val newState = state.addFloatingEffect(
            layer = Layer.ABILITY,
            modification = SerializableModification.PreventNextDamage(
                remainingAmount = continuation.amount,
                onlyFromSource = chosenSourceId
            ),
            affectedEntities = setOf(continuation.targetId),
            duration = com.wingedsheep.sdk.scripting.Duration.EndOfTurn,
            context = context
        )

        return checkForMore(newState, emptyList())
    }
}
