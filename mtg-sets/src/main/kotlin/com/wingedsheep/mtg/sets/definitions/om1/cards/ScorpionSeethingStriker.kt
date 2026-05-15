package com.wingedsheep.mtg.sets.definitions.om1.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.ConniveEffect

val ScorpionSeethingStriker = card("Scorpion, Seething Striker") {
    manaCost = "{3}{B}"
    colorIdentity = "B"
    typeLine = "Legendary Creature — Scorpion Human Villain"
    power = 3
    toughness = 3
    oracleText = "Deathtouch\nAt the beginning of your end step, if a creature died this turn, target creature you control connives. (Draw a card, then discard a card. If you discarded a nonland card, put a +1/+1 counter on that creature.)"

    keywords(Keyword.DEATHTOUCH)

    triggeredAbility {
        trigger = Triggers.YourEndStep
        triggerCondition = Conditions.CreatureDiedThisTurn
        val creature = target("target creature you control", Targets.CreatureYouControl)
        effect = ConniveEffect(target = creature)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "68"
        artist = "Dmitry Burmak"
        imageUri = "https://cards.scryfall.io/normal/front/0/a/0ac4c44a-d74b-47f3-91d9-bffc3cc4eaae.jpg?1757548052"
    }
}
