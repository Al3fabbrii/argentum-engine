package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Phyrexian Delver.
 *
 * Phyrexian Delver: {3}{B}{B}
 * Creature — Phyrexian Zombie 3/2
 * When this creature enters, return target creature card from your graveyard to the
 * battlefield. You lose life equal to that card's mana value.
 */
class PhyrexianDelverScenarioTest : ScenarioTestBase() {

    init {
        context("Phyrexian Delver enters-the-battlefield trigger") {

            test("reanimates a graveyard creature and loses life equal to its mana value") {
                // Hill Giant is {3}{R} — mana value 4.
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Phyrexian Delver")
                    .withLandsOnBattlefield(1, "Swamp", 5)
                    .withCardInGraveyard(1, "Hill Giant")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Phyrexian Delver")

                // Resolve the creature spell; the ETB trigger then asks for a target.
                while (game.state.stack.isNotEmpty() && !game.hasPendingDecision()) {
                    game.passPriority()
                }

                val targetDecision = game.getPendingDecision()
                targetDecision.shouldBeInstanceOf<ChooseTargetsDecision>()

                val hillGiant = game.findCardsInGraveyard(1, "Hill Giant").single()
                game.selectTargets(listOf(hillGiant))

                // Resolve the trigger.
                while (game.state.stack.isNotEmpty() && !game.hasPendingDecision()) {
                    game.passPriority()
                }

                withClue("Hill Giant should be returned to the battlefield") {
                    game.isOnBattlefield("Hill Giant") shouldBe true
                    game.graveyardSize(1) shouldBe 0
                }
                withClue("Controller loses life equal to Hill Giant's mana value (4)") {
                    game.getLifeTotal(1) shouldBe 16
                }
            }

            test("loses no life when no creature is returned (no valid target)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Phyrexian Delver")
                    .withLandsOnBattlefield(1, "Swamp", 5)
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Phyrexian Delver")
                game.resolveStack()

                withClue("Phyrexian Delver resolves with an empty graveyard; no life lost") {
                    game.isOnBattlefield("Phyrexian Delver") shouldBe true
                    game.getLifeTotal(1) shouldBe 20
                }
            }
        }
    }
}
