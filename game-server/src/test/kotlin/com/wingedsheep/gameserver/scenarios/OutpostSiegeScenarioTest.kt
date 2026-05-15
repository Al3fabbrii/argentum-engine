package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.components.identity.ChosenModeComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Scenario tests for Outpost Siege.
 *
 * Card reference:
 * - Outpost Siege ({3}{R}): Enchantment
 *   "As this enchantment enters, choose Khans or Dragons.
 *    • Khans — At the beginning of your upkeep, exile the top card of your
 *      library. Until end of turn, you may play that card.
 *    • Dragons — Whenever a creature you control leaves the battlefield, this
 *      enchantment deals 1 damage to any target."
 *
 * Exercises the generic `ChoiceType.MODE` `EntersWithChoice` primitive (named
 * options + optional icons) and the `SourceChosenModeIs` trigger condition.
 */
class OutpostSiegeScenarioTest : ScenarioTestBase() {

    private fun TestGame.chooseMode(modeId: String) {
        val decision = getPendingDecision()
        decision.shouldNotBeNull()
        decision.shouldBeInstanceOf<ChooseOptionDecision>()
        val idx = decision.optionMetadata.indexOfFirst { it.id == modeId }
        withClue("Mode '$modeId' should be among options ${decision.optionMetadata.map { it.id }}") {
            (idx >= 0) shouldBe true
        }
        submitDecision(OptionChosenResponse(decision.id, idx))
    }

    init {
        context("Outpost Siege - mode choice on entry") {

            test("ChooseOptionDecision exposes both labels, descriptions, and icon keys") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Outpost Siege")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Outpost Siege")
                castResult.error shouldBe null
                game.resolveStack()

                val decision = game.getPendingDecision()
                decision.shouldNotBeNull()
                decision.shouldBeInstanceOf<ChooseOptionDecision>()
                decision.options shouldBe listOf("Khans", "Dragons")
                decision.optionMetadata.map { it.id } shouldBe listOf("khans", "dragons")
                decision.optionMetadata.map { it.iconKey } shouldBe listOf("khans", "dragons")
                decision.optionMetadata.forEach { it.description shouldNotBe null }
            }

            test("Khans mode stores chosen mode, upkeep trigger exiles top card") {
                // Start Outpost Siege already on the battlefield with Khans mode stored,
                // then transition from UNTAP → UPKEEP for player 1 to fire the trigger.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Outpost Siege", tapped = false)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.BEGINNING, Step.UNTAP)
                    .build()

                // Stamp the chosen mode component directly (the EntersWithChoice flow is
                // covered separately by the "ChooseOptionDecision exposes ..." test).
                val siegeId = game.findPermanent("Outpost Siege")!!
                game.state = game.state.updateEntity(siegeId) { container ->
                    container.with(ChosenModeComponent("khans"))
                }

                val librarySizeBefore = game.librarySize(1)
                val exiledBefore = game.state.getZone(
                    com.wingedsheep.engine.state.ZoneKey(game.player1Id, Zone.EXILE)
                ).size

                // Transition into UPKEEP — the Khans trigger fires here.
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
                game.resolveStack()

                withClue("Khans mode should exile the top card of the library at upkeep") {
                    val exiledAfter = game.state.getZone(
                        com.wingedsheep.engine.state.ZoneKey(game.player1Id, Zone.EXILE)
                    ).size
                    exiledAfter shouldBe exiledBefore + 1
                    game.librarySize(1) shouldBe librarySizeBefore - 1
                }
            }

            test("Dragons mode stores chosen mode and gates Khans trigger off") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Outpost Siege")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Outpost Siege")
                game.resolveStack()
                game.chooseMode("dragons")

                val siegeId = game.findPermanent("Outpost Siege")!!
                game.state.getEntity(siegeId)?.get<ChosenModeComponent>()?.modeId shouldBe "dragons"

                val librarySizeBefore = game.librarySize(1)
                val exiledBefore = game.state.getZone(
                    com.wingedsheep.engine.state.ZoneKey(game.player1Id, Zone.EXILE)
                ).size

                // Advance to player 1's NEXT upkeep — the Khans trigger MUST NOT fire
                // because Dragons mode was chosen. SourceChosenModeIs gates it off.
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
                game.resolveStack()
                game.passUntilPhase(Phase.BEGINNING, Step.UNTAP)
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
                game.resolveStack()

                withClue("Dragons-mode Siege should not impulse-draw at upkeep") {
                    game.state.getZone(
                        com.wingedsheep.engine.state.ZoneKey(game.player1Id, Zone.EXILE)
                    ).size shouldBe exiledBefore
                    game.librarySize(1) shouldBe librarySizeBefore
                }
            }

            test("Dragons mode triggers when a creature you control leaves the battlefield") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Outpost Siege")
                    .withCardInHand(1, "Shock")
                    .withCardOnBattlefield(1, "Grizzly Bears", tapped = false)
                    .withLandsOnBattlefield(1, "Mountain", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Outpost Siege")
                game.resolveStack()
                game.chooseMode("dragons")

                // Shock our own Grizzly Bears (2 damage = lethal).
                val bearsId = game.findPermanent("Grizzly Bears")!!
                game.castSpell(1, "Shock", bearsId)
                game.resolveStack()

                // Bears died → Dragons trigger should be on the stack waiting for target.
                val decision = game.getPendingDecision()
                decision.shouldNotBeNull()
                withClue("Dragons-mode trigger should ask for a target after creature leaves") {
                    decision.shouldBeInstanceOf<com.wingedsheep.engine.core.ChooseTargetsDecision>()
                }

                // Target the opponent for 1 damage.
                val opponentLifeBefore = game.state.getEntity(game.player2Id)
                    ?.get<LifeTotalComponent>()?.life ?: error("missing life")
                game.selectTargets(listOf(game.player2Id))
                game.resolveStack()
                val opponentLifeAfter = game.state.getEntity(game.player2Id)
                    ?.get<LifeTotalComponent>()?.life ?: error("missing life")
                opponentLifeAfter shouldBe opponentLifeBefore - 1
            }
        }
    }
}
