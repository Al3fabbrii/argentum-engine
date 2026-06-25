package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec

/**
 * Tests for the `CreaturesAttackYourOpponent` trigger (Party Dude level 3): fires when one or more
 * of the controller's opponents are attacked, and NOT when the controller themself is attacked.
 */
class CreaturesAttackYourOpponentTriggerTest : FunSpec({

    val watcher = card("Test Attack Watcher") {
        manaCost = "{2}"; typeLine = "Creature — Spirit"; power = 0; toughness = 3
        triggeredAbility {
            trigger = Triggers.CreaturesAttackYourOpponent
            effect = Effects.GainLife(3)
        }
    }
    val bear = card("Test Bear") {
        manaCost = "{1}{G}"; typeLine = "Creature — Bear"; power = 2; toughness = 2
    }

    fun createDriver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(TestCards.all + listOf(watcher, bear))
        d.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        return d
    }

    test("fires when the controller attacks an opponent") {
        val d = createDriver()
        val me = d.player1
        val opp = d.player2

        d.removeSummoningSickness(d.putCreatureOnBattlefield(me, "Test Attack Watcher"))
        val attacker = d.putCreatureOnBattlefield(me, "Test Bear")
        d.removeSummoningSickness(attacker)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(me, listOf(attacker), opp)
        d.bothPass() // resolve the watcher's trigger

        d.assertLifeTotal(me, 23) // 20 + 3 from the trigger
    }
})
