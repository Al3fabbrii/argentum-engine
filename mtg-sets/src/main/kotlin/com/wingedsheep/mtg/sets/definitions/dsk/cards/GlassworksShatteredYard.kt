package com.wingedsheep.mtg.sets.definitions.dsk.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardLayout
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Glassworks // Shattered Yard (DSK 137) — split-layout Room (CR 709.5).
 *
 * Glassworks {2}{R} — Enchantment — Room
 *   When you unlock this door, this Room deals 4 damage to target creature an opponent controls.
 *
 * Shattered Yard {4}{R} — Enchantment — Room
 *   At the beginning of your end step, this Room deals 1 damage to each opponent.
 *
 * Cast each half separately; the cast face enters unlocked, the other locked. Pay the locked
 * face's printed mana cost as a sorcery-speed special action to unlock it (CR 709.5e). The
 * Glassworks door-unlock trigger ([Triggers.OnDoorUnlocked], CR 709.5h) targets a creature an
 * opponent controls and deals a fixed 4 damage; the Room itself is the damage source (the default
 * when no explicit `damageSource` is given to [Effects.DealDamage]). Shattered Yard is a
 * [Triggers.YourEndStep] pinger dealing 1 to [Player.EachOpponent] — the same fixed-damage
 * each-opponent shape as Intruding Soulrager / Grab the Prize.
 */
val GlassworksShatteredYard = card("Glassworks // Shattered Yard") {
    layout = CardLayout.SPLIT
    colorIdentity = "R"

    face("Glassworks") {
        manaCost = "{2}{R}"
        typeLine = "Enchantment — Room"
        oracleText = "When you unlock this door, this Room deals 4 damage to target creature an opponent controls."

        triggeredAbility {
            trigger = Triggers.OnDoorUnlocked
            target = Targets.CreatureOpponentControls
            effect = Effects.DealDamage(4, EffectTarget.ContextTarget(0))
            description = "When you unlock this door, this Room deals 4 damage to target creature an opponent controls."
        }
    }

    face("Shattered Yard") {
        manaCost = "{4}{R}"
        typeLine = "Enchantment — Room"
        oracleText = "At the beginning of your end step, this Room deals 1 damage to each opponent."

        triggeredAbility {
            trigger = Triggers.YourEndStep
            effect = Effects.DealDamage(1, EffectTarget.PlayerRef(Player.EachOpponent))
            description = "At the beginning of your end step, this Room deals 1 damage to each opponent."
        }
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "137"
        artist = "Sergey Glushakov"
        imageUri = "https://cards.scryfall.io/normal/front/f/e/fe32f667-8d9f-4414-913e-256cbc2fbc45.jpg?1726780646"

        ruling("2024-09-20", "To cast a Room spell, choose a half (or \"door\") to cast. There's no way to cast both halves of a Room card. When the Room spell resolves, the corresponding door becomes unlocked as the Room enters.")
        ruling("2024-09-20", "An ability that triggers \"when you unlock this door\" triggers when that door becomes unlocked, either on the battlefield or as the Room enters because you cast the corresponding half.")
        ruling("2024-09-20", "Some doors have abilities that trigger whenever you unlock that door and require one or more targets. You can unlock that door even if there would be insufficient legal targets for that triggered ability. The triggered ability won't go on the stack.")
        ruling("2024-09-20", "If a Room enters from any zone other than the stack, it will enter with both halves locked.")
    }
}
