package com.wingedsheep.mtg.sets.definitions.bro.cards

import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.Rarity

/**
 * Bushwhack reprint in BRO.
 *
 * The canonical [com.wingedsheep.sdk.model.CardDefinition] (script, types, P/T) lives in
 * another set's `cards/` package. This file contributes only the BRO-specific
 * presentation row — set, collector number, art — picked up automatically by
 * `CardDiscovery.findPrintingsIn` and surfaced via the set's `printings`.
 */
val BushwhackReprint = Printing(
    oracleId = "c61374e5-a7f6-455e-a40b-a481751b536b",
    name = "Bushwhack",
    setCode = "BRO",
    collectorNumber = "174",
    scryfallId = "712a0640-d9c8-46fc-b38b-bf20a40fa902",
    artist = "Artur Nakhodkin",
    imageUri = "https://cards.scryfall.io/normal/front/7/1/712a0640-d9c8-46fc-b38b-bf20a40fa902.jpg?1674421510",
    releaseDate = "2022-11-18",
    rarity = Rarity.UNCOMMON,
)
