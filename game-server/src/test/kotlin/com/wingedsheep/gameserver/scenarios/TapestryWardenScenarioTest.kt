package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
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
                    .withCardOnBattlefield(1, "Devoted Hero", summoningSickness = false) // 1/2 (T=2 > P=1)
                    .withActivePlayer(1)
                    .withLifeTotal(2, 20)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                // Devoted Hero: 1/2, toughness > power → assigns 2 damage
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
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false) // 2/2 (T=P)
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

                // Opponent's 1/2 attacks — Tapestry Warden does NOT apply to opponent's creatures
                game.declareAttackers(mapOf("Devoted Hero" to 1))
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Opponent's 1/2 Devoted Hero should deal 1 (power) damage, not 2 (toughness)") {
                    game.getLifeTotal(1) shouldBe 19
                }
            }
        }
    }
}
