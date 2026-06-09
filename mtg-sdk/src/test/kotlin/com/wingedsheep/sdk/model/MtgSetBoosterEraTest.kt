package com.wingedsheep.sdk.model

import com.wingedsheep.sdk.limited.PlayBooster
import com.wingedsheep.sdk.limited.StandardBooster
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.types.shouldBeInstanceOf

class MtgSetBoosterEraTest : DescribeSpec({

    fun setWithReleaseDate(date: String?): MtgSet = object : MtgSet {
        override val code = "TST"
        override val displayName = "Test Set"
        override val releaseDate = date
        override val cards: List<CardDefinition> = emptyList()
    }

    describe("MtgSet.boosterStrategy era default") {

        it("uses the classic 15-card booster for pre-2024 sets") {
            setWithReleaseDate("1997-05-01").boosterStrategy.shouldBeInstanceOf<StandardBooster>()
            setWithReleaseDate("2023-11-17").boosterStrategy.shouldBeInstanceOf<StandardBooster>()
        }

        it("uses the Play Booster from Murders at Karlov Manor onward") {
            setWithReleaseDate(MtgSet.PLAY_BOOSTER_ERA_START).boosterStrategy.shouldBeInstanceOf<PlayBooster>()
            setWithReleaseDate("2024-08-02").boosterStrategy.shouldBeInstanceOf<PlayBooster>()
            setWithReleaseDate("2026-01-23").boosterStrategy.shouldBeInstanceOf<PlayBooster>()
        }

        it("falls back to the classic booster when the release date is unknown") {
            setWithReleaseDate(null).boosterStrategy.shouldBeInstanceOf<StandardBooster>()
        }
    }
})
