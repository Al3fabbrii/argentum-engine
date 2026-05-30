package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContainIgnoringCase

/**
 * Scenario tests for Scarred Puma's co-attacker restriction.
 *
 * Scarred Puma: {R} 2/1 Creature — Cat
 * "This creature can't attack unless a black or green creature also attacks."
 *
 * Exercises the new [com.wingedsheep.sdk.scripting.CantAttackUnlessCoAttacker] static ability,
 * which is validated against the full proposed attacker group at declaration time.
 */
class ScarredPumaScenarioTest : ScenarioTestBase() {

    // White creature: not a valid co-attacker for the puma.
    private val whiteSoldier = CardDefinition.creature(
        name = "Test White Soldier",
        manaCost = ManaCost.parse("{1}{W}"),
        subtypes = setOf(Subtype.SOLDIER),
        power = 2,
        toughness = 2
    )

    // Black creature: a valid co-attacker.
    private val blackZombie = CardDefinition.creature(
        name = "Test Black Zombie",
        manaCost = ManaCost.parse("{1}{B}"),
        subtypes = setOf(Subtype.ZOMBIE),
        power = 2,
        toughness = 2
    )

    // Green creature: a valid co-attacker.
    private val greenBear = CardDefinition.creature(
        name = "Test Green Bear",
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype.BEAR),
        power = 2,
        toughness = 2
    )

    init {
        cardRegistry.register(whiteSoldier)
        cardRegistry.register(blackZombie)
        cardRegistry.register(greenBear)

        context("Scarred Puma co-attacker restriction") {

            test("cannot attack alone") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Scarred Puma")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val pumaId = game.findPermanent("Scarred Puma")!!

                val result = game.execute(
                    DeclareAttackers(game.player1Id, mapOf(pumaId to game.player2Id))
                )

                withClue("Attack should fail - no black or green co-attacker") {
                    result.error shouldNotBe null
                    result.error!! shouldContainIgnoringCase "can't attack"
                }
            }

            test("cannot attack alongside only a white creature") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Scarred Puma")
                    .withCardOnBattlefield(1, "Test White Soldier")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val pumaId = game.findPermanent("Scarred Puma")!!
                val soldierId = game.findPermanent("Test White Soldier")!!

                val result = game.execute(
                    DeclareAttackers(
                        game.player1Id,
                        mapOf(pumaId to game.player2Id, soldierId to game.player2Id)
                    )
                )

                withClue("Attack should fail - white creature is not black or green") {
                    result.error shouldNotBe null
                    result.error!! shouldContainIgnoringCase "can't attack"
                }
            }

            test("CAN attack alongside a black creature") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Scarred Puma")
                    .withCardOnBattlefield(1, "Test Black Zombie")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val pumaId = game.findPermanent("Scarred Puma")!!
                val zombieId = game.findPermanent("Test Black Zombie")!!

                val result = game.execute(
                    DeclareAttackers(
                        game.player1Id,
                        mapOf(pumaId to game.player2Id, zombieId to game.player2Id)
                    )
                )

                withClue("Attack should succeed - black creature also attacks: ${result.error}") {
                    result.error shouldBe null
                }
            }

            test("CAN attack alongside a green creature") {
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Scarred Puma")
                    .withCardOnBattlefield(1, "Test Green Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val pumaId = game.findPermanent("Scarred Puma")!!
                val bearId = game.findPermanent("Test Green Bear")!!

                val result = game.execute(
                    DeclareAttackers(
                        game.player1Id,
                        mapOf(pumaId to game.player2Id, bearId to game.player2Id)
                    )
                )

                withClue("Attack should succeed - green creature also attacks: ${result.error}") {
                    result.error shouldBe null
                }
            }

            test("a non-puma attacker is unaffected and the puma stays home") {
                // Green bear attacks alone; puma does not attack. No restriction applies.
                val game = scenario()
                    .withPlayers("Attacker", "Defender")
                    .withCardOnBattlefield(1, "Scarred Puma")
                    .withCardOnBattlefield(1, "Test Green Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                val bearId = game.findPermanent("Test Green Bear")!!

                val result = game.execute(
                    DeclareAttackers(game.player1Id, mapOf(bearId to game.player2Id))
                )

                withClue("Attack should succeed - bear has no restriction: ${result.error}") {
                    result.error shouldBe null
                }
            }
        }
    }
}
