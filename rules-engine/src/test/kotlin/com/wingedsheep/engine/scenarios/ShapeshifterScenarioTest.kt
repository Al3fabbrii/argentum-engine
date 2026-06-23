package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Shapeshifter (ATQ #64).
 *
 * {6} Artifact Creature — Shapeshifter, star / 7-star
 * "As this creature enters, choose a number between 0 and 7. At the beginning of your upkeep, you
 *  may choose a number between 0 and 7. Its power is the last chosen number and its toughness is 7
 *  minus that number."
 *
 * Verifies the as-it-enters choice fixes P/T as power = chosen, toughness = 7 − chosen (so they
 * always sum to 7), across multiple chosen values.
 */
class ShapeshifterScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        fun castAndChoose(chosen: Int): Pair<TestGame, com.wingedsheep.sdk.model.EntityId> {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Shapeshifter")
                .withLandsOnBattlefield(1, "Mountain", 6)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Shapeshifter").error shouldBe null
            game.resolveStack()
            withClue("entering Shapeshifter prompts the number choice") {
                (game.getPendingDecision() != null) shouldBe true
            }
            game.chooseNumber(chosen)
            game.resolveStack()
            return game to game.findPermanent("Shapeshifter")!!
        }

        context("Shapeshifter") {

            test("choosing 5 makes it a 5/2") {
                val (game, shifter) = castAndChoose(5)
                val p = stateProjector.project(game.state)
                withClue("power = 5") { p.getPower(shifter) shouldBe 5 }
                withClue("toughness = 7 - 5 = 2") { p.getToughness(shifter) shouldBe 2 }
            }

            test("choosing 6 makes it a 6/1") {
                val (game, shifter) = castAndChoose(6)
                val p = stateProjector.project(game.state)
                withClue("power = 6") { p.getPower(shifter) shouldBe 6 }
                withClue("toughness = 7 - 6 = 1") { p.getToughness(shifter) shouldBe 1 }
            }

            test("choosing 7 makes it a 7/0, which dies to state-based actions (0 toughness)") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Shapeshifter")
                    .withLandsOnBattlefield(1, "Mountain", 6)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                game.castSpell(1, "Shapeshifter").error shouldBe null
                game.resolveStack()
                game.chooseNumber(7)
                game.resolveStack()
                withClue("a 7/0 has 0 toughness and is put into the graveyard by SBAs") {
                    game.findPermanent("Shapeshifter") shouldBe null
                }
            }

            test("choosing 0 makes it a 0/7, and power+toughness always sums to 7") {
                val (game, shifter) = castAndChoose(0)
                val p = stateProjector.project(game.state)
                withClue("power = 0") { p.getPower(shifter) shouldBe 0 }
                withClue("toughness = 7") { p.getToughness(shifter) shouldBe 7 }
                ((p.getPower(shifter) ?: 0) + (p.getToughness(shifter) ?: 0)) shouldBe 7
            }

            // The number is chosen as a true "As ~ enters" replacement (CR 614.1c), not an ETB
            // trigger: the choice is presented while the spell resolves, BEFORE the permanent is on
            // the battlefield — so it never briefly sits at its default P/T as a real permanent.
            test("the number choice is an as-enters replacement: prompted before the creature is on the battlefield") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Shapeshifter")
                    .withLandsOnBattlefield(1, "Mountain", 6)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Shapeshifter").error shouldBe null
                game.resolveStack()

                withClue("the number choice is pending") { (game.getPendingDecision() != null) shouldBe true }
                withClue("the creature has NOT entered yet — the choice precedes entry") {
                    game.findPermanent("Shapeshifter") shouldBe null
                }

                game.chooseNumber(4)
                game.resolveStack()

                val shifter = game.findPermanent("Shapeshifter")!!
                val p = stateProjector.project(game.state)
                withClue("once it enters it is already 4/3") {
                    p.getPower(shifter) shouldBe 4
                    p.getToughness(shifter) shouldBe 3
                }
            }
        }
    }
}
