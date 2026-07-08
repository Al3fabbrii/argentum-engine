package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.atq.cards.Ornithopter
import com.wingedsheep.mtg.sets.definitions.lci.cards.ShipwreckSentry
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Scenario test for Shipwreck Sentry (LCI) — {1}{U} Human Pirate 3/3, Defender.
 *
 * "As long as an artifact entered the battlefield under your control this turn, this creature
 *  can attack as though it didn't have defender."
 *
 * Exercises [com.wingedsheep.sdk.scripting.CanAttackDespiteDefender] gated by
 * [com.wingedsheep.sdk.dsl.Conditions.ArtifactEnteredBattlefieldThisTurn]. Because the gate
 * is an ETB *event* tracker (not the current battlefield population), the artifact must enter
 * via the real zone-transition path — so the test casts Ornithopter ({0}) from hand rather
 * than placing it directly.
 */
class ShipwreckSentryScenarioTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCards(listOf(ShipwreckSentry, Ornithopter))
        return driver
    }

    test("cannot attack when no artifact entered under your control this turn") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), skipMulligans = true)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val you = driver.activePlayer!!
        val opponent = driver.state.turnOrder.first { it != you }

        val sentry = driver.putCreatureOnBattlefield(you, "Shipwreck Sentry")
        driver.removeSummoningSickness(sentry)

        // The engine skips the declare-attackers step entirely when the active player controls
        // no creature that can legally attack (TurnManager.hasValidAttackers). With only a
        // Defender in play the step would be skipped and we'd never land on DECLARE_ATTACKERS.
        // Add a vanilla creature that *can* attack as a decoy so combat proceeds normally; we
        // then still declare only the Sentry and expect Defender to refuse it.
        val decoy = driver.putCreatureOnBattlefield(you, "Centaur Courser")
        driver.removeSummoningSickness(decoy)

        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)

        val result = driver.declareAttackers(you, listOf(sentry), opponent)
        withClue("Defender must refuse the attack with no artifact having entered this turn") {
            (result.error != null) shouldBe true
        }
        driver.state.getEntity(sentry)?.get<AttackingComponent>().shouldBeNull()
    }

    test("can attack once an artifact entered under your control this turn") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), skipMulligans = true)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val you = driver.activePlayer!!
        val opponent = driver.state.turnOrder.first { it != you }

        val sentry = driver.putCreatureOnBattlefield(you, "Shipwreck Sentry")
        driver.removeSummoningSickness(sentry)

        // Cast Ornithopter ({0}) from hand so it enters via the real zone-transition path,
        // recording an artifact ETB under your control this turn.
        val thopter = driver.putCardInHand(you, "Ornithopter")
        driver.castSpell(you, thopter).isSuccess shouldBe true
        driver.bothPass() // resolve Ornithopter — the artifact enters the battlefield

        driver.passPriorityUntil(Step.DECLARE_ATTACKERS)

        val result = driver.declareAttackers(you, listOf(sentry), opponent)
        withClue("Sentry can attack despite Defender after an artifact entered: ${result.error}") {
            result.error shouldBe null
        }
        withClue("Sentry is now an attacker") {
            driver.state.getEntity(sentry)?.get<AttackingComponent>().shouldNotBeNull()
        }
    }
})
