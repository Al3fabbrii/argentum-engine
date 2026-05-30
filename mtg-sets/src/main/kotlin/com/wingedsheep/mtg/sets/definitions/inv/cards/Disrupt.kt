package com.wingedsheep.mtg.sets.definitions.inv.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Disrupt
 * {U}
 * Instant
 *
 * Counter target instant or sorcery spell unless its controller pays {1}.
 * Draw a card.
 */
val Disrupt = card("Disrupt") {
    manaCost = "{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Counter target instant or sorcery spell unless its controller pays {1}.\nDraw a card."

    spell {
        target = Targets.InstantOrSorcerySpell
        effect = Effects.CounterUnlessPays("{1}") then Effects.DrawCards(1)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "51"
        artist = "Paolo Parente"
        imageUri = "https://cards.scryfall.io/normal/front/c/0/c000a02f-6b7e-4925-a938-59e645e980d7.jpg?1562933600"
    }
}
