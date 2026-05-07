package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Deepway Navigator.
 *
 * Card reference:
 * - Deepway Navigator ({W}{U}): 2/2 Creature — Merfolk Wizard
 *   Flash
 *   When this creature enters, untap each other Merfolk you control.
 *   As long as you attacked with three or more Merfolk this turn,
 *   Merfolk you control get +1/+0.
 */
class DeepwayNavigatorScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Deepway Navigator ETB untap") {
            test("untaps each other Merfolk you control") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Merfolk of the Pearl Trident", tapped = true)
                    .withCardOnBattlefield(1, "Merfolk of the Pearl Trident", tapped = true)
                    .withCardInHand(1, "Deepway Navigator")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withLandsOnBattlefield(1, "Island", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Deepway Navigator")
                game.resolveStack()

                val merfolkIds = game.state.getBattlefield().filter { id ->
                    game.state.getEntity(id)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()
                        ?.name == "Merfolk of the Pearl Trident"
                }
                withClue("Both other Merfolk should be untapped after ETB trigger resolves") {
                    merfolkIds.size shouldBe 2
                    merfolkIds.all { game.state.getEntity(it)?.has<TappedComponent>() == false } shouldBe true
                }

                val navigatorId = game.findPermanent("Deepway Navigator")!!
                withClue("Deepway Navigator itself should not be untapped (it entered untapped, but the trigger excludes self)") {
                    game.state.getEntity(navigatorId)?.has<TappedComponent>() shouldBe false
                }
            }
        }

        context("Deepway Navigator conditional lord effect") {
            test("Merfolk get +1/+0 only after attacking with three or more Merfolk") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Deepway Navigator")
                    .withCardOnBattlefield(1, "Merfolk of the Pearl Trident")
                    .withCardOnBattlefield(1, "Merfolk of the Pearl Trident")
                    .withCardOnBattlefield(1, "Merfolk of the Pearl Trident")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val merfolkIds = game.state.getBattlefield().filter { id ->
                    game.state.getEntity(id)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()
                        ?.name == "Merfolk of the Pearl Trident"
                }
                merfolkIds.size shouldBe 3

                withClue("Before any attack: Merfolk of the Pearl Trident is base 1/1") {
                    val projected = stateProjector.project(game.state)
                    merfolkIds.forEach { id ->
                        projected.getPower(id) shouldBe 1
                        projected.getToughness(id) shouldBe 1
                    }
                }

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                val opponentId = game.state.turnOrder.first { it != game.state.activePlayerId }
                game.execute(DeclareAttackers(
                    playerId = game.state.activePlayerId!!,
                    attackers = mapOf(
                        merfolkIds[0] to opponentId,
                        merfolkIds[1] to opponentId,
                        merfolkIds[2] to opponentId,
                    )
                ))

                withClue("After attacking with three Merfolk: lord buff is active, +1/+0") {
                    val projected = stateProjector.project(game.state)
                    merfolkIds.forEach { id ->
                        projected.getPower(id) shouldBe 2
                        projected.getToughness(id) shouldBe 1
                    }
                    val navigatorId = game.findPermanent("Deepway Navigator")!!
                    projected.getPower(navigatorId) shouldBe 3
                    projected.getToughness(navigatorId) shouldBe 2
                }
            }

            test("buff does not apply when fewer than three Merfolk attacked") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Deepway Navigator")
                    .withCardOnBattlefield(1, "Merfolk of the Pearl Trident")
                    .withCardOnBattlefield(1, "Merfolk of the Pearl Trident")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val merfolkIds = game.state.getBattlefield().filter { id ->
                    game.state.getEntity(id)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()
                        ?.name == "Merfolk of the Pearl Trident"
                }

                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                val opponentId = game.state.turnOrder.first { it != game.state.activePlayerId }
                // Attack with only 2 Merfolk (Navigator stays back) — under the threshold.
                game.execute(DeclareAttackers(
                    playerId = game.state.activePlayerId!!,
                    attackers = mapOf(
                        merfolkIds[0] to opponentId,
                        merfolkIds[1] to opponentId,
                    )
                ))

                withClue("After attacking with only two Merfolk: buff is NOT active") {
                    val projected = stateProjector.project(game.state)
                    merfolkIds.forEach { id ->
                        projected.getPower(id) shouldBe 1
                        projected.getToughness(id) shouldBe 1
                    }
                }
            }
        }
    }
}
