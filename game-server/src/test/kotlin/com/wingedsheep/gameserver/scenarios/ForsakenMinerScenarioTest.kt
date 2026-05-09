package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Forsaken Miner.
 *
 * Forsaken Miner: {B}
 * Creature — Skeleton Rogue (2/2)
 * This creature can't block.
 * Whenever you commit a crime, you may pay {B}. If you do, return this card from your
 * graveyard to the battlefield.
 */
class ForsakenMinerScenarioTest : ScenarioTestBase() {

    init {
        context("Forsaken Miner crime trigger") {

            test("returns from graveyard when controller commits a crime and pays") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInGraveyard(1, "Forsaken Miner")
                    .withCardInHand(1, "Shock")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Casting Shock at the opponent is a crime — they're an opponent.
                game.castSpellTargetingPlayer(1, "Shock", targetPlayerNumber = 2)

                // The crime trigger lands on the stack on top of Shock. Resolve the trigger.
                game.resolveStack()

                // The trigger asks "may pay {B}?" Pay it.
                game.answerYesNo(true)
                game.submitManaSourcesAutoPay()

                withClue("Forsaken Miner should return to the battlefield") {
                    game.isOnBattlefield("Forsaken Miner") shouldBe true
                }
                withClue("Forsaken Miner should leave the graveyard") {
                    game.isInGraveyard(1, "Forsaken Miner") shouldBe false
                }

                // Resolve Shock so the test ends with an empty stack.
                game.resolveStack()
            }

            test("stays in graveyard when controller declines to pay") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInGraveyard(1, "Forsaken Miner")
                    .withCardInHand(1, "Shock")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellTargetingPlayer(1, "Shock", targetPlayerNumber = 2)
                game.resolveStack()
                game.answerYesNo(false)

                withClue("Forsaken Miner should stay in the graveyard") {
                    game.isInGraveyard(1, "Forsaken Miner") shouldBe true
                }
                withClue("Forsaken Miner should not be on the battlefield") {
                    game.isOnBattlefield("Forsaken Miner") shouldBe false
                }
            }

            test("does not trigger when the cast spell targets only the controller themselves") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInGraveyard(1, "Forsaken Miner")
                    .withCardInHand(1, "Shock")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLandsOnBattlefield(1, "Swamp", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Targeting yourself is not a crime — only opponents/their stuff are crime targets.
                game.castSpellTargetingPlayer(1, "Shock", targetPlayerNumber = 1)
                game.resolveStack()

                withClue("Forsaken Miner should still be in the graveyard (no crime, no trigger)") {
                    game.isInGraveyard(1, "Forsaken Miner") shouldBe true
                }
                withClue("Forsaken Miner should not be on the battlefield") {
                    game.isOnBattlefield("Forsaken Miner") shouldBe false
                }
            }
        }
    }
}
