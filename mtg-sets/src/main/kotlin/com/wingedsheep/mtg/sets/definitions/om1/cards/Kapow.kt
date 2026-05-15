package com.wingedsheep.mtg.sets.definitions.om1.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect

/**
 * Kapow!
 * {2}{G}
 * Sorcery
 * Put a +1/+1 counter on target creature you control. It fights target creature
 * an opponent controls. (Each deals damage equal to its power to the other.)
 */
val Kapow = card("Kapow!") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Sorcery"
    oracleText = "Put a +1/+1 counter on target creature you control. It fights target creature an opponent controls. (Each deals damage equal to its power to the other.)"

    spell {
        val yourCreature = target("creature you control", Targets.CreatureYouControl)
        val theirCreature = target("creature an opponent controls", Targets.CreatureOpponentControls)
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, yourCreature)
            .then(
                ConditionalEffect(
                    condition = Conditions.All(
                        Conditions.TargetMatchesFilter(
                            GameObjectFilter.Creature.youControl(), targetIndex = 0
                        ),
                        Conditions.TargetMatchesFilter(
                            GameObjectFilter.Creature.opponentControls(), targetIndex = 1
                        )
                    ),
                    effect = Effects.Fight(yourCreature, theirCreature)
                )
            )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "111"
        artist = "Arif Wijaya"
        imageUri = "https://cards.scryfall.io/normal/front/1/6/16f0ef01-aa78-4ea3-b1ae-cc9594196425.jpg?1757547412"
    }
}
