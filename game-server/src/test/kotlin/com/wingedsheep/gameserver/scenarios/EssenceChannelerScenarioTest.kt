package com.wingedsheep.gameserver.scenarios

import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for Essence Channeler — specifically the "When this creature dies,
 * put its counters on target creature you control" trigger.
 *
 * Per the Bloomburrow ruling (2024-07-26): "Essence Channeler's last ability puts
 * all counters that were on Essence Channeler onto the target creature, not just
 * its +1/+1 counters." See <https://github.com/wingedsheep/argentum-engine/issues/41>.
 */
class EssenceChannelerScenarioTest : ScenarioTestBase() {

    init {
        context("Essence Channeler dies trigger") {
            test("moves every counter type — not just +1/+1 — to the chosen creature") {
                val game = scenario()
                    .withPlayers("Player 1", "Player 2")
                    .withCardOnBattlefield(1, "Essence Channeler")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(2, "Mountain", 1)
                    .withCardInHand(2, "Shock")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .withPriorityPlayer(2)
                    .build()

                val essenceChanneler = game.findPermanent("Essence Channeler")
                    ?: error("Essence Channeler should be on the battlefield")
                val grizzlyBears = game.findPermanent("Grizzly Bears")
                    ?: error("Grizzly Bears should be on the battlefield")

                // Stamp Essence Channeler with a mix of counter kinds. Only PLUS_ONE_PLUS_ONE
                // would be carried over by the previous (buggy) implementation.
                // 1 PLUS_ONE_PLUS_ONE keeps Essence Channeler at 3/2 — Shock's 2 damage is
                // exactly lethal under SBA (704.5g). The GOLD and STUN counters don't affect
                // P/T, but the buggy implementation silently dropped them.
                game.state = game.state.updateEntity(essenceChanneler) { container ->
                    container.with(
                        CountersComponent(
                            mapOf(
                                CounterType.PLUS_ONE_PLUS_ONE to 1,
                                CounterType.GOLD to 1,
                                CounterType.STUN to 3,
                            )
                        )
                    )
                }

                // Player 2 zaps Essence Channeler. 2 damage > 1 toughness ⇒ dies.
                game.castSpell(2, "Shock", essenceChanneler)
                game.resolveStack()

                // Dies trigger requires Player 1 to choose a target. Grizzly Bears is the
                // only legal "creature you control" since Essence Channeler is in the
                // graveyard now.
                game.selectTargets(listOf(grizzlyBears))
                game.resolveStack()

                game.isInGraveyard(1, "Essence Channeler") shouldBe true

                val counters = game.state.getEntity(grizzlyBears)?.get<CountersComponent>()
                    ?: error("Grizzly Bears should have counters after the trigger resolves")
                counters.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
                counters.getCount(CounterType.GOLD) shouldBe 1
                counters.getCount(CounterType.STUN) shouldBe 3
            }
        }
    }
}
