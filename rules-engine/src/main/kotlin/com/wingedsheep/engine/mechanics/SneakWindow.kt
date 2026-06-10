package com.wingedsheep.engine.mechanics

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.combat.BlockingComponent
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId

/**
 * Sneak timing + cost helper (CR 702.190).
 *
 * "Sneak [cost]" means: *"Any time you could cast an instant during your declare blockers
 * step, you may cast this spell by paying [cost] and returning an unblocked creature you
 * control to its owner's hand rather than paying this spell's mana cost."* (CR 702.190a)
 *
 * This object centralizes the two facts every sneak code path needs so the gnarly combat-state
 * reads live in exactly one place (the legal-action enumerator, the cast handler's validate and
 * execute all consult it):
 *  - [isWindowOpen] — is it legal to *announce* a sneak cast right now, and
 *  - [unblockedAttackers] — which creatures can be returned to pay the sneak cost.
 *
 * Both are pure reads against [GameState]; the controller of each attacker is taken from the
 * projected state (battlefield reads must honor control-changing effects, CR 613).
 */
object SneakWindow {

    /**
     * The window is open for [playerId] when it is the declare blockers step of *their* combat
     * (they are the active player, CR 702.190a "your declare blockers step") and they control
     * at least one unblocked attacker available to return as the sneak cost.
     */
    fun isWindowOpen(state: GameState, playerId: EntityId): Boolean =
        state.step == Step.DECLARE_BLOCKERS &&
            state.activePlayerId == playerId &&
            unblockedAttackers(state, playerId).isNotEmpty()

    /**
     * Unblocked attackers [playerId] controls — the legal pool for the "return an unblocked
     * creature you control to its owner's hand" portion of a sneak cost. A creature qualifies
     * when it has an [AttackingComponent], its projected controller is [playerId], and no
     * creature currently blocks it (CR 509.1h — an attacker with no creatures declared as its
     * blockers is unblocked).
     */
    fun unblockedAttackers(state: GameState, playerId: EntityId): List<EntityId> {
        val projected = state.projectedState
        val battlefield = state.getBattlefield()
        return battlefield.filter { entityId ->
            val entity = state.getEntity(entityId) ?: return@filter false
            entity.get<AttackingComponent>() != null &&
                projected.getController(entityId) == playerId &&
                battlefield.none { blockerId ->
                    state.getEntity(blockerId)
                        ?.get<BlockingComponent>()
                        ?.blockedAttackerIds
                        ?.contains(entityId) == true
                }
        }
    }
}
