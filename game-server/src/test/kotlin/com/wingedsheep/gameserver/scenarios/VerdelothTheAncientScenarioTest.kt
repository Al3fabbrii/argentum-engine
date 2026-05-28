package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.WasKickedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.inv.InvasionSet
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Verdeloth the Ancient — the first user of Kicker {X}.
 *
 * Card reference:
 * - Verdeloth the Ancient {4}{G}{G} — Legendary Creature — Treefolk 4/7
 *   Kicker {X}
 *   Saproling creatures and other Treefolk creatures get +1/+1.
 *   When Verdeloth enters, if it was kicked, create X 1/1 green Saproling creature tokens.
 *
 * Test cases:
 * 1. Unkicked — no Saproling tokens; lord still pumps a Treefolk; Verdeloth doesn't pump itself.
 * 2. Kicked with X=3 — creates three 1/1 Saprolings, each pumped to 2/2 by the lord effect.
 */
class VerdelothTheAncientScenarioTest : ScenarioTestBase() {

    // A vanilla Treefolk to verify the "other Treefolk creatures get +1/+1" lord bonus.
    private val grizzledTreefolk = card("Grizzled Treefolk") {
        manaCost = "{3}{G}"
        colorIdentity = "G"
        typeLine = "Creature — Treefolk"
        power = 2
        toughness = 3
    }

    init {
        cardRegistry.register(InvasionSet.cards)
        cardRegistry.register(grizzledTreefolk)

        context("Verdeloth the Ancient") {

            test("unkicked: no tokens, lord pumps other Treefolk but not itself") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Verdeloth the Ancient")
                    .withCardOnBattlefield(1, "Grizzled Treefolk")
                    .withLandsOnBattlefield(1, "Forest", 6) // {4}{G}{G}
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val castResult = game.castSpell(1, "Verdeloth the Ancient")
                withClue("Cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                withClue("Verdeloth should be on the battlefield") {
                    game.isOnBattlefield("Verdeloth the Ancient") shouldBe true
                }
                withClue("Unkicked Verdeloth makes no Saproling tokens") {
                    game.findPermanents("Saproling Token").size shouldBe 0
                }

                val projected = game.state.projectedState
                val verdeloth = game.findPermanent("Verdeloth the Ancient")!!
                val treefolk = game.findPermanent("Grizzled Treefolk")!!

                withClue("Verdeloth doesn't pump itself (excludeSelf on the Treefolk lord)") {
                    projected.getPower(verdeloth) shouldBe 4
                    projected.getToughness(verdeloth) shouldBe 7
                }
                withClue("Other Treefolk get +1/+1 (2/3 -> 3/4)") {
                    projected.getPower(treefolk) shouldBe 3
                    projected.getToughness(treefolk) shouldBe 4
                }
            }

            test("kicked X=3: creates three Saprolings, each pumped to 2/2") {
                val game = scenario()
                    .withPlayers("Player", "Opponent")
                    .withCardInHand(1, "Verdeloth the Ancient")
                    .withLandsOnBattlefield(1, "Forest", 9) // {4}{G}{G} + {X} where X=3
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val playerId = game.player1Id
                val cardId = game.state.getHand(playerId).first { entityId ->
                    game.state.getEntity(entityId)?.get<CardComponent>()?.name == "Verdeloth the Ancient"
                }

                val castResult = game.execute(
                    CastSpell(playerId, cardId, wasKicked = true, xValue = 3)
                )
                withClue("Kicked cast should succeed: ${castResult.error}") {
                    castResult.error shouldBe null
                }
                game.resolveStack()

                val verdeloth = game.findPermanent("Verdeloth the Ancient")!!
                withClue("Verdeloth should carry WasKickedComponent") {
                    (game.state.getEntity(verdeloth)?.has<WasKickedComponent>() == true) shouldBe true
                }

                val saprolings = game.findPermanents("Saproling Token")
                withClue("Kicked for X=3 makes three Saproling tokens") {
                    saprolings.size shouldBe 3
                }

                val projected = game.state.projectedState
                saprolings.forEach { saproling ->
                    withClue("Each Saproling is pumped 1/1 -> 2/2 by the lord effect") {
                        projected.getPower(saproling) shouldBe 2
                        projected.getToughness(saproling) shouldBe 2
                    }
                }
            }
        }
    }
}
