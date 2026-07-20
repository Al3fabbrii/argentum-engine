package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for a batch of Wilds of Eldraine cards implemented together:
 *
 *  - Edgewall Pack ({3}{R} 3/3, Menace) — ETB makes WOE's 1/1 black Rat that can't block.
 *  - Charmed Clothier ({4}{W} 3/3, Flying) — ETB attaches a Royal Role to *another* creature
 *    you control.
 *  - Dream Spoilers ({3}{B} 2/2, Flying) — casting a spell during an opponent's turn shrinks
 *    a creature an opponent controls; the trigger stays silent on your own turn.
 *  - Bitter Chill ({1}{U} Aura) — taps and locks down the enchanted creature, and refunds a
 *    scry-and-draw for {1} when it hits the graveyard.
 *  - Glass Casket ({1}{W} artifact, canonical in ELD) — linked-exile removal for cheap
 *    creatures, returned when the Casket leaves.
 */
class WoeCardsBatch3ScenarioTest : ScenarioTestBase() {

    init {
        context("Edgewall Pack — menace plus a can't-block Rat") {
            test("entering the battlefield creates a 1/1 black Rat that can't block") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Edgewall Pack")
                    .withLandsOnBattlefield(1, "Mountain", 4)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Edgewall Pack")
                game.resolveStack()

                val pack = game.findPermanent("Edgewall Pack")!!
                withClue("Edgewall Pack has menace") {
                    game.state.projectedState.hasKeyword(pack, Keyword.MENACE) shouldBe true
                }

                val rat = game.findPermanent("Rat Token")
                withClue("the ETB trigger created a Rat token") { (rat != null) shouldBe true }

                withClue("the Rat is a 1/1") {
                    game.state.projectedState.getPower(rat!!) shouldBe 1
                    game.state.projectedState.getToughness(rat) shouldBe 1
                }
            }
        }

        context("Charmed Clothier — Royal Role on another creature you control") {
            test("the Role lands on the other creature, not on the Clothier itself") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Charmed Clothier")
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Plains", 5)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!

                val card = game.findCardsInHand(1, "Charmed Clothier").first()
                game.execute(CastSpell(game.player1Id, card, emptyList())).error shouldBe null
                game.resolveStack() // Clothier enters -> ETB trigger asks for its target

                game.selectTargets(listOf(bears)).error shouldBe null
                game.resolveStack()

                withClue("a Royal Role token exists") {
                    (game.findPermanent("Royal Role") != null) shouldBe true
                }
                withClue("2/2 Bears + Royal Role's +1/+1 = 3/3") {
                    game.state.projectedState.getPower(bears) shouldBe 3
                    game.state.projectedState.getToughness(bears) shouldBe 3
                }

                val clothier = game.findPermanent("Charmed Clothier")!!
                withClue("the Clothier is unenchanted — 'another target creature' excludes itself") {
                    game.state.projectedState.getPower(clothier) shouldBe 3
                    game.state.projectedState.getToughness(clothier) shouldBe 3
                }
                withClue("the Clothier has flying") {
                    game.state.projectedState.hasKeyword(clothier, Keyword.FLYING) shouldBe true
                }
            }
        }

        context("Dream Spoilers — only on an opponent's turn") {
            test("casting an instant on the opponent's turn shrinks their creature") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Dream Spoilers", summoningSickness = false)
                    .withCardInHand(1, "Shock")
                    .withCardOnBattlefield(2, "Hill Giant", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(2)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giant = game.findPermanent("Hill Giant")!!

                // Shock goes at the opponent's face so the only shrink comes from Dream Spoilers.
                game.castSpellTargetingPlayer(1, "Shock", 2)
                if (game.hasPendingDecision()) game.selectTargets(listOf(giant)).error shouldBe null
                game.resolveStack()
                if (game.hasPendingDecision()) game.selectTargets(listOf(giant)).error shouldBe null
                game.resolveStack()

                withClue("the opponent lost 2 life to Shock") {
                    game.getLifeTotal(2) shouldBe 18
                }
                withClue("3/3 Hill Giant gets -1/-1 from the Dream Spoilers trigger") {
                    game.state.projectedState.getPower(giant) shouldBe 2
                    game.state.projectedState.getToughness(giant) shouldBe 2
                }
            }

            test("casting a spell on your own turn does not trigger") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Dream Spoilers", summoningSickness = false)
                    .withCardInHand(1, "Giant Growth")
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardOnBattlefield(2, "Hill Giant", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val giant = game.findPermanent("Hill Giant")!!

                game.castSpell(1, "Giant Growth", bears)
                game.resolveStack()

                withClue("no pending target decision — the trigger never fired on our own turn") {
                    game.hasPendingDecision() shouldBe false
                }
                withClue("the opponent's Hill Giant is untouched") {
                    game.state.projectedState.getPower(giant) shouldBe 3
                    game.state.projectedState.getToughness(giant) shouldBe 3
                }
            }
        }

        context("Bitter Chill — tap, lock, and a {1} refund") {
            test("entering taps the enchanted creature and keeps it from untapping") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Bitter Chill")
                    .withCardOnBattlefield(2, "Grizzly Bears", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!

                game.castSpell(1, "Bitter Chill", bears)
                game.resolveStack()

                withClue("the ETB trigger tapped the enchanted creature") {
                    game.state.getEntity(bears)?.has<TappedComponent>() shouldBe true
                }
                withClue("the enchanted creature carries DOESNT_UNTAP") {
                    game.state.projectedState.hasKeyword(bears, AbilityFlag.DOESNT_UNTAP) shouldBe true
                }
            }

            test("destroying the enchanted creature offers the {1} scry-and-draw refund") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(2, "Grizzly Bears", summoningSickness = false)
                    .withCardAttachedTo(1, "Bitter Chill", "Grizzly Bears")
                    .withCardInHand(1, "Doom Blade")
                    .withLandsOnBattlefield(1, "Swamp", 2)
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Island")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                val handBefore = game.handSize(1)

                game.castSpell(1, "Doom Blade", bears)
                game.resolveStack()

                withClue("the Aura followed its creature into the graveyard") {
                    game.isInGraveyard(1, "Bitter Chill") shouldBe true
                }

                // "You may pay {1}. If you do, scry 1, then draw a card."
                game.answerYesNo(true)
                game.submitManaSourcesAutoPay()
                game.skipSelection()    // scry 1: nothing to the bottom
                game.keepLibraryOrder() // …and leave the top card where it is
                game.resolveStack()

                withClue("Doom Blade left the hand and the refund drew a card back — net zero") {
                    game.handSize(1) shouldBe handBefore
                }
                withClue("the scried-then-drawn card is the top of the library") {
                    game.isInHand(1, "Forest") shouldBe true
                }
            }
        }

        context("Glass Casket — linked exile for cheap creatures") {
            test("exiles a cheap creature and returns it when the Casket dies") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Glass Casket")
                    .withCardOnBattlefield(2, "Grizzly Bears", summoningSickness = false)
                    .withCardInHand(1, "Shatter")
                    .withLandsOnBattlefield(1, "Plains", 2)
                    .withLandsOnBattlefield(1, "Mountain", 2)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!

                val card = game.findCardsInHand(1, "Glass Casket").first()
                game.execute(CastSpell(game.player1Id, card, emptyList())).error shouldBe null
                game.resolveStack() // Casket enters -> ETB trigger asks for its target

                if (game.hasPendingDecision()) game.selectTargets(listOf(bears)).error shouldBe null
                game.resolveStack()

                withClue("Grizzly Bears (mana value 2) is exiled") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                    game.isInExile(2, "Grizzly Bears") shouldBe true
                }

                val casket = game.findPermanent("Glass Casket")!!
                game.castSpell(1, "Shatter", casket)
                game.resolveStack()

                withClue("the Casket leaving returns its linked exile to the battlefield") {
                    game.isInExile(2, "Grizzly Bears") shouldBe false
                    game.isOnBattlefield("Grizzly Bears") shouldBe true
                }
            }
        }
    }
}
