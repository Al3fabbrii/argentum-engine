package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario test for Blue Sun's Twilight.
 *
 * Card reference:
 * - Blue Sun's Twilight ({X}{U}{U}): Sorcery
 *   "Gain control of target creature with mana value X or less.
 *    If X is 5 or more, create a token that's a copy of that creature."
 */
class BlueSunsTwilightScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    private fun TestGame.castBlueSunsTwilight(
        targetEntityId: com.wingedsheep.sdk.model.EntityId,
        xValue: Int
    ): com.wingedsheep.engine.core.ExecutionResult {
        val hand = state.getHand(player1Id)
        val cardId = hand.find { entityId ->
            state.getEntity(entityId)?.get<CardComponent>()?.name == "Blue Sun's Twilight"
        } ?: error("Blue Sun's Twilight not found in player 1's hand")
        val targets = listOf(ChosenTarget.Permanent(targetEntityId))
        return execute(CastSpell(player1Id, cardId, targets, xValue))
    }

    init {
        context("Blue Sun's Twilight basic effect") {
            test("X=2 steals an MV-2 creature permanently with no token created") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Blue Sun's Twilight")
                    .withLandsOnBattlefield(1, "Island", 4) // {X=2}{U}{U} = 4 mana
                    .withCardOnBattlefield(2, "Grizzly Bears") // MV 2
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Grizzly Bears")!!

                val castResult = game.castBlueSunsTwilight(target, xValue = 2)
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Player 1 should now control the stolen creature") {
                    projected.getController(target) shouldBe game.player1Id
                }

                // No token copy created (X < 5) — only the original Hill-Giant-less Grizzly Bears remains.
                val grizzliesOnBattlefield = game.state.getBattlefield().count { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Grizzly Bears"
                }
                withClue("No token copy should be created when X < 5; only the original Grizzly Bears exists") {
                    grizzliesOnBattlefield shouldBe 1
                }
            }

            test("X=2 cannot target an MV-4 creature (illegal target)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Blue Sun's Twilight")
                    .withLandsOnBattlefield(1, "Island", 4)
                    .withCardOnBattlefield(2, "Hill Giant") // MV 4
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Hill Giant")!!

                val castResult = game.castBlueSunsTwilight(target, xValue = 2)
                withClue("Cast should fail because Hill Giant's MV (4) > X (2)") {
                    castResult.error shouldNotBe null
                }
            }

            test("X=5 steals an MV-4 creature AND creates a token copy") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Blue Sun's Twilight")
                    .withLandsOnBattlefield(1, "Island", 7) // {X=5}{U}{U} = 7 mana
                    .withCardOnBattlefield(2, "Hill Giant") // MV 4
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Hill Giant")!!

                val castResult = game.castBlueSunsTwilight(target, xValue = 5)
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                game.resolveStack()

                val projected = stateProjector.project(game.state)
                withClue("Player 1 should control the stolen Hill Giant") {
                    projected.getController(target) shouldBe game.player1Id
                }

                // Original Hill Giant (still in Player 2's battlefield zone, controlled by Player 1
                // via projected state) plus a token copy on Player 1's side.
                val hillGiants = game.state.getBattlefield().filter { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Hill Giant"
                }
                withClue("Original stolen Hill Giant + token copy = 2 Hill Giants on the battlefield") {
                    hillGiants shouldHaveSize 2
                }
                val hillGiantControllers = hillGiants.map { projected.getController(it) }.toSet()
                withClue("Both Hill Giants should be controlled by Player 1") {
                    hillGiantControllers shouldBe setOf(game.player1Id)
                }
            }
        }

        context("Blue Sun's Twilight target validation") {
            test("X=0 with no MV-0 creature in play has no legal target") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Blue Sun's Twilight")
                    .withLandsOnBattlefield(1, "Island", 2) // {X=0}{U}{U}
                    .withCardOnBattlefield(2, "Grizzly Bears") // MV 2
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val target = game.findPermanent("Grizzly Bears")!!

                val castResult = game.castBlueSunsTwilight(target, xValue = 0)
                withClue("Casting with X=0 against MV-2 creature should fail") {
                    castResult.error shouldNotBe null
                }
            }
        }
    }
}
