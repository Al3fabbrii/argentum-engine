package com.wingedsheep.engine.mechanics.sba.permanent

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.handlers.effects.ZoneMovementUtils.cleanupReverseAttachmentLink
import com.wingedsheep.engine.mechanics.sba.SbaOrder
import com.wingedsheep.engine.mechanics.sba.SbaZoneMovementHelper
import com.wingedsheep.engine.mechanics.sba.StateBasedActionCheck
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.identity.CardComponent

/**
 * 704.5m - An Aura attached to an illegal object/player or not attached goes to graveyard.
 * 704.5n - An Equipment or Fortification attached to an illegal permanent becomes unattached
 *          but remains on the battlefield.
 * 704.5p - A battle or creature attached to an object or player becomes unattached but
 *          remains on the battlefield. Drives the Equipment-that-became-a-creature case
 *          below: when an Equipment (e.g. Atomic Microsizer) becomes a creature via a
 *          layer-4 type-changing effect (Tezzeret, Cruel Captain's emblem turning an
 *          artifact into a 0/0 Robot artifact creature), the underlying CR 301.5c rule
 *          ("an Equipment that's also a creature can't equip a creature unless it has
 *          reconfigure") makes the attachment illegal, and 704.5p is the SBA that
 *          unattaches it. The "is it a creature?" question is asked of the projected
 *          state, since creatureness here comes from a layer-4 type-changing effect,
 *          not the printed type line.
 */
class UnattachedAurasCheck : StateBasedActionCheck {
    override val name = "704.5m/n/p Unattached Auras"
    override val order = SbaOrder.UNATTACHED_AURAS

    override fun check(state: GameState): ExecutionResult {
        var newState = state
        val events = mutableListOf<com.wingedsheep.engine.core.GameEvent>()
        val projected = state.projectedState

        for (entityId in state.getBattlefield().toList()) {
            val container = state.getEntity(entityId) ?: continue
            val cardComponent = container.get<CardComponent>() ?: continue

            val isAura = cardComponent.typeLine.isAura
            val isEquipment = cardComponent.typeLine.isEquipment

            if (!isAura && !isEquipment) continue

            val attachedTo = container.get<AttachedToComponent>()
            if (attachedTo == null) {
                if (isAura) {
                    // Aura not attached to anything - goes to graveyard
                    val result = SbaZoneMovementHelper.putPermanentInGraveyard(
                        newState, entityId, cardComponent
                    )
                    newState = result.newState
                    events.addAll(result.events)
                }
                // Equipment not attached to anything is fine - stays on battlefield
            } else {
                // Check if attached target still exists on battlefield
                if (attachedTo.targetId !in state.getBattlefield()) {
                    if (isAura) {
                        // Aura's target gone - goes to graveyard
                        val result = SbaZoneMovementHelper.putPermanentInGraveyard(
                            newState, entityId, cardComponent,
                            lastKnownAttachedTo = attachedTo.targetId
                        )
                        newState = result.newState
                        events.addAll(result.events)
                    } else {
                        // Equipment's target gone - just detach, stays on battlefield
                        newState = cleanupReverseAttachmentLink(newState, entityId)
                        newState = newState.updateEntity(entityId) { c ->
                            c.without<AttachedToComponent>()
                        }
                    }
                } else if (isEquipment &&
                    projected.isCreature(entityId) &&
                    !projected.hasKeyword(entityId, "RECONFIGURE")
                ) {
                    // CR 704.5p (with 301.5c as the underlying prohibition): an Equipment
                    // that's also a creature can't equip a creature unless it has
                    // reconfigure, so the SBA unattaches it. Stays on the battlefield.
                    newState = cleanupReverseAttachmentLink(newState, entityId)
                    newState = newState.updateEntity(entityId) { c ->
                        c.without<AttachedToComponent>()
                    }
                }
            }
        }

        return ExecutionResult.success(newState, events)
    }
}
