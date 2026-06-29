package com.wingedsheep.mtg.sets.definitions.tla.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.DrawCardsEffect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.MoveType
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Sokka, Bold Boomeranger — Avatar: The Last Airbender #240
 * {U}{R} · Legendary Creature — Human Warrior Ally · Rare
 * 1/1
 *
 * When Sokka enters, discard up to two cards, then draw that many cards.
 * Whenever you cast an artifact or Lesson spell, put a +1/+1 counter on Sokka.
 */
val SokkaBoldBoomeranger = card("Sokka, Bold Boomeranger") {
    manaCost = "{U}{R}"
    colorIdentity = "UR"
    typeLine = "Legendary Creature — Human Warrior Ally"
    power = 1
    toughness = 1
    oracleText = "When Sokka enters, discard up to two cards, then draw that many cards.\n" +
        "Whenever you cast an artifact or Lesson spell, put a +1/+1 counter on Sokka."

    // ETB loot: discard up to two, then draw that many. The draw count reads the actual number
    // discarded via the pipeline collection's `_count` (declining discards draws zero).
    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.Composite(
            listOf(
                GatherCardsEffect(
                    source = CardSource.FromZone(Zone.HAND, Player.You),
                    storeAs = "hand"
                ),
                SelectFromCollectionEffect(
                    from = "hand",
                    selection = SelectionMode.ChooseUpTo(DynamicAmount.Fixed(2)),
                    storeSelected = "discarded",
                    prompt = "Discard up to two cards"
                ),
                MoveCollectionEffect(
                    from = "discarded",
                    destination = CardDestination.ToZone(Zone.GRAVEYARD, Player.You),
                    moveType = MoveType.Discard
                ),
                DrawCardsEffect(DynamicAmount.VariableReference("discarded_count"))
            )
        )
        description = "When Sokka enters, discard up to two cards, then draw that many cards."
    }

    triggeredAbility {
        trigger = Triggers.youCastSpell(
            spellFilter = GameObjectFilter.Artifact or GameObjectFilter.Any.withSubtype(Subtype.LESSON)
        )
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
        description = "Whenever you cast an artifact or Lesson spell, put a +1/+1 counter on Sokka."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "240"
        artist = "Toni Infante"
        flavorText = "\"I'm just a guy with a boomerang!\""
        imageUri = "https://cards.scryfall.io/normal/front/4/a/4a1f1472-55b4-450d-8e4d-7297130a0cf3.jpg?1764121775"
    }
}
