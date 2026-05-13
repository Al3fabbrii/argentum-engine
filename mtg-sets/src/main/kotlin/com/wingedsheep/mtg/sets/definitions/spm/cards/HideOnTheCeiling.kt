package com.wingedsheep.mtg.sets.definitions.spm.cards

import com.wingedsheep.sdk.dsl.EffectPatterns
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Hide on the Ceiling
 * {X}{U}
 * Instant
 * Exile X target artifacts and/or creatures. Return the exiled cards to the
 * battlefield under their owners' control at the beginning of the next end step.
 */
val HideOnTheCeiling = card("Hide on the Ceiling") {
    manaCost = "{X}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Exile X target artifacts and/or creatures. Return the exiled cards to the battlefield under their owners' control at the beginning of the next end step."

    spell {
        val target = target("target artifact or creature", Targets.CreatureOrArtifact)
        effect = EffectPatterns.exileUntilEndStep(target)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "32"
        artist = "Fariba Khamseh"
        imageUri = "https://cards.scryfall.io/normal/front/7/9/7977e448-01fa-4fa5-a275-0d6a1357b35c.jpg?1757376939"
    }
}
