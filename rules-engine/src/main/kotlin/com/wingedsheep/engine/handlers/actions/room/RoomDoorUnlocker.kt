package com.wingedsheep.engine.handlers.actions.room

import com.wingedsheep.engine.core.DoorUnlockedEvent
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.RoomFullyUnlockedEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.RoomComponent
import com.wingedsheep.engine.state.components.identity.RoomFaceId
import com.wingedsheep.sdk.model.EntityId

/**
 * Shared door-unlock primitive (CR 709.5f/h). Gives a single locked face of a Room permanent the
 * appropriate "unlocked" designation and emits the resulting events.
 *
 * This is the one place that mutates [RoomComponent.unlocked]. Both paths that can unlock a door —
 * the unlock-cost special action ([UnlockRoomDoorHandler], CR 709.5e) and the resolution-time
 * "unlock a door" effect (`UnlockDoorEffect`, CR 709.5f) — funnel through here so they emit
 * identical [DoorUnlockedEvent] / [RoomFullyUnlockedEvent] events. That keeps "When you unlock this
 * door" (CR 709.5h) and "fully unlock" / Eerie triggers firing the same way regardless of how the
 * door was unlocked.
 *
 * Trigger detection is the caller's responsibility: the special-action handler runs it inline
 * (special actions don't use the stack), while the effect executor returns the events for the
 * normal effect-resolution trigger pass to pick up.
 */
object RoomDoorUnlocker {

    /**
     * Unlock [faceId] of the Room [roomId] controlled by [controllerId]. Returns the new state with
     * that face designated unlocked, plus a [DoorUnlockedEvent] (and a [RoomFullyUnlockedEvent] when
     * this transition completes the Room). If the face is already unlocked or the entity isn't a
     * Room, returns the state unchanged with no events.
     */
    fun unlock(
        state: GameState,
        roomId: EntityId,
        faceId: RoomFaceId,
        controllerId: EntityId,
    ): Pair<GameState, List<GameEvent>> {
        val container = state.getEntity(roomId) ?: return state to emptyList()
        val room = container.get<RoomComponent>() ?: return state to emptyList()
        val face = room.faces.find { it.id == faceId } ?: return state to emptyList()
        if (room.isUnlocked(faceId)) return state to emptyList()

        val roomName = container.get<CardComponent>()?.name ?: face.name
        val updatedRoom = room.copy(unlocked = room.unlocked + faceId)
        val newState = state.updateEntity(roomId) { c -> c.with(updatedRoom) }

        val events = mutableListOf<GameEvent>()
        val nowFullyUnlocked = updatedRoom.isFullyUnlocked
        events.add(
            DoorUnlockedEvent(
                roomId = roomId,
                roomName = roomName,
                faceId = faceId,
                faceName = face.name,
                controllerId = controllerId,
                becameFullyUnlocked = nowFullyUnlocked,
            )
        )
        if (nowFullyUnlocked) {
            events.add(
                RoomFullyUnlockedEvent(
                    roomId = roomId,
                    roomName = roomName,
                    controllerId = controllerId,
                )
            )
        }
        return newState to events
    }
}
