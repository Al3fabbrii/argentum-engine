package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsDiscardedEvent
import com.wingedsheep.engine.core.CardsRevealedEvent
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Sindbad and its [DrawRevealDiscardUnlessEffect] ability:
 * "{T}: Draw a card and reveal it. If it isn't a land card, discard it."
 */
class SindbadScenarioTest : ScenarioTestBase() {

    /** Activate Sindbad's tap ability, resolve it, and return every event emitted. */
    private fun activateSindbad(game: TestGame): List<GameEvent> {
        val sourceId = game.findPermanent("Sindbad")!!
        val ability = cardRegistry.getCard("Sindbad")!!.script.activatedAbilities[0]
        val activation = game.execute(
            ActivateAbility(
                playerId = game.player1Id,
                sourceId = sourceId,
                abilityId = ability.id,
            )
        )
        withClue("Activation should succeed: ${activation.error}") {
            activation.error shouldBe null
        }
        val events = activation.events.toMutableList()
        game.resolveStack().forEach { events.addAll(it.events) }
        return events
    }

    init {
        context("Sindbad's draw-reveal-discard ability") {

            test("discards a drawn nonland card") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Sindbad")
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                activateSindbad(game)

                withClue("Nonland drawn card should be discarded to the graveyard") {
                    game.isInGraveyard(1, "Grizzly Bears") shouldBe true
                }
                withClue("Discarded card should not remain in hand") {
                    game.handSize(1) shouldBe 0
                }
            }

            test("discarding the nonland card emits a CardsDiscardedEvent") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Sindbad")
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val events = activateSindbad(game)

                val discards = events.filterIsInstance<CardsDiscardedEvent>()
                withClue("A discard must fire a CardsDiscardedEvent so discard triggers/madness see it") {
                    discards.any { it.playerId == game.player1Id && it.cardNames.contains("Grizzly Bears") }
                        .shouldBeTrue()
                }
            }

            test("reveals the drawn card whether it is kept or discarded") {
                val discardGame = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Sindbad")
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val discardReveals = activateSindbad(discardGame).filterIsInstance<CardsRevealedEvent>()
                withClue("The discarded nonland card should still be revealed") {
                    discardReveals.any {
                        it.revealingPlayerId == discardGame.player1Id && it.cardNames.contains("Grizzly Bears")
                    }.shouldBeTrue()
                }

                val keepGame = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Sindbad")
                    .withCardInLibrary(1, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val keepReveals = activateSindbad(keepGame).filterIsInstance<CardsRevealedEvent>()
                withClue("The kept land card should be revealed to the opponent") {
                    keepReveals.any {
                        it.revealingPlayerId == keepGame.player1Id && it.cardNames.contains("Mountain")
                    }.shouldBeTrue()
                }
            }

            test("keeps a drawn land card") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Sindbad")
                    .withCardInLibrary(1, "Mountain")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val events = activateSindbad(game)

                withClue("Land drawn card should be kept in hand") {
                    game.handSize(1) shouldBe 1
                }
                withClue("Land drawn card should not be discarded") {
                    game.isInGraveyard(1, "Mountain") shouldBe false
                }
                withClue("Keeping a land must not fire a discard event") {
                    events.filterIsInstance<CardsDiscardedEvent>() shouldBe emptyList()
                }
            }

            test("activating with an empty library discards nothing and does not crash") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Sindbad")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val events = activateSindbad(game)

                withClue("Empty-library draw should not put anything in hand") {
                    game.handSize(1) shouldBe 0
                }
                withClue("Empty-library draw should not discard anything") {
                    game.graveyardSize(1) shouldBe 0
                    events.filterIsInstance<CardsDiscardedEvent>() shouldBe emptyList()
                }
            }
        }
    }
}
