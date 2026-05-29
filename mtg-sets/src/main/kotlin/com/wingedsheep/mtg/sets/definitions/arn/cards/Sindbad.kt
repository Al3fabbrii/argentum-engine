package com.wingedsheep.mtg.sets.definitions.arn.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter

/**
 * Sindbad
 * {1}{U}
 * Creature — Human
 * 1/1
 * {T}: Draw a card and reveal it. If it isn't a land card, discard it.
 */
val Sindbad = card("Sindbad") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Human"
    power = 1
    toughness = 1
    oracleText = "{T}: Draw a card and reveal it. If it isn't a land card, discard it."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.DrawRevealDiscardUnless(GameObjectFilter.Land)
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "21"
        artist = "Julie Baroh"
        imageUri = "https://cards.scryfall.io/normal/front/7/b/7b112a10-ac40-4353-bbdd-e5efd4546330.jpg?1562917732"
    }
}
