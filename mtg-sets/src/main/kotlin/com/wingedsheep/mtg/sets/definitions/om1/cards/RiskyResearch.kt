package com.wingedsheep.mtg.sets.definitions.om1.cards

import com.wingedsheep.sdk.dsl.EffectPatterns
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val RiskyResearch = card("Risky Research") {
    manaCost = "{2}{B}"
    colorIdentity = "B"
    typeLine = "Sorcery"
    oracleText = "Surveil 2, then draw two cards, then you lose 2 life."

    spell {
        effect = EffectPatterns.surveil(2) then Effects.DrawCards(2) then Effects.LoseLife(2, EffectTarget.Controller)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "52"
        artist = "Will Gist"
        imageUri = "https://cards.scryfall.io/normal/front/b/0/b08238f7-9e00-4122-b196-05d623c84f8f.jpg?1757542775"
    }
}
