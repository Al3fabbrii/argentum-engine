package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.components.battlefield.CastChoicesComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.conditions.SneakCostWasPaid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Tests for the Sneak [cost] keyword (CR 702.190, Teenage Mutant Ninja Turtles).
 *
 * "Sneak [cost]" — *"Any time you could cast an instant during your declare blockers step, you may
 * cast this spell by paying [cost] and returning an unblocked creature you control to its owner's
 * hand rather than paying this spell's mana cost."* (702.190a) A permanent spell whose sneak cost
 * was paid enters tapped and attacking the same defender the returned creature was attacking
 * (702.190b).
 *
 * Exercised through an inline sneak creature; the vanilla attacker provides the unblocked attacker
 * to return.
 */
class SneakTest : FunSpec({

    // A creature with Sneak {1}{G}; full mana cost {4}{G}.
    val sneakNinja = card("Sneaky Ninja") {
        manaCost = "{4}{G}"
        typeLine = "Creature — Turtle Ninja"
        power = 3
        toughness = 3
        sneak("{1}{G}")
    }

    // A vanilla creature that declares as the unblocked attacker to return.
    val vanillaAttacker = card("Plain Brawler") {
        manaCost = "{1}{G}"
        typeLine = "Creature — Bear"
        power = 2
        toughness = 2
    }

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all + listOf(sneakNinja, vanillaAttacker))
        return driver
    }

    /** Advance to the declare blockers step with [attackerCreature] declared, unblocked. */
    fun GameTestDriver.openSneakWindow(
        attacker: EntityId,
        defender: EntityId,
        attackerCreature: EntityId
    ) {
        passPriorityUntil(Step.DECLARE_ATTACKERS)
        declareAttackers(attacker, listOf(attackerCreature), defender).isSuccess shouldBe true
        passPriorityUntil(Step.DECLARE_BLOCKERS)
        // Defender declares no blockers, leaving the attacker unblocked (CR 509.1h).
        declareBlockers(defender, emptyMap()).isSuccess shouldBe true
        // Hand priority to the active player so they can cast during this step (CR 509.1).
        var guard = 0
        while (state.priorityPlayerId != null && state.priorityPlayerId != attacker &&
            state.step == Step.DECLARE_BLOCKERS && guard++ < 4
        ) {
            passPriority(state.priorityPlayerId!!)
        }
    }

    test("cast for sneak: pay mana + return an unblocked attacker; permanent enters tapped and attacking") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), startingLife = 20)
        val attacker = driver.activePlayer!!
        val defender = driver.getOpponent(attacker)

        val brawler = driver.putCreatureOnBattlefield(attacker, "Plain Brawler")
        driver.removeSummoningSickness(brawler)
        val ninja = driver.putCardInHand(attacker, "Sneaky Ninja")

        driver.openSneakWindow(attacker, defender, brawler)

        // Pay the sneak mana ({1}{G}) from pool and return the unblocked Brawler to hand.
        driver.giveMana(attacker, Color.GREEN, 2)
        val castResult = driver.submit(
            CastSpell(
                playerId = attacker,
                cardId = ninja,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.SNEAK,
                additionalCostPayment = AdditionalCostPayment(bouncedPermanents = listOf(brawler)),
                paymentStrategy = PaymentStrategy.FromPool
            )
        )
        io.kotest.assertions.withClue("error=${castResult.error} pendingDecision=${castResult.pendingDecision}") {
            castResult.isSuccess shouldBe true
        }
        while (driver.state.stack.isNotEmpty()) driver.bothPass()

        // The Brawler was returned to its owner's hand as part of the cost.
        driver.getHand(attacker) shouldContain brawler
        driver.findPermanent(attacker, "Plain Brawler") shouldBe null

        // The Ninja resolved onto the battlefield tapped and attacking the same defender.
        val ninjaPerm = driver.findPermanent(attacker, "Sneaky Ninja")
        ninjaPerm.shouldNotBeNull()
        driver.state.getEntity(ninjaPerm)?.has<TappedComponent>() shouldBe true
        val attacking = driver.state.getEntity(ninjaPerm)?.get<AttackingComponent>()
        attacking.shouldNotBeNull()
        attacking.defenderId shouldBe defender

        // Its sneak cost was paid — both the durable flag and the condition agree.
        driver.state.getEntity(ninjaPerm)
            ?.get<CastChoicesComponent>()
            ?.chosen
            ?.containsKey(ChoiceSlot.SNEAK) shouldBe true
        ConditionEvaluator().evaluate(
            driver.state,
            SneakCostWasPaid,
            EffectContext(sourceId = ninjaPerm, controllerId = attacker, opponentId = driver.getOpponent(attacker))
        ).shouldBeTrue()
    }

    test("sneak cast deals combat damage as part of the same combat") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), startingLife = 20)
        val attacker = driver.activePlayer!!
        val defender = driver.getOpponent(attacker)

        val brawler = driver.putCreatureOnBattlefield(attacker, "Plain Brawler")
        driver.removeSummoningSickness(brawler)
        val ninja = driver.putCardInHand(attacker, "Sneaky Ninja")

        driver.openSneakWindow(attacker, defender, brawler)
        driver.giveMana(attacker, Color.GREEN, 2)
        driver.submit(
            CastSpell(
                playerId = attacker,
                cardId = ninja,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.SNEAK,
                additionalCostPayment = AdditionalCostPayment(bouncedPermanents = listOf(brawler)),
                paymentStrategy = PaymentStrategy.FromPool
            )
        ).isSuccess shouldBe true
        while (driver.state.stack.isNotEmpty()) driver.bothPass()

        // The 3/3 Ninja entered attacking and the bounced Brawler no longer deals damage:
        // 20 - 3 = 17.
        driver.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        driver.assertLifeTotal(defender, 17)
    }

    test("sneak is offered as a legal action only inside the declare blockers window") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), startingLife = 20)
        val attacker = driver.activePlayer!!
        val defender = driver.getOpponent(attacker)

        val brawler = driver.putCreatureOnBattlefield(attacker, "Plain Brawler")
        driver.removeSummoningSickness(brawler)
        driver.putCardInHand(attacker, "Sneaky Ninja")
        driver.giveMana(attacker, Color.GREEN, 2)

        val enumerator = LegalActionEnumerator.create(driver.cardRegistry)
        fun sneakActions() = enumerator.enumerate(driver.state, attacker)
            .mapNotNull { it.action as? CastSpell }
            .filter { it.alternativeCostType == AlternativeCostType.SNEAK }

        // Main phase: no sneak option (it's not your declare blockers step).
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        sneakActions().isEmpty().shouldBeTrue()

        // Declare blockers with the Brawler unblocked: the sneak option appears.
        driver.openSneakWindow(attacker, defender, brawler)
        sneakActions().isNotEmpty().shouldBeTrue()
    }

    test("cannot cast for sneak outside the declare blockers step") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), startingLife = 20)
        val attacker = driver.activePlayer!!

        val brawler = driver.putCreatureOnBattlefield(attacker, "Plain Brawler")
        driver.removeSummoningSickness(brawler)
        val ninja = driver.putCardInHand(attacker, "Sneaky Ninja")
        driver.giveMana(attacker, Color.GREEN, 2)

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        driver.submitExpectFailure(
            CastSpell(
                playerId = attacker,
                cardId = ninja,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.SNEAK,
                additionalCostPayment = AdditionalCostPayment(bouncedPermanents = listOf(brawler)),
                paymentStrategy = PaymentStrategy.FromPool
            )
        )
    }

    test("normal cast is unaffected: enters untapped, not attacking, sneak flag false") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Forest" to 40), startingLife = 20)
        val attacker = driver.activePlayer!!

        val ninja = driver.putCardInHand(attacker, "Sneaky Ninja")
        // Pay the full {4}{G}.
        driver.giveMana(attacker, Color.GREEN, 5)

        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)
        driver.castSpell(attacker, ninja).isSuccess shouldBe true
        while (driver.state.stack.isNotEmpty()) driver.bothPass()

        val ninjaPerm = driver.findPermanent(attacker, "Sneaky Ninja")
        ninjaPerm.shouldNotBeNull()
        driver.state.getEntity(ninjaPerm)?.has<TappedComponent>() shouldBe false
        driver.state.getEntity(ninjaPerm)?.get<AttackingComponent>() shouldBe null
        ConditionEvaluator().evaluate(
            driver.state,
            SneakCostWasPaid,
            EffectContext(sourceId = ninjaPerm, controllerId = attacker, opponentId = driver.getOpponent(attacker))
        ).shouldBeFalse()
    }
})
