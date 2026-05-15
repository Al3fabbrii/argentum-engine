package com.wingedsheep.mtg.sets.definitions.om1.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.CantBeBlockedByMoreThan

val ProfessionalWrestler = card("Professional Wrestler") {
    manaCost = "{3}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Human Warrior Performer"
    power = 4
    toughness = 4
    oracleText = "When this creature enters, create a Treasure token. (It's an artifact with \"{T}, Sacrifice this token: Add one mana of any color.\")\nThis creature can't be blocked by more than one creature."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.CreateTreasure(1)
    }

    staticAbility {
        ability = CantBeBlockedByMoreThan(maxBlockers = 1)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "106"
        artist = "Chris Mangum"
        imageUri = "https://cards.scryfall.io/normal/front/5/e/5e1bc145-8e4b-484a-bf29-4b46089d9300.jpg?1757544275"
    }
}
