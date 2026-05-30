package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Scenario tests for Invasion "batch B": the spellcaster masters and a few spells/permanents
 * that compose existing primitives in non-trivial ways.
 *
 *  - Nightscape Master: {U}{U},{T} bounce target creature; {R}{R},{T} deal 2 damage.
 *  - Phyrexian Infiltrator: {2}{U}{U} exchange control of itself and target creature.
 *  - Reya Dawnbringer: upkeep "may return target creature card from your graveyard to the battlefield".
 *  - Stalking Assassin: {3}{U},{T} tap; {3}{B},{T} destroy target tapped creature.
 *  - Sway of Illusion: any number of target creatures become a chosen color; draw a card.
 */
class InvasionBatchBScenarioTest : ScenarioTestBase() {

    private val greenBear = CardDefinition.creature(
        name = "Green Bear", manaCost = ManaCost.parse("{G}"),
        subtypes = setOf(Subtype("Bear")), power = 2, toughness = 2
    )
    private val redOgre = CardDefinition.creature(
        name = "Red Ogre", manaCost = ManaCost.parse("{R}"),
        subtypes = setOf(Subtype("Ogre")), power = 2, toughness = 2
    )

    private fun setMana(game: TestGame, white: Int = 0, blue: Int = 0, black: Int = 0, red: Int = 0, green: Int = 0, colorless: Int = 0) {
        game.state = game.state.updateEntity(game.player1Id) { container ->
            container.with(ManaPoolComponent(white = white, blue = blue, black = black, red = red, green = green, colorless = colorless))
        }
    }

    private fun activate(game: TestGame, sourceName: String, abilityIndex: Int, targets: List<ChosenTarget> = emptyList()) {
        val sourceId = game.findPermanent(sourceName)!!
        val ability = cardRegistry.getCard(sourceName)!!.script.activatedAbilities[abilityIndex]
        val result = game.execute(
            ActivateAbility(
                playerId = game.player1Id,
                sourceId = sourceId,
                abilityId = ability.id,
                targets = targets
            )
        )
        withClue("Activation should succeed: ${result.error}") { result.error shouldBe null }
    }

    init {
        cardRegistry.register(greenBear)
        cardRegistry.register(redOgre)

        context("Nightscape Master") {
            test("{U}{U},{T} returns target creature to its owner's hand") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Nightscape Master")
                    .withCardOnBattlefield(2, "Green Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bear = game.findPermanent("Green Bear")!!
                setMana(game, blue = 2)
                activate(game, "Nightscape Master", 0, listOf(ChosenTarget.Permanent(bear)))
                game.resolveStack()

                withClue("Green Bear should be bounced off the battlefield") {
                    game.findPermanent("Green Bear") shouldBe null
                }
                withClue("Green Bear should be in its owner's (Player 2) hand") {
                    game.state.getHand(game.player2Id).any {
                        game.state.getEntity(it)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Green Bear"
                    } shouldBe true
                }
            }

            test("{R}{R},{T} deals 2 damage to target creature, killing a 2/2") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Nightscape Master")
                    .withCardOnBattlefield(2, "Green Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bear = game.findPermanent("Green Bear")!!
                setMana(game, red = 2)
                activate(game, "Nightscape Master", 1, listOf(ChosenTarget.Permanent(bear)))
                game.resolveStack()

                withClue("Green Bear should die from 2 damage") {
                    game.isInGraveyard(2, "Green Bear") shouldBe true
                }
            }
        }

        context("Phyrexian Infiltrator") {
            test("exchanges control of itself and a target creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Phyrexian Infiltrator")
                    .withCardOnBattlefield(2, "Green Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val infiltrator = game.findPermanent("Phyrexian Infiltrator")!!
                val bear = game.findPermanent("Green Bear")!!
                setMana(game, blue = 2, colorless = 2)
                activate(game, "Phyrexian Infiltrator", 0, listOf(ChosenTarget.Permanent(bear)))
                game.resolveStack()

                val projected = game.state.projectedState
                withClue("Player 2 should now control the Infiltrator") {
                    projected.getController(infiltrator) shouldBe game.player2Id
                }
                withClue("Player 1 should now control the Green Bear") {
                    projected.getController(bear) shouldBe game.player1Id
                }
            }
        }

        context("Reya Dawnbringer") {
            test("upkeep trigger may return a creature card from graveyard to the battlefield") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Reya Dawnbringer")
                    .withCardInGraveyard(1, "Green Bear")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.BEGINNING, Step.UNTAP)
                    .build()

                // Pass into the upkeep step so the beginning-of-upkeep trigger fires.
                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
                game.resolveStack()
                game.answerYesNo(true)
                val bearInGy = game.state.getGraveyard(game.player1Id).first {
                    game.state.getEntity(it)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Green Bear"
                }
                game.selectTargets(listOf(bearInGy))
                game.resolveStack()

                withClue("Green Bear should be returned to the battlefield under Player 1's control") {
                    game.findPermanent("Green Bear") shouldNotBe null
                }
            }
        }

        context("Stalking Assassin") {
            test("{3}{U},{T} taps a creature, then {3}{B},{T} destroys a tapped creature") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardOnBattlefield(1, "Stalking Assassin")
                    .withCardOnBattlefield(1, "Stalking Assassin")
                    .withCardOnBattlefield(2, "Green Bear")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val assassins = game.findPermanents("Stalking Assassin")
                val tapper = assassins[0]
                val destroyer = assassins[1]
                val bear = game.findPermanent("Green Bear")!!

                // Tap the bear with the first assassin.
                val tapAbility = cardRegistry.getCard("Stalking Assassin")!!.script.activatedAbilities[0]
                setMana(game, blue = 1, colorless = 3)
                game.execute(
                    ActivateAbility(game.player1Id, tapper, tapAbility.id, listOf(ChosenTarget.Permanent(bear)))
                ).error shouldBe null
                game.resolveStack()

                withClue("Green Bear should be tapped") {
                    game.state.getEntity(bear)?.has<TappedComponent>() shouldBe true
                }

                // Destroy the now-tapped bear with the second assassin.
                val destroyAbility = cardRegistry.getCard("Stalking Assassin")!!.script.activatedAbilities[1]
                setMana(game, black = 1, colorless = 3)
                game.execute(
                    ActivateAbility(game.player1Id, destroyer, destroyAbility.id, listOf(ChosenTarget.Permanent(bear)))
                ).error shouldBe null
                game.resolveStack()

                withClue("Green Bear should be destroyed") {
                    game.isInGraveyard(2, "Green Bear") shouldBe true
                }
            }
        }

        context("Sway of Illusion") {
            test("makes target creatures the chosen color and draws a card") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Sway of Illusion")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withCardOnBattlefield(1, "Green Bear")
                    .withCardOnBattlefield(2, "Red Ogre")
                    .withCardInLibrary(1, "Island")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bear = game.findPermanent("Green Bear")!!
                val ogre = game.findPermanent("Red Ogre")!!
                val cardId = game.state.getHand(game.player1Id).first {
                    game.state.getEntity(it)?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Sway of Illusion"
                }
                val handBefore = game.handSize(1)

                game.execute(
                    CastSpell(
                        game.player1Id, cardId,
                        targets = listOf(ChosenTarget.Permanent(bear), ChosenTarget.Permanent(ogre))
                    )
                ).error shouldBe null
                game.resolveStack()

                val colorDecision = game.getPendingDecision()
                withClue("Should pause for a color choice") {
                    (colorDecision is ChooseColorDecision) shouldBe true
                }
                game.submitDecision(ColorChosenResponse(colorDecision!!.id, Color.WHITE))
                game.resolveStack()

                val projected = game.state.projectedState
                withClue("Green Bear should now be white") {
                    projected.hasColor(bear, Color.WHITE) shouldBe true
                }
                withClue("Red Ogre should now be white") {
                    projected.hasColor(ogre, Color.WHITE) shouldBe true
                }
                withClue("A card should have been drawn (cast 1 from hand, drew 1 → net same size, minus the spell)") {
                    // Started with just the spell; cast it (−1) then drew (+1).
                    game.handSize(1) shouldBe handBefore
                }
            }
        }
    }
}
