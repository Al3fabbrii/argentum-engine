package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.asArr
import com.wingedsheep.tooling.coverage.compact
import com.wingedsheep.tooling.coverage.findInteger
import com.wingedsheep.tooling.coverage.jsonContains
import com.wingedsheep.tooling.coverage.strField
import com.wingedsheep.tooling.coverage.subtypes
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Target / filter recovery — reads the mtgish target vocabulary the coverage map discards and rebuilds
 * the Argentum target/filter DSL. A filter we can't faithfully render returns null → the card drops
 * to SCAFFOLD rather than emitting a confidently-wrong target.
 */
internal fun EmitCtx.creatureFilterDsl(filterNode: JsonElement?): String {
    used.add("TargetFilter")
    var suffix = ""
    val blob = compact(filterNode)
    Regex(""""IsNonColor".*?"_Color":\s*"(\w+)"""").find(blob)?.let {
        used.add("Color"); suffix += ".notColor(Color.${it.groupValues[1].uppercase()})"
    }
    Regex(""""IsColor".*?"_Color":\s*"(\w+)"""").find(blob)?.let {
        used.add("Color"); suffix += ".color(Color.${it.groupValues[1].uppercase()})"
    }
    if (jsonContains(filterNode, "_Permanents", "DoesntHaveAbility") && "\"Flying\"" in blob) {
        used.add("Keyword"); suffix += ".withoutKeyword(Keyword.FLYING)"
    }
    return "TargetFilter.Creature$suffix"
}

private fun targetTypes(args: JsonElement?): Set<String> =
    Regex(""""IsCardtype",\s*"args":\s*"(\w+)"""").findAll(compact(args)).map { it.groupValues[1] }.toSet()

/** Faithful Argentum target DSL, or null if the filter can't be rendered (-> not AUTO). */
internal fun EmitCtx.targetDsl(tnode: JsonObject): String? {
    val ttype = tnode.strField("_Target")
    val args = tnode["args"]
    val countInt = findInteger(tnode)
    if (ttype == "TargetPlayer") {
        return if (jsonContains(tnode, "_Players", "Opponent")) {
            used.add("TargetOpponent"); "TargetOpponent()"
        } else {
            used.add("TargetPlayer"); "TargetPlayer()"
        }
    }
    if (ttype == "AnyTarget" || ttype == "TargetPlayerOrPermanent") {
        used.add("AnyTarget"); return "AnyTarget()"
    }
    if (ttype in setOf("TargetPermanent", "NumberTargetPermanents", "UptoNumberTargetPermanents", "OneOrTwoTargetPermanents")) {
        val types = targetTypes(args)
        if (types == setOf("Creature")) {
            used.add("TargetCreature")
            val parts = mutableListOf("filter = ${creatureFilterDsl(args)}")
            if (ttype in setOf("NumberTargetPermanents", "UptoNumberTargetPermanents") && countInt is Int) parts.add(0, "count = $countInt")
            if (ttype in setOf("UptoNumberTargetPermanents", "OneOrTwoTargetPermanents")) parts.add(0, "optional = true")
            return "TargetCreature(${parts.joinToString(", ")})"
        }
        val singleType = mapOf("Land" to "TargetFilter.Land", "Artifact" to "TargetFilter.Artifact", "Enchantment" to "TargetFilter.Enchantment")
        if (types.size == 1 && types.first() in singleType) {
            used.addAll(listOf("TargetPermanent", "TargetFilter"))
            return "TargetPermanent(filter = ${singleType[types.first()]})"
        }
        if (types.isEmpty() && "IsCardtype" !in compact(args)) {
            used.add("TargetPermanent"); return "TargetPermanent()"
        }
        if (types.isNotEmpty() && types.all { it in setOf("Creature", "Land", "Artifact", "Enchantment") }) {
            used.add("TargetPermanent"); return "TargetPermanent()"  // multi-type Or — broad, review-flagged
        }
        return null  // unusual filters: not rendered yet -> SCAFFOLD
    }
    if (ttype == "TargetSpell") { used.add("TargetSpell"); return "TargetSpell()" }
    if (ttype == "TargetGraveyardCard") {
        used.addAll(listOf("TargetObject", "TargetFilter"))
        val blob = compact(args)
        val filt = if ("\"Creature\"" in blob) {
            if ("\"You\"" in blob) "TargetFilter.CreatureInYourGraveyard" else "TargetFilter.CreatureInGraveyard"
        } else "TargetFilter.CardInGraveyard"
        return "TargetObject(filter = $filt)"
    }
    return null
}

/** Best-effort GroupFilter for mass effects (semantic exactness is review territory). */
internal fun EmitCtx.groupFilterDsl(filterNode: JsonElement?): String {
    used.add("GroupFilter")
    val blob = compact(filterNode)
    return when {
        "\"Creature\"" in blob -> "GroupFilter.AllCreatures"
        "\"Land\"" in blob -> "GroupFilter.AllLands"
        else -> "GroupFilter.AllPermanents"
    }
}

internal fun EmitCtx.landSearchFilterDsl(filterNode: JsonElement?): String {
    used.add("GameObjectFilter")
    val subs = subtypes(filterNode)
    if (subs.isNotEmpty()) return "GameObjectFilter.Land.withSubtype(\"${subs[0]}\")"
    val blob = compact(filterNode)
    return when {
        "\"Land\"" in blob -> "GameObjectFilter.Land"
        "\"Creature\"" in blob -> "GameObjectFilter.Creature"
        else -> "GameObjectFilter.Any"
    }
}
