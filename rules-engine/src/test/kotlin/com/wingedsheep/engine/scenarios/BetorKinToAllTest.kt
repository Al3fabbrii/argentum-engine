package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for Betor, Kin to All ({2}{W}{B}{G}, 5/7, Flying):
 * "At the beginning of your end step, if creatures you control have total toughness 10 or
 *  greater, draw a card. Then if ... 20 or greater, untap each creature you control. Then if
 *  ... 40 or greater, each opponent loses half their life, rounded up."
 *
 * The toughness thresholds use `Compare(AggregateBattlefield(SUM, TOUGHNESS), GTE, n)` over
 * projected toughness; the 10 gate is the trigger's intervening "if".
 */
class BetorKinToAllTest : FunSpec({

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), startingLife = 20)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return driver
    }

    fun advanceToEndStep(driver: GameTestDriver, targetPlayer: EntityId) {
        driver.passPriorityUntil(Step.END, maxPasses = 300)
        driver.activePlayer shouldBe targetPlayer
        driver.currentStep shouldBe Step.END
    }

    test("total toughness 10+: the end-step trigger draws a card") {
        val driver = createDriver()
        val me = driver.activePlayer!!

        // Betor (toughness 7) + Hill Giant (toughness 3) = 10 total toughness — clears the draw gate.
        driver.putCreatureOnBattlefield(me, "Betor, Kin to All")
        driver.putCreatureOnBattlefield(me, "Hill Giant")

        val handBefore = driver.state.getZone(ZoneKey(me, Zone.HAND)).size

        advanceToEndStep(driver, me)
        driver.bothPass() // resolve the end-step trigger

        driver.state.getZone(ZoneKey(me, Zone.HAND)).size shouldBe handBefore + 1
    }

    test("total toughness below 10: the intervening if fails, no draw") {
        val driver = createDriver()
        val me = driver.activePlayer!!

        // Betor alone = 7 toughness, under the 10 threshold.
        driver.putCreatureOnBattlefield(me, "Betor, Kin to All")

        val handBefore = driver.state.getZone(ZoneKey(me, Zone.HAND)).size

        advanceToEndStep(driver, me)
        driver.bothPass()

        driver.state.getZone(ZoneKey(me, Zone.HAND)).size shouldBe handBefore
    }
})
