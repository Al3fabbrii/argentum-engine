package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Reproducer: when Player 1 attacks alone with Goldmeadow Nomad, Thoughtweft Imbuer's
 * "attacks alone" trigger lands on the stack. Without holdPriority, AutoPassManager
 * sees Player 1's own trigger on top of the stack and auto-passes priority — resolving
 * the trigger before Player 1 ever gets a chance to activate Kirol to copy it.
 *
 * This test pins the fix: Kirol's copy ability sets holdPriority = true, so the legal
 * action carries that flag and AutoPassManager keeps priority instead of auto-passing.
 */
class KirolCopyThoughtweftImbuerTest : ScenarioTestBase() {

    init {
        context("Kirol copies Thoughtweft Imbuer's attack-alone trigger") {

            test("Kirol's activation has holdPriority and successfully copies the trigger") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Kirol, Attentive First-Year")  // 3/3 untapped
                    .withCardOnBattlefield(1, "Thoughtweft Imbuer")           // Kithkin Advisor (0/5)
                    .withCardOnBattlefield(1, "Goldmeadow Nomad")             // Kithkin Scout (1/2) - attacker
                    .withCardOnBattlefield(1, "Llanowar Elves")               // 1/1 - extra tap fodder
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                    .build()

                game.declareAttackers(mapOf("Goldmeadow Nomad" to 2))

                withClue("Thoughtweft Imbuer's attack-alone trigger should be on the stack") {
                    game.state.stack.size shouldBe 1
                }
                val triggerEntityId = game.state.stack.last()

                // Verify Kirol's activation appears in legal actions AND carries holdPriority,
                // so AutoPassManager won't pass priority past P1's own trigger.
                val enumerator = LegalActionEnumerator.create(cardRegistry)
                val legalActions = enumerator.enumerate(game.state, game.player1Id)
                val kirolId = game.findPermanent("Kirol, Attentive First-Year")!!
                val kirolActivation = legalActions.find { la ->
                    val act = la.action
                    act is ActivateAbility && act.sourceId == kirolId
                }
                withClue("Kirol's activation should be enumerated as a legal action") {
                    (kirolActivation != null) shouldBe true
                }
                withClue("Kirol's activation must set holdPriority so AutoPassManager keeps priority") {
                    kirolActivation!!.holdPriority shouldBe true
                }

                // Sanity: actually activating Kirol still copies the trigger correctly.
                val kirolDef = cardRegistry.getCard("Kirol, Attentive First-Year")!!
                val copyAbility = kirolDef.script.activatedAbilities[0]
                val imbuerId = game.findPermanent("Thoughtweft Imbuer")!!
                val elvesId = game.findPermanent("Llanowar Elves")!!

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = kirolId,
                        abilityId = copyAbility.id,
                        targets = listOf(ChosenTarget.Spell(triggerEntityId)),
                        costPayment = AdditionalCostPayment(tappedPermanents = listOf(imbuerId, elvesId))
                    )
                )
                withClue("Activation should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                withClue("Stack should have original trigger + copy") {
                    game.state.stack.size shouldBe 2
                }

                game.resolveStack()

                // Goldmeadow Nomad (1/2 base) attacked alone; 2 Kithkin (Imbuer + Nomad) → X = 2.
                // Original trigger: +2/+2. Copy: +2/+2. Total: 1+4 = 5 power, 2+4 = 6 toughness.
                val nomadId = game.findPermanent("Goldmeadow Nomad")!!
                val projected = game.state.projectedState
                withClue("Goldmeadow Nomad should be buffed by both the original and the copy") {
                    projected.getPower(nomadId) shouldBe 5
                    projected.getToughness(nomadId) shouldBe 6
                }
            }

            test("Kirol does NOT hold priority when stack is empty") {
                // No trigger on the stack, no reason to hold priority — the legal-action
                // enumerator should not flag Kirol's activation with holdPriority.
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Kirol, Attentive First-Year")
                    .withCardOnBattlefield(1, "Thoughtweft Imbuer")
                    .withCardOnBattlefield(1, "Goldmeadow Nomad")
                    .withCardOnBattlefield(1, "Llanowar Elves")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .withPriorityPlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Without the trigger on stack, Kirol's activation isn't even a legal
                // action (no valid target), so we just verify it isn't there.
                val enumerator = LegalActionEnumerator.create(cardRegistry)
                val legalActions = enumerator.enumerate(game.state, game.player1Id)
                val kirolId = game.findPermanent("Kirol, Attentive First-Year")!!
                val kirolActivation = legalActions.find { la ->
                    val act = la.action
                    act is ActivateAbility && act.sourceId == kirolId
                }
                withClue("Kirol's activation should not be enumerated when no trigger is on the stack") {
                    (kirolActivation == null) shouldBe true
                }
            }
        }
    }
}
