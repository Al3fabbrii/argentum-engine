package com.wingedsheep.mtg.sets.definitions.om1.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

val ProwlerClawedThief = card("Prowler, Clawed Thief") {
    manaCost = "{1}{U}{B}"
    colorIdentity = "UB"
    typeLine = "Legendary Creature — Human Rogue Villain"
    oracleText = "Menace\nConnive — Whenever another Villain you control enters, Prowler, Clawed Thief connives."
    power = 2
    toughness = 3

    keywords(Keyword.MENACE)

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "128"
        artist = "Cristi Balanescu"
        imageUri = "https://cards.scryfall.io/normal/front/f/7/f7717ba8-c225-40ad-bd2e-282d0f43cdaf.jpg?1757542393"
    }
}
