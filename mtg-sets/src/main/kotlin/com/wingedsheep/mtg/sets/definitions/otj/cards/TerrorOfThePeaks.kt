package com.wingedsheep.mtg.sets.definitions.otj.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.SpellTargetingLifeCost
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.EntityNumericProperty
import com.wingedsheep.sdk.scripting.values.EntityReference

val TerrorOfThePeaks = card("Terror of the Peaks") {
    manaCost = "{3}{R}{R}"
    colorIdentity = "R"
    typeLine = "Creature — Dragon"
    power = 5
    toughness = 4
    oracleText = "Flying\nSpells your opponents cast that target this creature cost an additional 3 life to cast.\nWhenever another creature you control enters, this creature deals damage equal to that creature's power to any target."

    keywords(Keyword.FLYING)

    // Spells your opponents cast that target this creature cost an additional 3 life to cast.
    staticAbility {
        ability = SpellTargetingLifeCost(3)
    }

    // Whenever another creature you control enters, this creature deals damage equal to
    // that creature's power to any target.
    triggeredAbility {
        trigger = Triggers.OtherCreatureEnters
        val anyTarget = target("any target", Targets.Any)
        effect = Effects.DealDamage(
            DynamicAmount.EntityProperty(EntityReference.Triggering, EntityNumericProperty.Power),
            anyTarget
        )
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "149"
        artist = "Joshua Raphael"
        imageUri = "https://cards.scryfall.io/normal/front/9/0/904ff94a-4db4-44a6-8593-89c32905b3fc.jpg?1712355862"
    }
}
