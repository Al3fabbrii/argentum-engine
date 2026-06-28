package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.mechanics.layers.StateProjector
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.definitions.fin.cards.PuPuUFO
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Behavioural coverage for a batch of Final Fantasy cards implemented from existing SDK
 * primitives (no new engine code):
 *
 *  - Paladin's Arms — Job-select Equipment granting +2/+1, ward {1}, and Knight.
 *  - Machinist's Arsenal — Job-select Equipment granting a dynamic +2/+2 per artifact you
 *    control and Artificer.
 *  - Astrologian's Planisphere — Job-select Equipment granting Wizard and a triggered ability
 *    that puts a +1/+1 counter on the equipped creature when its controller casts a noncreature
 *    spell.
 *  - PuPu UFO — flier whose {3} sets its base power to the number of Towns you control, and
 *    whose {T} puts a land from hand onto the battlefield.
 */
class FinJobSelectBatchScenarioTest : ScenarioTestBase() {

    private val stateProjector = StateProjector()

    init {
        context("Paladin's Arms") {
            test("ETB makes a Hero token that is a 3/2 Knight with ward {1}") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardInHand(1, "Paladin's Arms")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Paladin's Arms")
                withClue("Casting should succeed: ${cast.error}") { cast.error shouldBe null }
                if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
                game.resolveStack()

                val hero = game.findPermanent("Hero Token")!!
                val projected = stateProjector.project(game.state)
                withClue("Equipped Hero is 1/1 + (+2/+1) = 3/2") {
                    projected.getPower(hero) shouldBe 3
                    projected.getToughness(hero) shouldBe 2
                }
                withClue("Equipped Hero is a Knight in addition to Hero, and has ward") {
                    projected.hasSubtype(hero, "Knight") shouldBe true
                    projected.hasSubtype(hero, "Hero") shouldBe true
                    projected.hasKeyword(hero, Keyword.WARD) shouldBe true
                }
            }
        }

        context("Machinist's Arsenal") {
            test("equipped Hero gets +2/+2 for each artifact its controller controls") {
                // One other artifact (Coral Sword) already in play; casting the Arsenal makes the
                // second artifact, so 2 artifacts → +4/+4 on the 1/1 Hero token = 5/5.
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardInHand(1, "Machinist's Arsenal")
                    .withCardOnBattlefield(1, "Coral Sword")
                    .withLandsOnBattlefield(1, "Plains", 5)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Machinist's Arsenal")
                withClue("Casting should succeed: ${cast.error}") { cast.error shouldBe null }
                if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
                game.resolveStack()

                val hero = game.findPermanent("Hero Token")!!
                val projected = stateProjector.project(game.state)
                withClue("2 artifacts (Coral Sword + Arsenal) → 1/1 + (+4/+4) = 5/5") {
                    projected.getPower(hero) shouldBe 5
                    projected.getToughness(hero) shouldBe 5
                }
                withClue("Equipped Hero is an Artificer in addition to Hero") {
                    projected.hasSubtype(hero, "Artificer") shouldBe true
                }
            }
        }

        context("Astrologian's Planisphere") {
            test("equipped Hero is a Wizard and gains a +1/+1 counter when its controller casts a noncreature spell") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardInHand(1, "Astrologian's Planisphere")
                    .withCardInHand(1, "Shock")
                    .withLandsOnBattlefield(1, "Island", 2)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardOnBattlefield(2, "Hill Giant", summoningSickness = false)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val cast = game.castSpell(1, "Astrologian's Planisphere")
                withClue("Casting should succeed: ${cast.error}") { cast.error shouldBe null }
                if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
                game.resolveStack()

                val hero = game.findPermanent("Hero Token")!!
                val before = stateProjector.project(game.state)
                withClue("Equipped Hero is a Wizard, base 1/1 before any noncreature spell") {
                    before.hasSubtype(hero, "Wizard") shouldBe true
                    before.getPower(hero) shouldBe 1
                    before.getToughness(hero) shouldBe 1
                }

                val giant = game.findPermanent("Hill Giant")!!
                val shock = game.castSpell(1, "Shock", giant)
                withClue("Shock should succeed: ${shock.error}") { shock.error shouldBe null }
                if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
                game.resolveStack()

                val after = stateProjector.project(game.state)
                withClue("Casting a noncreature spell puts a +1/+1 counter on the Hero → 2/2") {
                    after.getPower(hero) shouldBe 2
                    after.getToughness(hero) shouldBe 2
                }
            }
        }

        context("PuPu UFO") {
            test("{3} sets its base power to the number of Towns you control") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardOnBattlefield(1, "PuPu UFO", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Capital City", 3)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val ufo = game.findPermanent("PuPu UFO")!!
                val pump = PuPuUFO.activatedAbilities[1].id

                val before = stateProjector.project(game.state)
                withClue("PuPu UFO starts as a 0/4") {
                    before.getPower(ufo) shouldBe 0
                    before.getToughness(ufo) shouldBe 4
                }

                val result = game.execute(
                    ActivateAbility(playerId = game.player1Id, sourceId = ufo, abilityId = pump)
                )
                withClue("Activating the pump should succeed: ${result.error}") { result.error shouldBe null }
                if (game.hasPendingDecision()) game.submitManaSourcesAutoPay()
                game.resolveStack()

                val after = stateProjector.project(game.state)
                withClue("Base power becomes the number of Towns you control (3); toughness unchanged") {
                    after.getPower(ufo) shouldBe 3
                    after.getToughness(ufo) shouldBe 4
                }
            }

            test("{T} puts a land card from hand onto the battlefield") {
                val game = scenario()
                    .withPlayers("Alice", "Bob")
                    .withCardOnBattlefield(1, "PuPu UFO", summoningSickness = false)
                    .withCardInHand(1, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val ufo = game.findPermanent("PuPu UFO")!!
                val putLand = PuPuUFO.activatedAbilities[0].id

                val result = game.execute(
                    ActivateAbility(playerId = game.player1Id, sourceId = ufo, abilityId = putLand)
                )
                withClue("Activating the land-drop should succeed: ${result.error}") { result.error shouldBe null }
                game.resolveStack()

                val decision = game.getPendingDecision()
                decision.shouldBeInstanceOf<SelectCardsDecision>()
                game.selectCards(listOf(decision.options.first()))
                game.resolveStack()

                withClue("The Forest should now be on the battlefield") {
                    game.findPermanent("Forest") shouldNotBe null
                }
                withClue("The Forest left Alice's hand") {
                    game.state.getHand(game.player1Id).count { id ->
                        game.state.getEntity(id)?.get<CardComponent>()?.name == "Forest"
                    } shouldBe 0
                }
            }
        }
    }
}
