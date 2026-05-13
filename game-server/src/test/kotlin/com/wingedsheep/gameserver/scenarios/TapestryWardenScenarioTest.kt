package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Tapestry Warden (EOE).
 *
 * Tapestry Warden: {3}{G} Artifact Creature — Robot Soldier 3/4
 * Vigilance
 * Each creature you control with toughness greater than its power assigns combat damage
 * equal to its toughness rather than its power.
 * Each creature you control with toughness greater than its power stations permanents
 * using its toughness rather than its power.
 */
class TapestryWardenScenarioTest : ScenarioTestBase() {

    init {
        context("Tapestry Warden — assigns damage as toughness") {

            test("1/2 creature assigns 2 (toughness) damage when Tapestry Warden is in play") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Tapestry Warden", summoningSickness = false)
                    .withCardOnBattlefield(1, "Devoted Hero", summoningSickness = false) // 1/2 (T > P)
                    .withActivePlayer(1)
                    .withLifeTotal(2, 20)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Devoted Hero" to 2))
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Defending player should have taken 2 (toughness of 1/2 Devoted Hero) damage") {
                    game.getLifeTotal(2) shouldBe 18
                }
            }

            test("Tapestry Warden itself (3/4, T > P) assigns 4 (toughness) damage") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Tapestry Warden", summoningSickness = false)
                    .withActivePlayer(1)
                    .withLifeTotal(2, 20)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Tapestry Warden" to 2))
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Tapestry Warden (3/4) should assign 4 (its toughness) damage") {
                    game.getLifeTotal(2) shouldBe 16
                }
            }

            test("creature with power >= toughness is not affected (still uses power)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Tapestry Warden", summoningSickness = false)
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false) // 2/2 (T == P)
                    .withActivePlayer(1)
                    .withLifeTotal(2, 20)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Grizzly Bears" to 2))
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Grizzly Bears (2/2, T == P) should still deal 2 (power) damage") {
                    game.getLifeTotal(2) shouldBe 18
                }
            }

            test("ability only applies to controller's creatures, not opponent's") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Tapestry Warden", summoningSickness = false)
                    .withCardOnBattlefield(2, "Devoted Hero", summoningSickness = false) // opponent's 1/2
                    .withActivePlayer(2)
                    .withLifeTotal(1, 20)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Devoted Hero" to 1))
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Opponent's 1/2 Devoted Hero should deal 1 (power) damage, not 2 (toughness)") {
                    game.getLifeTotal(1) shouldBe 19
                }
            }
        }

        context("Tapestry Warden — stations using toughness") {

            test("1/2 creature contributes 2 (toughness) charge counters when stationing with Tapestry Warden in play") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Tapestry Warden", summoningSickness = false)
                    .withCardOnBattlefield(1, "Devoted Hero", summoningSickness = false) // 1/2 tapper
                    .withCardOnBattlefield(1, "Debris Field Crusher") // the Spacecraft being stationed
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val stationAbility = cardRegistry.getCard("Debris Field Crusher")!!
                    .script.activatedAbilities.first()
                val crusherId = game.findPermanent("Debris Field Crusher")!!
                val devotedHeroId = game.findPermanent("Devoted Hero")!!

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = crusherId,
                        abilityId = stationAbility.id,
                        costPayment = AdditionalCostPayment(tappedPermanents = listOf(devotedHeroId))
                    )
                )
                withClue("Station activation should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                game.resolveStack()

                val counters = game.state.getEntity(crusherId)?.get<CountersComponent>()
                withClue("Debris Field Crusher should have 2 charge counters (Devoted Hero toughness), not 1 (power)") {
                    counters?.getCount(CounterType.CHARGE) shouldBe 2
                }
            }

            test("without Tapestry Warden, 1/2 creature contributes only 1 (power) charge counter") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Devoted Hero", summoningSickness = false) // 1/2 tapper
                    .withCardOnBattlefield(1, "Debris Field Crusher")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val stationAbility = cardRegistry.getCard("Debris Field Crusher")!!
                    .script.activatedAbilities.first()
                val crusherId = game.findPermanent("Debris Field Crusher")!!
                val devotedHeroId = game.findPermanent("Devoted Hero")!!

                game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = crusherId,
                        abilityId = stationAbility.id,
                        costPayment = AdditionalCostPayment(tappedPermanents = listOf(devotedHeroId))
                    )
                )
                game.resolveStack()

                val counters = game.state.getEntity(crusherId)?.get<CountersComponent>()
                withClue("Without Tapestry Warden, Debris Field Crusher should have 1 charge counter (power)") {
                    counters?.getCount(CounterType.CHARGE) shouldBe 1
                }
            }
        }
    }
}
