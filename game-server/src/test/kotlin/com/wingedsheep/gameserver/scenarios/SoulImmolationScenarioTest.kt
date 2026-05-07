package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Soul Immolation.
 *
 * Soul Immolation:
 * - {3}{R}{R} Sorcery
 * - "As an additional cost to cast this spell, blight X. X can't be greater than
 *    the greatest toughness among creatures you control. (Put X -1/-1 counters
 *    on a creature you control.)
 *    Soul Immolation deals X damage to each opponent and each creature they control."
 */
class SoulImmolationScenarioTest : ScenarioTestBase() {

    init {
        context("Soul Immolation blight-X variable additional cost") {
            test("Blighting 3 deals 3 damage to opponent and each creature they control") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Soul Immolation")
                    .withCardOnBattlefield(1, "Hill Giant") // 3/3 — caps X at 3
                    .withCardOnBattlefield(2, "Grizzly Bears") // 2/2
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3
                    .withLandsOnBattlefield(1, "Mountain", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val hand = game.state.getHand(game.player1Id)
                val cardId = hand.first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Soul Immolation"
                }
                val ownGiantId = game.state.getBattlefield().first { entityId ->
                    val container = game.state.getEntity(entityId) ?: return@first false
                    container.get<CardComponent>()?.name == "Hill Giant" &&
                        game.state.projectedState.getController(entityId) == game.player1Id
                }

                val payment = AdditionalCostPayment(
                    blightTargets = listOf(ownGiantId),
                    blightAmount = 3
                )
                val castResult = game.execute(
                    CastSpell(game.player1Id, cardId, additionalCostPayment = payment)
                )
                withClue("Soul Immolation should cast successfully: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                game.resolveStack()

                // Player 2 takes 3 damage (20 -> 17)
                game.getLifeTotal(2) shouldBe 17

                // Player 2's Grizzly Bears (2/2) takes 3 damage and dies
                withClue("Grizzly Bears should be dead from 3 damage") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                }

                // Player 2's Hill Giant (3/3) takes 3 damage and dies
                val p2GiantStillAlive = game.state.getBattlefield().any { entityId ->
                    val container = game.state.getEntity(entityId) ?: return@any false
                    container.get<CardComponent>()?.name == "Hill Giant" &&
                        game.state.projectedState.getController(entityId) == game.player2Id
                }
                withClue("Player 2's Hill Giant should be dead from 3 damage") {
                    p2GiantStillAlive shouldBe false
                }

                // Player 1's own Hill Giant survives (now 0/0 from -3/-3 counters → also dies as SBA)
                // Hill Giant is 3/3 with 3 -1/-1 counters → 0/0 → dies via SBA
                val p1GiantStillAlive = game.state.getBattlefield().any { it == ownGiantId }
                withClue("Player 1's Hill Giant should die from -3/-3 counters") {
                    p1GiantStillAlive shouldBe false
                }
            }

            test("Blighting 1 deals 1 damage to opponent and each creature they control") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Soul Immolation")
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2 — caps X at 2
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3
                    .withLandsOnBattlefield(1, "Mountain", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val hand = game.state.getHand(game.player1Id)
                val cardId = hand.first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Soul Immolation"
                }
                val ownBearsId = game.state.getBattlefield().first { entityId ->
                    val container = game.state.getEntity(entityId) ?: return@first false
                    container.get<CardComponent>()?.name == "Grizzly Bears" &&
                        game.state.projectedState.getController(entityId) == game.player1Id
                }

                val payment = AdditionalCostPayment(
                    blightTargets = listOf(ownBearsId),
                    blightAmount = 1
                )
                val castResult = game.execute(
                    CastSpell(game.player1Id, cardId, additionalCostPayment = payment)
                )
                withClue("Soul Immolation should cast successfully: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                game.resolveStack()

                // Player 2 takes 1 damage (20 -> 19)
                game.getLifeTotal(2) shouldBe 19

                // Player 2's Hill Giant (3/3) takes 1 damage, survives at 3/2
                val hillGiantId = game.findPermanent("Hill Giant")
                hillGiantId.shouldNotBeNull()
                val clientState = game.getClientState(2)
                val hillGiantInfo = clientState.cards[hillGiantId]
                withClue("Hill Giant should survive 1 damage") {
                    hillGiantInfo.shouldNotBeNull()
                }
            }

            test("Cannot blight more than the greatest toughness among creatures you control") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Soul Immolation")
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2 — caps X at 2
                    .withLandsOnBattlefield(1, "Mountain", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val hand = game.state.getHand(game.player1Id)
                val cardId = hand.first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Soul Immolation"
                }
                val bearsId = game.state.getBattlefield().first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Grizzly Bears"
                }

                val payment = AdditionalCostPayment(
                    blightTargets = listOf(bearsId),
                    blightAmount = 5 // greater than max toughness (2)
                )
                val castResult = game.execute(
                    CastSpell(game.player1Id, cardId, additionalCostPayment = payment)
                )
                withClue("Casting with X above the cap should fail: ${castResult.error}") {
                    (castResult.error != null) shouldBe true
                }
            }

            test("Blighting 0 with no creatures deals 0 damage") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Soul Immolation")
                    .withCardOnBattlefield(2, "Hill Giant") // 3/3 — opp creature
                    .withLandsOnBattlefield(1, "Mountain", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val hand = game.state.getHand(game.player1Id)
                val cardId = hand.first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Soul Immolation"
                }

                // Caster controls no creatures, so X must be 0 (no blightTargets)
                val payment = AdditionalCostPayment(blightAmount = 0)
                val castResult = game.execute(
                    CastSpell(game.player1Id, cardId, additionalCostPayment = payment)
                )
                withClue("Soul Immolation with X=0 should cast: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                game.resolveStack()

                // Player 2 took 0 damage
                game.getLifeTotal(2) shouldBe 20

                // Hill Giant survives
                game.isOnBattlefield("Hill Giant") shouldBe true
            }
        }
    }
}
