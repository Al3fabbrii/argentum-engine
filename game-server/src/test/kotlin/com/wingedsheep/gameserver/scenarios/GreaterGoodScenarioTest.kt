package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Scenario tests for Greater Good ({2}{G}{G}, Enchantment).
 *
 * Sacrifice a creature: Draw cards equal to the sacrificed creature's power, then
 * discard three cards.
 *
 * Regression: while the ability sat on the stack, the rendered oracle text used
 * to read "Draw 0 cards" because the activated-ability-on-stack component's
 * sacrificed-permanent snapshots weren't propagated into the EffectContext built
 * by `ClientStateTransformer.runtimeAbilityText`. The evaluator therefore
 * resolved `EntityReference.Sacrificed` to no entity and fell through to 0.
 */
class GreaterGoodScenarioTest : ScenarioTestBase() {

    init {
        context("Greater Good activated ability") {
            test("stack text shows actual sacrificed creature's power, not 0") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Greater Good")
                    .withCardOnBattlefield(1, "Thundering Wurm") // 4/4
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Hill Giant")
                    .withCardInLibrary(1, "Python")
                    .withCardsInHand(1, "Hill Giant", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val greaterGoodId = game.findPermanent("Greater Good")!!
                val wurmId = game.findPermanent("Thundering Wurm")!!

                val cardDef = cardRegistry.getCard("Greater Good")!!
                val ability = cardDef.script.activatedAbilities.first()

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = greaterGoodId,
                        abilityId = ability.id,
                        costPayment = AdditionalCostPayment(
                            sacrificedPermanents = listOf(wurmId)
                        )
                    )
                )

                withClue("Activation should succeed: ${result.error}") {
                    result.error shouldBe null
                }

                val client = game.getClientState(1)
                val stackAbility = client.cards.values.firstOrNull {
                    it.zone?.zoneType == Zone.STACK && it.name.startsWith("Greater Good")
                } ?: error("Greater Good ability not found on stack")

                withClue("Stack text should reference Wurm's power (4), not 0") {
                    stackAbility.oracleText shouldContain "Draw 4 cards"
                }
            }
        }
    }
}
