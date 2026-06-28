package com.wingedsheep.mtg.sets.definitions.fin.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.Duration

/**
 * Ride the Shoopuf
 * {1}{G}
 * Enchantment
 * Landfall — Whenever a land you control enters, put a +1/+1 counter on target creature you control.
 * {5}{G}{G}: This enchantment becomes a 7/7 Beast creature in addition to its other types.
 *
 * Landfall trigger ([Triggers.LandYouControlEnters]) targeting a creature you control, plus an
 * activated animate that turns the enchantment itself into a 7/7 Beast *permanently*
 * ([Duration.Permanent]) while keeping its other types (BecomeCreature leaves existing types and
 * always adds CREATURE).
 */
val RideTheShoopuf = card("Ride the Shoopuf") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Enchantment"
    oracleText = "Landfall — Whenever a land you control enters, put a +1/+1 counter on target " +
        "creature you control.\n" +
        "{5}{G}{G}: This enchantment becomes a 7/7 Beast creature in addition to its other types."

    triggeredAbility {
        trigger = Triggers.LandYouControlEnters
        val t = target("target creature you control", Targets.CreatureYouControl)
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, t)
    }

    activatedAbility {
        cost = Costs.Mana("{5}{G}{G}")
        effect = Effects.BecomeCreature(
            power = 7,
            toughness = 7,
            creatureTypes = setOf("Beast"),
            duration = Duration.Permanent,
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "197"
        artist = "Leonardo Santanna"
        flavorText = "\"Ride ze shoopuf? All aboards!\""
        imageUri = "https://cards.scryfall.io/normal/front/1/9/19ad36d6-8bf4-490c-9980-b98a470af892.jpg?1748706498"
    }
}
