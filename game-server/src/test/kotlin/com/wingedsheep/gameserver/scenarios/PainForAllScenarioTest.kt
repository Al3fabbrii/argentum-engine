package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Pain for All.
 *
 * Card reference:
 * - Pain for All ({2}{R}): Enchantment — Aura
 *   Enchant creature you control
 *   When this Aura enters, enchanted creature deals damage equal to its power to any other target.
 *   Whenever enchanted creature is dealt damage, it deals that much damage to each opponent.
 *
 * Exercises the new SDK/engine plumbing this card introduced:
 * - EntityReference.EnchantedCreature (power of the attached creature as a dynamic amount)
 * - TargetOther(excludeAttachedCreature = true) ("any other target" excludes the enchanted creature)
 */
class PainForAllScenarioTest : ScenarioTestBase() {

    init {
        context("Pain for All - enters-the-battlefield trigger") {

            test("enchanted creature deals damage equal to its power to a chosen opponent") {
                val game = scenario()
                    .withPlayers("Aura Player", "Opponent")
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2
                    .withCardInHand(1, "Pain for All")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!

                val castResult = game.castSpell(1, "Pain for All", bears)
                withClue("Pain for All should be cast successfully: ${castResult.error}") {
                    castResult.error shouldBe null
                }

                // Aura resolves and attaches, then the ETB trigger asks for "any other target".
                game.resolveStack()
                game.selectTargets(listOf(game.player2Id))
                game.resolveStack()

                withClue("Opponent should have taken 2 damage (Grizzly Bears' power)") {
                    game.getLifeTotal(2) shouldBe 18
                }
                withClue("Aura controller's life should be unchanged") {
                    game.getLifeTotal(1) shouldBe 20
                }
                withClue("Pain for All should be attached and on the battlefield") {
                    game.findPermanent("Pain for All") shouldNotBe null
                }
            }

            test("\"any other target\" cannot be the enchanted creature itself") {
                val game = scenario()
                    .withPlayers("Aura Player", "Opponent")
                    .withCardOnBattlefield(1, "Grizzly Bears") // 2/2 — the creature being enchanted
                    .withCardInHand(1, "Pain for All")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                game.castSpell(1, "Pain for All", bears)
                game.resolveStack()

                val decision = game.getPendingDecision()
                decision.shouldBeInstanceOf<ChooseTargetsDecision>()
                val legal = decision.legalTargets[0] ?: emptyList()

                withClue("Enchanted creature must be excluded from 'any other target'") {
                    legal shouldNotContain bears
                }
                withClue("The opponent is still a legal target") {
                    legal shouldContain game.player2Id
                }
            }
        }

        context("Pain for All - dealt-damage retaliation") {

            test("when the enchanted creature is dealt damage, it deals that much to each opponent") {
                val game = scenario()
                    .withPlayers("Aura Player", "Opponent")
                    .withCardOnBattlefield(1, "Hulking Cyclops") // 5/4, survives 2 damage
                    .withCardInHand(1, "Pain for All")
                    .withCardInHand(1, "Shock") // deals 2 to any target
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withCardOnBattlefield(2, "Grizzly Bears") // ETB damage sink — keeps opponent's life isolated
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cyclops = game.findPermanent("Hulking Cyclops")!!
                val opponentBears = game.findPermanent("Grizzly Bears")!!

                // Attach the Aura; send the ETB damage into the opponent's Grizzly Bears so the
                // opponent's life only reflects the retaliation ability under test.
                game.castSpell(1, "Pain for All", cyclops)
                game.resolveStack()
                game.selectTargets(listOf(opponentBears))
                game.resolveStack()

                withClue("Opponent's Grizzly Bears should have died to 5 ETB damage") {
                    game.findPermanent("Grizzly Bears") shouldBe null
                }
                withClue("Opponent's life is still 20 (ETB damage hit the creature, not the player)") {
                    game.getLifeTotal(2) shouldBe 20
                }

                // Now deal 2 damage to the enchanted creature; it retaliates against each opponent.
                game.castSpell(1, "Shock", cyclops)
                game.resolveStack()

                withClue("Enchanted creature survived 2 damage (5/4)") {
                    game.findPermanent("Hulking Cyclops") shouldNotBe null
                    game.state.getEntity(cyclops)?.get<DamageComponent>()?.amount shouldBe 2
                }
                withClue("Each opponent took 2 damage from the retaliation trigger") {
                    game.getLifeTotal(2) shouldBe 18
                }
                withClue("Aura controller's life should be unchanged") {
                    game.getLifeTotal(1) shouldBe 20
                }
            }
        }
    }
}
