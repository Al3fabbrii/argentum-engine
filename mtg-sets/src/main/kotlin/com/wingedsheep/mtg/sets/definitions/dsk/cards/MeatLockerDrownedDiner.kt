package com.wingedsheep.mtg.sets.definitions.dsk.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardLayout
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.AddCountersEffect
import com.wingedsheep.sdk.scripting.targets.TargetCreature

/**
 * Meat Locker // Drowned Diner (DSK 65) — split-layout Room (CR 709.5).
 *
 * Meat Locker {2}{U} — Enchantment — Room
 *   When you unlock this door, tap up to one target creature and put two stun counters on it.
 *
 * Drowned Diner {3}{U}{U} — Enchantment — Room
 *   When you unlock this door, draw three cards, then discard a card.
 *
 * Cast each half separately; the cast face enters unlocked, the other locked. Pay the locked face's
 * printed mana cost as a sorcery-speed special action to unlock it (CR 709.5e). Both abilities are
 * "when you unlock this door" triggers ([Triggers.OnDoorUnlocked], CR 709.5h).
 *
 * Meat Locker is the "tap + stun" shape on an *optional* single target ("up to one target
 * creature"): an optional [TargetCreature], then [Effects.Tap] and [AddCountersEffect] with
 * `count = 2` of [Counters.STUN] on that same target — unlike Fear of Immobility, the stun counters
 * are unconditional (no opponent-control gate). If the player chooses no target, nothing happens.
 *
 * Drowned Diner is the canonical loot composite: [Effects.DrawCards] (3) then [Effects.Discard] (1),
 * both affecting the controller.
 */
val MeatLockerDrownedDiner = card("Meat Locker // Drowned Diner") {
    layout = CardLayout.SPLIT
    colorIdentity = "U"

    face("Meat Locker") {
        manaCost = "{2}{U}"
        typeLine = "Enchantment — Room"
        oracleText = "When you unlock this door, tap up to one target creature and put two stun " +
            "counters on it. (If a permanent with a stun counter would become untapped, remove one " +
            "from it instead.)"

        triggeredAbility {
            trigger = Triggers.OnDoorUnlocked
            val t = target("target", TargetCreature(optional = true))
            effect = Effects.Composite(
                Effects.Tap(t),
                AddCountersEffect(counterType = Counters.STUN, count = 2, target = t),
            )
            description = "When you unlock this door, tap up to one target creature and put two " +
                "stun counters on it."
        }
    }

    face("Drowned Diner") {
        manaCost = "{3}{U}{U}"
        typeLine = "Enchantment — Room"
        oracleText = "When you unlock this door, draw three cards, then discard a card."

        triggeredAbility {
            trigger = Triggers.OnDoorUnlocked
            effect = Effects.DrawCards(3).then(Effects.Discard(1))
        }
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "65"
        artist = "Sergey Glushakov"
        imageUri = "https://cards.scryfall.io/normal/front/b/3/b3c773f0-9e65-48de-a362-a9a943198693.jpg?1726780679"
        ruling("2024-09-20", "An ability that triggers \"when you unlock this door\" triggers when that door becomes unlocked, either on the battlefield or as the Room enters because you cast the corresponding half.")
        ruling("2024-09-20", "You can unlock a door even if there would be insufficient legal targets for its triggered ability; the triggered ability simply won't go on the stack.")
    }
}
