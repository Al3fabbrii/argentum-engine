package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Perfect Intimidation — focus on the new
 * [com.wingedsheep.sdk.scripting.effects.RemoveAllCountersEffect] mechanic.
 *
 * Perfect Intimidation {3}{B} — Sorcery
 * Choose one or both —
 * • Target opponent exiles two cards from their hand.
 * • Remove all counters from target creature.
 */
class PerfectIntimidationScenarioTest : ScenarioTestBase() {

    init {
        context("Perfect Intimidation — Remove all counters mode") {

            test("strips every counter kind from the target creature") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Perfect Intimidation")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Swamp", 4)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!

                game.state = game.state.updateEntity(bears) { container ->
                    container.with(
                        CountersComponent()
                            .withAdded(CounterType.PLUS_ONE_PLUS_ONE, 3)
                            .withAdded(CounterType.MINUS_ONE_MINUS_ONE, 1)
                    )
                }

                // Mode index 1 is "Remove all counters from target creature".
                val cast = game.castSpellWithMode(1, "Perfect Intimidation", modeIndex = 1, targetId = bears)
                withClue("Cast should succeed: ${cast.error}") { cast.error shouldBe null }

                game.resolveStack()

                val counters = game.state.getEntity(bears)?.get<CountersComponent>()
                withClue("All +1/+1 counters cleared") {
                    (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
                }
                withClue("All -1/-1 counters cleared") {
                    (counters?.getCount(CounterType.MINUS_ONE_MINUS_ONE) ?: 0) shouldBe 0
                }
            }

            test("is a no-op when the target creature has no counters") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Perfect Intimidation")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Swamp", 4)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!

                val cast = game.castSpellWithMode(1, "Perfect Intimidation", modeIndex = 1, targetId = bears)
                withClue("Cast should succeed: ${cast.error}") { cast.error shouldBe null }

                game.resolveStack()

                val counters = game.state.getEntity(bears)?.get<CountersComponent>()
                withClue("Target with no counters resolves cleanly") {
                    (counters?.counters?.values?.sum() ?: 0) shouldBe 0
                }
            }
        }

        context("Perfect Intimidation — both modes") {

            test("opponent exiles two cards and target creature loses its counters") {
                val game = scenario()
                    .withPlayers("Caster", "Opponent")
                    .withCardInHand(1, "Perfect Intimidation")
                    .withCardInHand(2, "Forest")
                    .withCardInHand(2, "Mountain")
                    .withCardInHand(2, "Plains")
                    .withCardOnBattlefield(2, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Swamp", 4)
                    .withCardInLibrary(1, "Swamp")
                    .withCardInLibrary(2, "Swamp")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!

                game.state = game.state.updateEntity(bears) { container ->
                    container.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 2))
                }

                val player1Id = game.player1Id
                val player2Id = game.player2Id
                val cardId = game.state.getHand(player1Id).first {
                    game.state.getEntity(it)?.get<CardComponent>()?.name == "Perfect Intimidation"
                }

                val opponentTarget = listOf(ChosenTarget.Player(player2Id))
                val creatureTarget = listOf(ChosenTarget.Permanent(bears))

                val cast = game.execute(
                    CastSpell(
                        playerId = player1Id,
                        cardId = cardId,
                        targets = opponentTarget + creatureTarget,
                        chosenModes = listOf(0, 1),
                        modeTargetsOrdered = listOf(opponentTarget, creatureTarget)
                    )
                )
                withClue("Cast should succeed: ${cast.error}") { cast.error shouldBe null }

                game.resolveStack()

                // Mode 0 prompts the targeted opponent to choose two cards from their hand.
                withClue("Opponent should be prompted to exile two cards from hand") {
                    game.hasPendingDecision() shouldBe true
                }
                val opponentHand = game.state.getHand(player2Id)
                game.selectCards(opponentHand.take(2))

                game.resolveStack()

                withClue("Opponent's hand should be reduced by two cards") {
                    game.handSize(2) shouldBe 1
                }
                withClue("Two cards should now be in opponent's exile") {
                    game.state.getExile(player2Id).size shouldBe 2
                }

                val counters = game.state.getEntity(bears)?.get<CountersComponent>()
                withClue("Mode 1 should also resolve and clear all counters") {
                    (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
                }
            }
        }
    }
}
