package com.wingedsheep.mtg.sets.definitions.om1.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.OptionalCostEffect
import com.wingedsheep.sdk.scripting.effects.SacrificeSelfEffect
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.effects.TapUntapCollectionEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

val RentIsDue = card("Rent Is Due") {
    manaCost = "{W}"
    colorIdentity = "W"
    typeLine = "Enchantment"
    oracleText = "At the beginning of your upkeep, tap two untapped creatures and/or Treasures you control. If you do, draw a card. Otherwise, sacrifice Rent Is Due."

    triggeredAbility {
        trigger = Triggers.YourUpkeep
        val tapCost = CompositeEffect(listOf(
            GatherCardsEffect(
                source = CardSource.ControlledPermanents(
                    player = Player.You,
                    filter = (GameObjectFilter.Creature or GameObjectFilter.Artifact.withSubtype("Treasure")).untapped()
                ),
                storeAs = "rentTargets"
            ),
            SelectFromCollectionEffect(
                from = "rentTargets",
                selection = SelectionMode.ChooseExactly(DynamicAmount.Fixed(2)),
                storeSelected = "toTap",
                prompt = "Tap two untapped creatures and/or Treasures you control",
                useTargetingUI = true
            ),
            TapUntapCollectionEffect("toTap", tap = true)
        ))
        effect = OptionalCostEffect(
            cost = tapCost,
            ifPaid = Effects.DrawCards(1),
            ifNotPaid = SacrificeSelfEffect,
            descriptionOverride = "Tap two untapped creatures and/or Treasures you control. If you do, draw a card. Otherwise, sacrifice Rent Is Due."
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "6"
        artist = "Campbell White"
        imageUri = "https://cards.scryfall.io/normal/front/d/7/d70c0e56-4f97-486a-8345-0cc6c51e3e36.jpg?1757542853"
    }
}
