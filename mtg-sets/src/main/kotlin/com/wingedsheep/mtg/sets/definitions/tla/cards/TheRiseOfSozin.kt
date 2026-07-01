package com.wingedsheep.mtg.sets.definitions.tla.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.firebending
import com.wingedsheep.sdk.dsl.namedFromVariable
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.MayPayXForEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.effects.SelectionRestriction
import com.wingedsheep.sdk.scripting.effects.ShuffleLibraryEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetOpponent
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * The Rise of Sozin // Fire Lord Sozin (TLA #117)
 * {4}{B}{B} — Enchantment — Saga
 * //  — Legendary Creature — Human Noble 5/5
 *
 * Front — The Rise of Sozin:
 *   (As this Saga enters and after your draw step, add a lore counter.)
 *   I — Destroy all creatures.
 *   II — Choose a card name. Search target opponent's graveyard, hand, and library for up to four
 *        cards with that name and exile them. Then that player shuffles.
 *   III — Exile this Saga, then return it to the battlefield transformed under your control.
 *
 * Back — Fire Lord Sozin:
 *   Menace, firebending 3 (Whenever this creature attacks, add {R}{R}{R}. This mana lasts until end
 *   of combat.)
 *   Whenever Fire Lord Sozin deals combat damage to a player, you may pay {X}. When you do, put any
 *   number of target creature cards with total mana value X or less from that player's graveyard onto
 *   the battlefield under your control.
 *
 * Chapter I is the standard board wipe ([Effects.DestroyAll] over [GameObjectFilter.Creature]).
 * Chapter II is the "name a card, then multi-zone search" family (Lobotomy / Desperate Research):
 * [Effects.ChooseCardName] records a free-typed name, a [CardSource.FromMultipleZones] gather over
 * the target opponent's graveyard/hand/library filtered by [GameObjectFilter.namedFromVariable]
 * finds every match, a `ChooseUpTo(4)` select caps it at four, the picks are exiled, and the opponent
 * shuffles. Chapter III is the standard transforming-Saga final chapter
 * ([Effects.ExileAndReturnTransformed], as on The Legend of Roku / Kuruk).
 *
 * The back face reuses [firebending] (the display keyword + attack-triggered "add {R}{R}{R} until end
 * of combat") and [Keyword.MENACE], plus a pay-{X} reflexive reanimation: [MayPayXForEffect] gates the
 * follow-up on paying X, then a gather → `ChooseAnyNumber` select (capped by the *dynamic*
 * [SelectionRestriction.TotalManaValueAtMost] `maxAmount = DynamicAmount.XValue`, i.e. total mana value
 * X or less) → move-to-battlefield reanimates the chosen creature cards from the damaged player's
 * ([Player.TriggeringPlayer]) graveyard under your control ([CardDestination.ToZone] battlefield sets
 * the controller to you while ownership stays with the opponent).
 */
private val FireLordSozin = card("Fire Lord Sozin") {
    manaCost = ""
    colorIdentity = "B"
    typeLine = "Legendary Creature — Human Noble"
    oracleText = "Menace, firebending 3 (Whenever this creature attacks, add {R}{R}{R}. This mana " +
        "lasts until end of combat.)\n" +
        "Whenever Fire Lord Sozin deals combat damage to a player, you may pay {X}. When you do, put " +
        "any number of target creature cards with total mana value X or less from that player's " +
        "graveyard onto the battlefield under your control."
    power = 5
    toughness = 5

    keywords(Keyword.MENACE)
    firebending(3)

    // Whenever Fire Lord Sozin deals combat damage to a player, you may pay {X}. When you do, put
    // any number of target creature cards with total mana value X or less from that player's
    // graveyard onto the battlefield under your control.
    triggeredAbility {
        trigger = Triggers.DealsCombatDamageToPlayer
        effect = MayPayXForEffect(
            effect = Effects.Composite(
                listOf(
                    // Gather the damaged player's graveyard creature cards.
                    GatherCardsEffect(
                        source = CardSource.FromZone(
                            zone = Zone.GRAVEYARD,
                            player = Player.TriggeringPlayer,
                            filter = GameObjectFilter.Creature
                        ),
                        storeAs = "sozinReanimate"
                    ),
                    // Choose any number of them with total mana value X or less.
                    SelectFromCollectionEffect(
                        from = "sozinReanimate",
                        selection = SelectionMode.ChooseAnyNumber,
                        restrictions = listOf(
                            SelectionRestriction.TotalManaValueAtMost(maxAmount = DynamicAmount.XValue)
                        ),
                        storeSelected = "sozinReanimated",
                        prompt = "Put any number of creature cards with total mana value X or less " +
                            "onto the battlefield under your control",
                        selectedLabel = "Put onto the battlefield"
                    ),
                    // Put them onto the battlefield under your control (owner stays the opponent).
                    MoveCollectionEffect(
                        from = "sozinReanimated",
                        destination = CardDestination.ToZone(Zone.BATTLEFIELD, Player.You)
                    )
                )
            )
        )
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "117"
        artist = "Mitori"
        imageUri = "https://cards.scryfall.io/normal/back/1/4/14eadf46-90c2-4376-8183-6a922a60174d.jpg?1768466342"
    }
}

private val TheRiseOfSozinFront = card("The Rise of Sozin") {
    manaCost = "{4}{B}{B}"
    colorIdentity = "B"
    typeLine = "Enchantment — Saga"
    oracleText = "(As this Saga enters and after your draw step, add a lore counter.)\n" +
        "I — Destroy all creatures.\n" +
        "II — Choose a card name. Search target opponent's graveyard, hand, and library for up to " +
        "four cards with that name and exile them. Then that player shuffles.\n" +
        "III — Exile this Saga, then return it to the battlefield transformed under your control."

    // I — Destroy all creatures.
    sagaChapter(1) {
        effect = Effects.DestroyAll(GameObjectFilter.Creature)
    }

    // II — Choose a card name. Search target opponent's graveyard, hand, and library for up to four
    // cards with that name and exile them. Then that player shuffles.
    sagaChapter(2) {
        target("target opponent", TargetOpponent())
        effect = Effects.Composite(
            listOf(
                // Choose a card name.
                Effects.ChooseCardName(
                    storeAs = "sozinChosenName",
                    prompt = "Choose a card name"
                ),
                // Search the target opponent's graveyard, hand, and library for cards with that name.
                GatherCardsEffect(
                    source = CardSource.FromMultipleZones(
                        zones = listOf(Zone.GRAVEYARD, Zone.HAND, Zone.LIBRARY),
                        player = Player.ContextPlayer(0),
                        filter = GameObjectFilter.Any.namedFromVariable("sozinChosenName")
                    ),
                    storeAs = "sozinMatches"
                ),
                // Up to four of them.
                SelectFromCollectionEffect(
                    from = "sozinMatches",
                    selection = SelectionMode.ChooseUpTo(DynamicAmount.Fixed(4)),
                    storeSelected = "sozinToExile",
                    prompt = "Choose up to four cards to exile",
                    selectedLabel = "Exile"
                ),
                // Exile them.
                MoveCollectionEffect(
                    from = "sozinToExile",
                    destination = CardDestination.ToZone(Zone.EXILE, Player.ContextPlayer(0))
                ),
                // Then that player shuffles.
                ShuffleLibraryEffect(target = EffectTarget.ContextTarget(0))
            )
        )
    }

    // III — Exile this Saga, then return it to the battlefield transformed under your control.
    sagaChapter(3) {
        effect = Effects.ExileAndReturnTransformed()
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "117"
        artist = "Mitori"
        imageUri = "https://cards.scryfall.io/normal/front/1/4/14eadf46-90c2-4376-8183-6a922a60174d.jpg?1768466342"
    }
}

val TheRiseOfSozin: CardDefinition = CardDefinition.doubleFacedPermanent(
    frontFace = TheRiseOfSozinFront,
    backFace = FireLordSozin,
)
