package com.wingedsheep.mtg.sets.definitions.inv.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.conditions.WasKicked
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Llanowar Elite
 * {G}
 * Creature — Elf
 * 1/1
 * Kicker {8}
 * Trample
 * If this creature was kicked, it enters with five +1/+1 counters on it.
 */
val LlanowarElite = card("Llanowar Elite") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elf"
    power = 1
    toughness = 1
    oracleText = "Kicker {8} (You may pay an additional {8} as you cast this spell.)\n" +
        "Trample\n" +
        "If this creature was kicked, it enters with five +1/+1 counters on it."

    keywordAbility(KeywordAbility.kicker("{8}"))
    keywords(Keyword.TRAMPLE)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        triggerCondition = WasKicked
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 5, EffectTarget.Self)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "196"
        artist = "Kev Walker"
        imageUri = "https://cards.scryfall.io/normal/front/3/e/3e207863-de68-47e1-8c63-413b5fa48943.jpg?1562907508"
    }
}
