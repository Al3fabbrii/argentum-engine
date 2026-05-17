package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario test for Polymorph.
 *
 * Card reference:
 * - Polymorph (3U): Sorcery
 *   "Destroy target creature. It can't be regenerated. Its controller reveals cards
 *    from the top of their library until they reveal a creature card. The player puts
 *    that card onto the battlefield, then shuffles all other cards revealed this way
 *    into their library."
 */
class PolymorphScenarioTest : ScenarioTestBase() {

    init {
        context("Polymorph") {
            test("destroys target creature and replaces it with the first creature revealed from its controller's library") {
                // P1 casts Polymorph on P2's Grizzly Bears.
                // P2's library top-down: Mountain, Forest, Hill Giant, Mountain.
                // P2 reveals until creature → Hill Giant. Hill Giant enters P2's battlefield.
                // The remaining revealed cards (Mountain, Forest) get shuffled into P2's library.
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Polymorph")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardInLibrary(2, "Mountain")
                    .withCardInLibrary(2, "Forest")
                    .withCardInLibrary(2, "Hill Giant")
                    .withCardInLibrary(2, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val initialLibrarySize = game.librarySize(2)

                val castResult = game.castSpell(1, "Polymorph", bears)
                withClue("Cast should succeed") {
                    castResult.error shouldBe null
                }

                game.resolveStack()

                withClue("Grizzly Bears destroyed and in opponent's graveyard") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                    game.isInGraveyard(2, "Grizzly Bears") shouldBe true
                }
                withClue("Hill Giant entered opponent's battlefield") {
                    game.isOnBattlefield("Hill Giant") shouldBe true
                }
                withClue("Opponent's library is smaller by one (Hill Giant moved out; revealed non-creatures shuffled back in)") {
                    game.librarySize(2) shouldBe initialLibrarySize - 1
                }
            }

            test("if no creature is in the library, the target is still destroyed and the library is shuffled") {
                // P2's library has only lands → no creature to put onto the battlefield.
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Polymorph")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withCardInLibrary(2, "Mountain")
                    .withCardInLibrary(2, "Forest")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val initialLibrarySize = game.librarySize(2)

                val castResult = game.castSpell(1, "Polymorph", bears)
                withClue("Cast should succeed") {
                    castResult.error shouldBe null
                }

                game.resolveStack()

                withClue("Grizzly Bears destroyed") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                    game.isInGraveyard(2, "Grizzly Bears") shouldBe true
                }
                withClue("Library is intact (no creature found, just shuffled)") {
                    game.librarySize(2) shouldBe initialLibrarySize
                }
            }
        }
    }
}
