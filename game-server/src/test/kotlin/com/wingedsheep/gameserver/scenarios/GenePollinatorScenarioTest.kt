package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Gene Pollinator.
 *
 * Card reference:
 * - Gene Pollinator {G} — Artifact Creature — Robot Insect 1/2
 *   {T}, Tap an untapped permanent you control: Add one mana of any color.
 *
 * Regression: previously the cost defaulted to `GameObjectFilter.Creature`, which prevented
 * tapping non-creature permanents (lands, artifacts, etc.) — wrong for Gene Pollinator.
 */
class GenePollinatorScenarioTest : ScenarioTestBase() {

    init {
        context("Gene Pollinator tap-an-untapped-permanent ability") {

            test("can tap another creature you control to add one mana of any color") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Gene Pollinator")
                    .withCardOnBattlefield(1, "Elvish Warrior")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val pollinator = game.findPermanent("Gene Pollinator")!!
                val warrior = game.findPermanent("Elvish Warrior")!!

                val cardDef = cardRegistry.getCard("Gene Pollinator")!!
                val ability = cardDef.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = pollinator,
                        abilityId = ability.id,
                        costPayment = AdditionalCostPayment(tappedPermanents = listOf(warrior)),
                        manaColorChoice = Color.BLUE
                    )
                )

                withClue("Ability should activate without error: ${result.error}") {
                    result.error shouldBe null
                }
                withClue("Gene Pollinator should be tapped (from Costs.Tap)") {
                    game.state.getEntity(pollinator)?.has<TappedComponent>() shouldBe true
                }
                withClue("Tapped creature should be tapped (from Costs.TapAnotherPermanent)") {
                    game.state.getEntity(warrior)?.has<TappedComponent>() shouldBe true
                }
                val manaPool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()!!
                withClue("Should have 1 blue mana") {
                    manaPool.blue shouldBe 1
                }
            }

            test("can tap a non-creature permanent (a land) you control") {
                // Regression: the previous Creature-only filter rejected this activation.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Gene Pollinator")
                    .withCardOnBattlefield(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val pollinator = game.findPermanent("Gene Pollinator")!!
                val forest = game.findPermanent("Forest")!!

                val cardDef = cardRegistry.getCard("Gene Pollinator")!!
                val ability = cardDef.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = pollinator,
                        abilityId = ability.id,
                        costPayment = AdditionalCostPayment(tappedPermanents = listOf(forest)),
                        manaColorChoice = Color.RED
                    )
                )

                withClue("Should be able to tap a non-creature permanent: ${result.error}") {
                    result.error shouldBe null
                }
                withClue("Gene Pollinator should be tapped") {
                    game.state.getEntity(pollinator)?.has<TappedComponent>() shouldBe true
                }
                withClue("Forest should be tapped to pay the cost") {
                    game.state.getEntity(forest)?.has<TappedComponent>() shouldBe true
                }
                val manaPool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()!!
                withClue("Should have 1 red mana from Gene Pollinator's any-color effect") {
                    manaPool.red shouldBe 1
                }
            }

            test("cannot activate without another untapped permanent to tap") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Gene Pollinator")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val pollinator = game.findPermanent("Gene Pollinator")!!

                val cardDef = cardRegistry.getCard("Gene Pollinator")!!
                val ability = cardDef.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = pollinator,
                        abilityId = ability.id,
                        costPayment = AdditionalCostPayment(tappedPermanents = emptyList()),
                        manaColorChoice = Color.GREEN
                    )
                )

                withClue("Activation should fail: Gene Pollinator can't tap itself for the 'another' cost") {
                    result.error shouldNotBe null
                }
            }
        }
    }
}
