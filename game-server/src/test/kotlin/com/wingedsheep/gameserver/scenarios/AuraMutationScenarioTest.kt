package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Aura Mutation.
 *
 * Card reference:
 * - Aura Mutation ({G}{W}): Instant
 *   Destroy target enchantment. Create X 1/1 green Saproling creature tokens, where X is that
 *   enchantment's mana value.
 *
 * The load-bearing detail is that the token count reads the enchantment's mana value *after* it
 * has been destroyed (moved to the graveyard). Mana value is a card characteristic that survives
 * the zone change, so `DynamicAmount.EntityProperty(Target(0), ManaValue)` resolves against the
 * destroyed card. These tests pin two different mana values to prove the count tracks the
 * destroyed enchantment, not a hardcoded number.
 */
class AuraMutationScenarioTest : ScenarioTestBase() {

    init {
        context("Aura Mutation") {

            test("destroys the target enchantment and makes Saprolings equal to its mana value") {
                // Collective Restraint costs {3}{U} → mana value 4.
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(2, "Collective Restraint")
                    .withCardInHand(1, "Aura Mutation")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val enchantmentId = game.findPermanent("Collective Restraint")!!
                val castResult = game.castSpell(1, "Aura Mutation", targetId = enchantmentId)
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("The enchantment should be destroyed") {
                    game.findPermanents("Collective Restraint").isEmpty() shouldBe true
                    game.isInGraveyard(2, "Collective Restraint") shouldBe true
                }
                withClue("Mana value 4 → four Saproling tokens") {
                    game.findPermanents("Saproling Token").size shouldBe 4
                }
                withClue("Tokens are controlled by the spell's caster") {
                    game.findPermanents("Saproling Token").all { tokenId ->
                        game.state.getEntity(tokenId)?.get<ControllerComponent>()?.playerId == game.player1Id
                    } shouldBe true
                }
            }

            test("token count follows the destroyed enchantment's mana value") {
                // Vile Consumption costs {1}{U}{B} → mana value 3.
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(2, "Vile Consumption")
                    .withCardInHand(1, "Aura Mutation")
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val enchantmentId = game.findPermanent("Vile Consumption")!!
                game.castSpell(1, "Aura Mutation", targetId = enchantmentId)
                game.resolveStack()

                withClue("Mana value 3 → three Saproling tokens") {
                    game.findPermanents("Saproling Token").size shouldBe 3
                }
            }
        }
    }
}
