package com.wingedsheep.mtg.sets.definitions.eoe.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AssignDamageEqualToToughness
import com.wingedsheep.sdk.scripting.StationUsingToughness
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter

/**
 * Tapestry Warden
 * {3}{G}
 * Artifact Creature — Robot Soldier
 * 3/4
 * Vigilance
 * Each creature you control with toughness greater than its power assigns combat damage
 * equal to its toughness rather than its power.
 * Each creature you control with toughness greater than its power stations permanents
 * using its toughness rather than its power.
 */
val TapestryWarden = card("Tapestry Warden") {
    manaCost = "{3}{G}"
    colorIdentity = "G"
    typeLine = "Artifact Creature — Robot Soldier"
    power = 3
    toughness = 4
    oracleText = "Vigilance\n" +
        "Each creature you control with toughness greater than its power assigns combat damage " +
        "equal to its toughness rather than its power.\n" +
        "Each creature you control with toughness greater than its power stations permanents " +
        "using its toughness rather than its power."

    keywords(Keyword.VIGILANCE)

    staticAbility {
        ability = AssignDamageEqualToToughness(
            filter = GroupFilter.AllCreaturesYouControl,
            onlyWhenToughnessGreaterThanPower = true,
        )
    }

    staticAbility {
        ability = StationUsingToughness(
            filter = GroupFilter.AllCreaturesYouControl,
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "209"
        artist = "Andreas Zafiratos"
        flavorText = "Drix never leave their worlds defenseless."
        imageUri = "https://cards.scryfall.io/normal/front/7/c/7cbbab6c-43ae-4e50-97ce-532a3316591a.jpg?1752947411"
    }
}
