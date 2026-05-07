package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Sunderflock.
 *
 * Card reference:
 * - Sunderflock ({7}{U}{U}): Creature — Elemental, 5/5
 *   "This spell costs {X} less to cast, where X is the greatest mana value among
 *    Elementals you control."
 *   "Flying"
 *   "When this creature enters, if you cast it, return all non-Elemental creatures
 *    to their owners' hands."
 */
class SunderflockScenarioTest : ScenarioTestBase() {

    private fun ScenarioBuilder.withLibraryCards(playerNumber: Int, cardName: String, count: Int): ScenarioBuilder {
        repeat(count) { withCardInLibrary(playerNumber, cardName) }
        return this
    }

    init {
        context("Sunderflock cost reduction") {

            test("costs are reduced by greatest mana value among controlled Elementals") {
                // Fire Elemental (MV 4) reduces {7}{U}{U} → {3}{U}{U} (5 mana total)
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Sunderflock")
                    .withCardOnBattlefield(1, "Fire Elemental")
                    .withLandsOnBattlefield(1, "Island", 5)
                    .withLibraryCards(1, "Island", 5)
                    .withLibraryCards(2, "Island", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Sunderflock")
                withClue("Casting Sunderflock with reduced cost should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
            }

            test("cannot pay full cost without Elementals on the battlefield") {
                // No Elementals → no reduction → 5 lands isn't enough for {7}{U}{U}
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Sunderflock")
                    .withLandsOnBattlefield(1, "Island", 5)
                    .withLibraryCards(1, "Island", 5)
                    .withLibraryCards(2, "Island", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Sunderflock")
                withClue("Should not be able to cast Sunderflock without enough mana") {
                    castResult.error shouldBe "Not enough mana to cast this spell"
                }
            }
        }

        context("Sunderflock ETB returns non-Elementals") {

            test("returns all non-Elemental creatures to their owners' hands when cast") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Sunderflock")
                    .withCardOnBattlefield(1, "Fire Elemental")          // Elemental — survives
                    .withCardOnBattlefield(1, "Glory Seeker")            // Human Soldier — bounced
                    .withCardOnBattlefield(2, "Enormous Baloth")         // Beast — bounced
                    .withLandsOnBattlefield(1, "Island", 9)
                    .withLibraryCards(1, "Island", 5)
                    .withLibraryCards(2, "Island", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val initialOpponentHandSize = game.handSize(2)

                val castResult = game.castSpell(1, "Sunderflock")
                withClue("Casting should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Sunderflock should be on the battlefield") {
                    game.isOnBattlefield("Sunderflock") shouldBe true
                }
                withClue("Fire Elemental should remain (Elemental)") {
                    game.isOnBattlefield("Fire Elemental") shouldBe true
                }
                withClue("Glory Seeker should be returned to its owner's hand") {
                    game.isOnBattlefield("Glory Seeker") shouldBe false
                    game.isInHand(1, "Glory Seeker") shouldBe true
                }
                withClue("Enormous Baloth should be returned to its owner's hand") {
                    game.isOnBattlefield("Enormous Baloth") shouldBe false
                    game.handSize(2) shouldBe initialOpponentHandSize + 1
                }
            }
        }
    }
}
