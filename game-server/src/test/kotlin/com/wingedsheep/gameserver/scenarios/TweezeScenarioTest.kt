package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Tweeze ({2}{R}, Instant):
 *   "Tweeze deals 3 damage to any target. You may discard a card. If you do, draw a card."
 *
 * Regression: the discard-then-draw used to be a `MayEffect(CompositeEffect[discard, draw])`,
 * which would still draw a card when the discard portion silently produced no movement
 * (e.g. empty hand). Switched to `MayEffect(IfYouDoEffect(discard, draw))` so the draw is
 * properly gated on the discard succeeding.
 */
class TweezeScenarioTest : ScenarioTestBase() {

    init {
        context("Tweeze — discard-then-draw rider") {

            test("declining the discard does not draw a card") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardInHand(1, "Tweeze")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellTargetingPlayer(1, "Tweeze", 2)

                // Tweeze resolves → 3 damage to Bob → "may discard?" yes/no decision
                game.resolveStack()

                withClue("Tweeze should present a may-discard yes/no decision") {
                    game.hasPendingDecision() shouldBe true
                }

                game.answerYesNo(false)
                game.resolveStack()

                withClue("Hand should be empty — declining the discard must not draw") {
                    game.handSize(1) shouldBe 0
                }
                withClue("Graveyard should hold only Tweeze (no discard happened)") {
                    game.graveyardSize(1) shouldBe 1
                }
            }

            test("accepting the discard discards one card and draws one") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardInHand(1, "Tweeze")
                    .withCardInHand(1, "Forest") // discard fodder
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellTargetingPlayer(1, "Tweeze", 2)
                game.resolveStack()

                game.answerYesNo(true)
                game.resolveStack()

                withClue("Hand size unchanged: discarded Forest, drew Plains") {
                    game.handSize(1) shouldBe 1
                }
                withClue("Forest should be in graveyard after discard") {
                    game.isInGraveyard(1, "Forest") shouldBe true
                }
            }

            test("accepting with empty hand: nothing discarded, no draw") {
                // Regression for the original bug: with the old
                // MayEffect(CompositeEffect[discard, draw]) wiring, saying yes still
                // drew a card even though no card was discarded.
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardInHand(1, "Tweeze")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Hand before cast: [Tweeze]; after cast: []
                game.castSpellTargetingPlayer(1, "Tweeze", 2)
                game.resolveStack()

                // Accept the may, but no card is available to discard.
                game.answerYesNo(true)
                game.resolveStack()

                withClue("No draw when nothing was actually discarded") {
                    game.handSize(1) shouldBe 0
                }
                withClue("Graveyard should hold only Tweeze (nothing else moved)") {
                    game.graveyardSize(1) shouldBe 1
                }
            }
        }
    }
}
