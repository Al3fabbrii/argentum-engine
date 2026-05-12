package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario test for Kodama of the East Tree.
 *
 * {4}{G}{G} 6/6 Legendary Creature — Spirit
 *   Reach
 *   Whenever another permanent you control enters, you may put a permanent card with
 *   equal or lesser mana value from your hand onto the battlefield.
 *
 * Implementation notes verified here:
 *   - The mana-value filter compares against the triggering permanent's mana value
 *     (CardPredicate.ManaValueAtMostEntity(EntityReference.Triggering)).
 *   - The "may" is modeled by ChooseUpTo(1) — declining means picking zero cards.
 *   - Only permanent cards are offered (instants/sorceries are filtered out).
 */
class KodamaOfTheEastTreeScenarioTest : ScenarioTestBase() {

    init {
        context("Kodama's permanent-enters trigger") {

            test("puts a permanent card with mana value <= the triggering permanent's MV") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Kodama of the East Tree")
                    .withCardInHand(1, "Elite Cat Warrior")    // MV 3 — cast as trigger source
                    .withCardInHand(1, "Grizzly Bears")        // MV 2 — eligible pay-off
                    .withLandsOnBattlefield(1, "Forest", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Elite Cat Warrior")
                game.resolveStack()

                // Kodama's trigger now pauses for the hand-card pick.
                val bearsInHand = game.findCardsInHand(1, "Grizzly Bears")
                withClue("Grizzly Bears (MV 2) should be a valid candidate (≤ Elite Cat Warrior MV 3)") {
                    bearsInHand.size shouldBe 1
                }
                game.selectCards(bearsInHand)
                game.resolveStack()

                withClue("Grizzly Bears should have entered the battlefield via Kodama") {
                    game.findAllPermanents("Grizzly Bears").size shouldBe 1
                }
                withClue("Elite Cat Warrior is still on the battlefield from its cast") {
                    game.findAllPermanents("Elite Cat Warrior").size shouldBe 1
                }
                withClue("Grizzly Bears is no longer in hand") {
                    game.findCardsInHand(1, "Grizzly Bears").size shouldBe 0
                }
            }

            test("hand card with higher mana value is not a valid pay-off") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Kodama of the East Tree")
                    .withCardInHand(1, "Llanowar Elves")       // MV 1 — trigger source
                    .withCardInHand(1, "Grizzly Bears")        // MV 2 — too expensive
                    .withLandsOnBattlefield(1, "Forest", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Llanowar Elves")
                // Trigger fires but Gather finds no matching candidates — resolves with
                // no decision and no effect.
                game.resolveStack()

                withClue("Grizzly Bears (MV 2 > MV 1) stays in hand") {
                    game.findCardsInHand(1, "Grizzly Bears").size shouldBe 1
                }
            }

            test("declining the optional trigger leaves the eligible card in hand") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Kodama of the East Tree")
                    .withCardInHand(1, "Elite Cat Warrior")    // MV 3 — trigger source
                    .withCardInHand(1, "Grizzly Bears")        // MV 2 — eligible but declined
                    .withLandsOnBattlefield(1, "Forest", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Elite Cat Warrior")
                game.resolveStack()

                // Card-selection appears; decline by selecting nothing.
                game.skipSelection()
                game.resolveStack()

                withClue("Grizzly Bears should still be in hand") {
                    game.findCardsInHand(1, "Grizzly Bears").size shouldBe 1
                }
                withClue("Only Elite Cat Warrior entered (not Grizzly Bears)") {
                    game.findAllPermanents("Grizzly Bears").size shouldBe 0
                }
            }
        }
    }
}
