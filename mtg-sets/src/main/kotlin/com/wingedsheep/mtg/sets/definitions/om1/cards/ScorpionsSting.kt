package com.wingedsheep.mtg.sets.definitions.om1.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

val ScorpionsSting = card("Scorpion's Sting") {
    manaCost = "{1}{B}"
    colorIdentity = "B"
    typeLine = "Instant"
    oracleText = "Target creature gets -3/-3 until end of turn."

    spell {
        val creature = target("target creature", Targets.Creature)
        effect = Effects.ModifyStats(-3, -3, creature)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "67"
        artist = "Inkognit"
        imageUri = "https://cards.scryfall.io/normal/front/2/b/2be7387a-4350-4c9e-8f5d-7716b903e476.jpg?1757548031"
    }
}
