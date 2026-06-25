package com.wingedsheep.sdk.scripting.effects

import com.wingedsheep.sdk.scripting.targets.EffectTarget
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// Room / Door Effects (CR 709.5)
// =============================================================================

/**
 * Resolution-time "unlock a door" effect (CR 709.5f).
 *
 * "To unlock half of a permanent, a player chooses a locked half of that permanent, and that
 * permanent is given the appropriate unlocked designation." This is the spell/ability form of
 * unlocking — distinct from the [unlock cost] special action (CR 709.5e), which pays a half's
 * mana cost. The resolving controller pays nothing.
 *
 * The [target] is the Room whose door is unlocked. Model it with an "up to one target Room you
 * control with a locked door" `TargetObject` (`TargetFilter.…hasLockedDoor()`) so a fully-unlocked
 * Room is never a legal target and the optional ("up to one") form lets the controller choose no
 * target. If the target Room still has a single locked door at resolution it is unlocked; if it has
 * more than one locked door (a Room that entered without being cast, CR 709.5d), one of them is
 * unlocked.
 *
 * Unlocking emits the same `DoorUnlockedEvent` (and `RoomFullyUnlockedEvent` when it completes the
 * Room) the unlock-cost special action emits, so face-scoped "When you unlock this door" triggers
 * (CR 709.5h) and "fully unlock" / Eerie triggers fire identically.
 *
 * Authored via [com.wingedsheep.sdk.dsl.Effects.UnlockDoor].
 */
@SerialName("UnlockDoor")
@Serializable
data class UnlockDoorEffect(
    val target: EffectTarget = EffectTarget.ContextTarget(0)
) : Effect {
    override val description: String = "unlock a locked door of ${target.description} Room"
}
