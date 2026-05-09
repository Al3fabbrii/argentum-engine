package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.matchers.shouldBe

class ShootTheSheriffScenarioTest : ScenarioTestBase() {

    // Non-outlaw — Human Soldier
    private val deputy = CardDefinition.creature(
        name = "Loyal Deputy",
        manaCost = ManaCost.parse("{1}{W}"),
        subtypes = setOf(Subtype.HUMAN, Subtype.SOLDIER),
        power = 2, toughness = 2
    )

    // Outlaw — single-type Pirate
    private val pirate = CardDefinition.creature(
        name = "Sea Bandit",
        manaCost = ManaCost.parse("{1}{U}"),
        subtypes = setOf(Subtype.PIRATE),
        power = 2, toughness = 1
    )

    // Outlaw — multi-type with at least one outlaw subtype (Human Rogue)
    private val humanRogue = CardDefinition.creature(
        name = "Backstreet Cutpurse",
        manaCost = ManaCost.parse("{1}{B}"),
        subtypes = setOf(Subtype.HUMAN, Subtype.ROGUE),
        power = 2, toughness = 2
    )

    init {
        cardRegistry.register(deputy)
        cardRegistry.register(pirate)
        cardRegistry.register(humanRogue)

        context("Shoot the Sheriff") {
            test("destroys a non-outlaw creature") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Shoot the Sheriff")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardOnBattlefield(2, "Loyal Deputy")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Loyal Deputy")!!
                game.castSpell(1, "Shoot the Sheriff", target)
                game.resolveStack()

                game.isInGraveyard(2, "Loyal Deputy") shouldBe true
                game.isOnBattlefield("Loyal Deputy") shouldBe false
            }

            test("cannot target an outlaw (Pirate) creature") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Shoot the Sheriff")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardOnBattlefield(2, "Sea Bandit")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val pirateId = game.findPermanent("Sea Bandit")!!
                val result = game.castSpell(1, "Shoot the Sheriff", pirateId)
                (result.error != null) shouldBe true
                game.isOnBattlefield("Sea Bandit") shouldBe true
            }

            test("cannot target a multi-type creature that is also an outlaw (Human Rogue)") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Shoot the Sheriff")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withCardOnBattlefield(2, "Backstreet Cutpurse")
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val rogueId = game.findPermanent("Backstreet Cutpurse")!!
                val result = game.castSpell(1, "Shoot the Sheriff", rogueId)
                (result.error != null) shouldBe true
                game.isOnBattlefield("Backstreet Cutpurse") shouldBe true
            }
        }
    }
}
