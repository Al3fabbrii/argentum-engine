package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.asArr
import com.wingedsheep.tooling.coverage.compact
import com.wingedsheep.tooling.coverage.jsonContains
import com.wingedsheep.tooling.coverage.strField
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** (targets|null, actions|null) from the first Targeted / ActionList envelope in a subtree.
 *  Shared by spells (SpellActions) and triggered abilities (TriggerA). */
internal fun extractEnvelope(node: JsonElement?): Pair<List<JsonObject>?, List<JsonObject>?> {
    var foundTargets: List<JsonObject>? = null
    var foundActions: List<JsonObject>? = null
    fun walk(n: JsonElement?) {
        when (n) {
            is JsonObject -> {
                val actionsKind = n.strField("_Actions")
                val args = n["args"].asArr
                if (actionsKind == "Targeted" && args != null && args.size >= 2) {
                    if (foundActions == null) {
                        foundTargets = (args[0].asArr)?.filterIsInstance<JsonObject>() ?: emptyList()
                        foundActions = (args[1] as? JsonObject)?.get("args").asArr?.filterIsInstance<JsonObject>() ?: emptyList()
                    }
                } else if (actionsKind == "ActionList" && args != null && foundActions == null) {
                    foundActions = args.filterIsInstance<JsonObject>()
                }
                n.values.forEach { walk(it) }
            }
            is JsonArray -> n.forEach { walk(it) }
            else -> {}
        }
    }
    walk(node)
    return foundTargets to foundActions
}

/** (tdsl, tvar) for an envelope's targets; (null,null) if unrenderable; ("",null) if none. */
internal fun EmitCtx.spellTarget(targets: List<JsonObject>?): Pair<String?, String?> {
    if (targets.isNullOrEmpty()) return "" to null
    if (targets.size > 1) { reasons.add("multi-target"); return null to null }
    val tdsl = targetDsl(targets[0]) ?: run { reasons.add("target:${targets[0].strField("_Target")}"); return null to null }
    return tdsl to "t"
}

private fun EmitCtx.conditionDsl(ifNode: JsonElement?): String? {
    val blob = compact(ifNode)
    if ("ControlsMorePermanentThanPlayer" in blob && "\"Land\"" in blob) { used.add("Conditions"); return "Conditions.OpponentControlsMoreLands" }
    if ("ControlsMorePermanentThanPlayer" in blob && "\"Creature\"" in blob) { used.add("Conditions"); return "Conditions.OpponentControlsMoreCreatures" }
    return null
}

/** Top-level `If{cond}[effect]` -> spell `condition =` gate + the inner effect (Gift of Estates). */
private fun EmitCtx.conditionalSpell(card: JsonObject): List<String>? {
    val (_, actions) = extractEnvelope(card["Rules"])
    if (actions == null || actions.size != 1 || actions[0].strField("_Action") != "If") return null
    val ifNode = actions[0]
    val cond = conditionDsl(ifNode) ?: return null
    val body = ifNode["args"].asArr
    val inner = if (body != null && body.size > 1 && body[1] is JsonArray) (body[1] as JsonArray).filterIsInstance<JsonObject>() else null
    if (inner == null) return null
    val edsl = renderEffectList(inner, null) ?: return null
    return listOf("    spell {", "        condition = $cond", "        effect = $edsl", "    }")
}

internal fun EmitCtx.spellBlock(card: JsonObject): List<String>? {
    // One-line `effect =` shortcuts, then whole-block shortcuts, then the generic envelope path.
    eachplayerMaydraw(card)?.let { return spellOf(it) }
    fluxEffect(card)?.let { return spellOf(it) }
    windsEffect(card)?.let { return spellOf(it) }
    extraTurnEffect(card)?.let { return spellOf(it) }
    distributedSpell(card)?.let { return it }
    balanceEffect(card)?.let { return it }
    conditionalSpell(card)?.let { return it }

    val (targets, actions) = extractEnvelope(card["Rules"])
    if (actions == null) return null
    val (tdsl, tvar) = spellTarget(targets)
    if (tdsl == null) return null
    val edsl = renderEffectList(actions, tvar) ?: return null
    val inner = if (tvar != null) listOf("        val t = target(\"target\", $tdsl)") else emptyList()
    return listOf("    spell {") + inner + listOf("        effect = $edsl", "    }")
}

private fun spellOf(effect: String) = listOf("    spell {", "        effect = $effect", "    }")

private val TRIGGER_SPEC = mapOf(
    "WhenAPermanentEntersTheBattlefield" to "Triggers.EntersBattlefield",
    "WhenACreatureOrPlaneswalkerDies" to "Triggers.Dies",
    "WhenACreatureAttacks" to "Triggers.Attacks",
    "WhenACreatureDealsCombatDamageToAPlayer" to "Triggers.DealsCombatDamageToPlayer",
)

/** A TriggerA rule (self-triggered) -> triggeredAbility { trigger; [target]; effect }. */
internal fun EmitCtx.triggerBlock(rule: JsonObject): List<String>? {
    var spec: String? = null
    for ((mtTrigger, dsl) in TRIGGER_SPEC) {
        if (jsonContains(rule, "_Trigger", mtTrigger) && jsonContains(rule, "_Permanent", "ThisPermanent")) { spec = dsl; break }
    }
    if (spec == null) { reasons.add("trigger-shape"); return null }
    used.add("Triggers")
    val (targets, actions) = extractEnvelope(rule)
    if (actions == null) { reasons.add("trigger-actions"); return null }
    val (tdsl, tvar) = spellTarget(targets)
    if (tdsl == null) return null
    val edsl = renderEffectList(actions, tvar) ?: return null
    val lines = mutableListOf("    triggeredAbility {", "        trigger = $spec")
    if (tvar != null) lines.add("        val t = target(\"target\", $tdsl)")
    lines.addAll(listOf("        effect = $edsl", "    }"))
    return lines
}

/** An Activated / ActivatedWithModifiers rule -> activatedAbility { cost; [target]; effect }. */
internal fun EmitCtx.activatedBlock(rule: JsonObject): List<String>? {
    val args = rule["args"].asArr
    var cost: String? = "AbilityCost.Tap"  // default; refine from the _Cost node
    val costNode = args?.firstOrNull() as? JsonObject
    if (costNode?.strField("_Cost") == "TapPermanent") cost = "AbilityCost.Tap"
    else if (costNode?.strField("_Cost") == "PayMana") cost = null  // mana costs need symbols -> SCAFFOLD
    if (cost == null) { reasons.add("activated-cost"); return null }
    used.add("AbilityCost")
    val (targets, actions) = extractEnvelope(rule)
    if (actions == null) { reasons.add("activated-actions"); return null }
    val (tdsl, tvar) = spellTarget(targets)
    if (tdsl == null) return null
    val edsl = renderEffectList(actions, tvar) ?: return null
    val lines = mutableListOf("    activatedAbility {", "        cost = $cost")
    if (tvar != null) lines.add("        val t = target(\"target\", $tdsl)")
    lines.addAll(listOf("        effect = $edsl", "    }"))
    return lines
}
