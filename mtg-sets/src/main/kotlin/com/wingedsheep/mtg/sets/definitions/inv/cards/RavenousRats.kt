package com.wingedsheep.mtg.sets.definitions.inv.cards

import com.wingedsheep.sdk.dsl.EffectPatterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.TargetOpponent

/**
 * Ravenous Rats
 * {1}{B}
 * Creature — Rat
 * 1/1
 * When this creature enters, target opponent discards a card.
 *
 * Canonical printing note: Ravenous Rats first appeared in Portal Second Age (P02),
 * which is not scaffolded in this repo. Invasion (INV) is the earliest scaffolded set
 * that printed it, so the canonical CardDefinition lives here.
 */
val RavenousRats = card("Ravenous Rats") {
    manaCost = "{1}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Rat"
    power = 1
    toughness = 1
    oracleText = "When this creature enters, target opponent discards a card."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        val t = target("target opponent", TargetOpponent())
        effect = EffectPatterns.discardCards(1, t)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "120"
        artist = "Tom Wänerstrand"
        imageUri = "https://cards.scryfall.io/normal/front/8/9/89e29069-add5-4099-b800-9f1e4402cc1a.jpg?1562922876"
    }
}
