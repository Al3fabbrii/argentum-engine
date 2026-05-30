package com.wingedsheep.mtg.sets.definitions.inv.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.effects.MoveToZoneEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Shimmering Wings
 * {U}
 * Enchantment — Aura
 * Enchant creature
 * Enchanted creature has flying.
 * {U}: Return this Aura to its owner's hand.
 */
val ShimmeringWings = card("Shimmering Wings") {
    manaCost = "{U}"
    colorIdentity = "U"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant creature\n" +
        "Enchanted creature has flying.\n" +
        "{U}: Return this Aura to its owner's hand."

    auraTarget = Targets.Creature

    staticAbility {
        ability = GrantKeyword(Keyword.FLYING)
    }

    activatedAbility {
        cost = Costs.Mana("{U}")
        effect = MoveToZoneEffect(EffectTarget.Self, Zone.HAND)
        description = "{U}: Return this Aura to its owner's hand."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "72"
        artist = "Carl Critchlow"
        imageUri = "https://cards.scryfall.io/normal/front/9/6/9615a6c2-1732-4a04-9be1-cc0a8d39de3f.jpg?1562925242"
    }
}
