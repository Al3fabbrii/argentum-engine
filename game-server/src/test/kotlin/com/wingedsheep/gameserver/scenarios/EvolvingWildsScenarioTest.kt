package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain

/**
 * Scenario tests for Evolving Wilds (Lorwyn Eclipsed reprint).
 *
 * Card reference:
 * - Evolving Wilds: Land
 *   "{T}, Sacrifice this land: Search your library for a basic land card,
 *    put it onto the battlefield tapped, then shuffle."
 */
class EvolvingWildsScenarioTest : ScenarioTestBase() {

    private fun TestGame.activateEvolvingWilds(): com.wingedsheep.engine.core.ExecutionResult {
        val sourceId = findPermanent("Evolving Wilds")
            ?: error("Evolving Wilds not on battlefield")
        val cardDef = cardRegistry.getCard("Evolving Wilds")!!
        val ability = cardDef.script.activatedAbilities.first()
        return execute(
            ActivateAbility(
                playerId = player1Id,
                sourceId = sourceId,
                abilityId = ability.id,
                targets = emptyList()
            )
        )
    }

    private fun TestGame.libraryCardNames(playerNumber: Int): List<String> {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getLibrary(playerId).mapNotNull {
            state.getEntity(it)?.get<CardComponent>()?.name
        }
    }

    private fun TestGame.findLibraryCard(playerNumber: Int, name: String): com.wingedsheep.sdk.model.EntityId {
        val playerId = if (playerNumber == 1) player1Id else player2Id
        return state.getLibrary(playerId).first { entityId ->
            state.getEntity(entityId)?.get<CardComponent>()?.name == name
        }
    }

    init {
        context("Evolving Wilds activated ability") {

            test("fetches a basic land and puts it onto the battlefield tapped") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Evolving Wilds")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Forest") // opponent needs library
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val activate = game.activateEvolvingWilds()
                withClue("Activation should succeed: ${activate.error}") {
                    activate.error shouldBe null
                }

                game.resolveStack()

                withClue("Should have a SelectCards decision for the search") {
                    game.hasPendingDecision() shouldBe true
                }
                val decision = game.getPendingDecision() as SelectCardsDecision
                withClue("ChooseUpTo(1) should allow 0 selections") {
                    decision.minSelections shouldBe 0
                    decision.maxSelections shouldBe 1
                }

                val forestId = game.findLibraryCard(1, "Forest")
                game.selectCards(listOf(forestId))

                withClue("Evolving Wilds should be in the graveyard (sacrificed as cost)") {
                    game.isInGraveyard(1, "Evolving Wilds") shouldBe true
                }
                withClue("Evolving Wilds should no longer be on the battlefield") {
                    game.findPermanent("Evolving Wilds") shouldBe null
                }

                val fetchedForestId = game.findPermanent("Forest")
                fetchedForestId shouldNotBe null
                withClue("Fetched Forest should enter tapped") {
                    game.state.getEntity(fetchedForestId!!)?.has<TappedComponent>() shouldBe true
                }

                withClue("Library should be shuffled afterwards (Forest moved out, Mountain + Plains remain)") {
                    val remaining = game.libraryCardNames(1).sorted()
                    remaining shouldBe listOf("Mountain", "Plains")
                }
            }

            test("only basic lands are offered (Filters.BasicLand regression)") {
                // Put a non-basic land in the library alongside basics. The search must NOT
                // offer the non-basic. Regression test for PR #47 which used Filters.Land
                // instead of Filters.BasicLand.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Evolving Wilds")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Evolving Wilds") // non-basic land
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.activateEvolvingWilds().error shouldBe null
                game.resolveStack()

                val decision = game.getPendingDecision() as SelectCardsDecision
                val forestId = game.findLibraryCard(1, "Forest")
                val nonBasicId = game.findLibraryCard(1, "Evolving Wilds")

                withClue("Forest (basic) should be a valid search option") {
                    decision.options shouldContain forestId
                }
                withClue("Evolving Wilds (non-basic land) must NOT be a valid search option") {
                    decision.options shouldNotContain nonBasicId
                }

                game.selectCards(listOf(forestId))
            }

            test("player may choose to find no card") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Evolving Wilds")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.activateEvolvingWilds().error shouldBe null
                game.resolveStack()

                withClue("Search decision should be pending") {
                    game.hasPendingDecision() shouldBe true
                }

                // Decline to fetch — minSelections is 0 so empty selection is legal.
                game.skipSelection()

                withClue("Evolving Wilds should still be sacrificed even if no land was chosen") {
                    game.isInGraveyard(1, "Evolving Wilds") shouldBe true
                }
                withClue("No Forest should have entered the battlefield") {
                    game.findPermanent("Forest") shouldBe null
                }
                withClue("Forest should still be in the library (post-shuffle)") {
                    game.libraryCardNames(1) shouldBe listOf("Forest")
                }
            }
        }
    }
}
