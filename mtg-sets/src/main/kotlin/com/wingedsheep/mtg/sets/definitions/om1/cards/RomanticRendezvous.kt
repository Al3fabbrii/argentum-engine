package com.wingedsheep.mtg.sets.definitions.om1.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

val RomanticRendezvous = card("Romantic Rendezvous") {
    manaCost = "{1}{R}"
    colorIdentity = "R"
    typeLine = "Sorcery"
    oracleText = "Discard a card, then draw two cards."

    spell {
        effect = Effects.Discard() then Effects.DrawCards(2)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "93"
        artist = "Mariah Tekulve"
        imageUri = "https://cards.scryfall.io/normal/front/d/1/d18dc61a-377e-4b84-9fc5-6496e21c9346.jpg?1757547850"
    }
}
