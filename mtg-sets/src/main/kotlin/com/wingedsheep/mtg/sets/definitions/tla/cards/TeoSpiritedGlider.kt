package com.wingedsheep.mtg.sets.definitions.tla.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

/**
 * Teo, Spirited Glider
 * {3}{U}
 * Legendary Creature — Human Pilot Ally
 * 1/4
 *
 * Flying
 * Whenever one or more creatures you control with flying attack, draw a card, then discard a
 * card. When you discard a nonland card this way, put a +1/+1 counter on target creature you
 * control.
 *
 * The looting + conditional reflexive +1/+1 counter is exactly connive (CR 702.166), modeled via
 * [Effects.Connive] with the counter recipient as the chosen target creature you control.
 */
val TeoSpiritedGlider = card("Teo, Spirited Glider") {
    manaCost = "{3}{U}"
    colorIdentity = "U"
    typeLine = "Legendary Creature — Human Pilot Ally"
    power = 1
    toughness = 4
    oracleText = "Flying\n" +
        "Whenever one or more creatures you control with flying attack, draw a card, then " +
        "discard a card. When you discard a nonland card this way, put a +1/+1 counter on " +
        "target creature you control."

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.YouAttackWithFilter(
            GameObjectFilter.Creature.youControl().withKeyword(Keyword.FLYING)
        )
        val creature = target("target creature you control", Targets.CreatureYouControl)
        effect = Effects.Connive(target = creature)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "74"
        artist = "Robin Har"
        imageUri = "https://cards.scryfall.io/normal/front/6/6/66906ed4-baac-4be0-9359-34f453d1a04a.jpg?1764120477"
    }
}
