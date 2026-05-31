# Tarkir: Dragonstorm — Engine Gap Analysis

Cross-reference of the **252 remaining (unimplemented) TDM cards** against the engine's actual
capabilities (SDK reference + source verification, May 2026). Generated to scope what must be
built before the set can be completed.

**Status:** 50 / 271 implemented (18%). All five wedge clan keywords (Tier 1), every Tier-2
primitive, and every Tier-3 one-off are now built — all engine gaps are closed.
Card list comes from `scripts/card-status --list --set TDM`. Oracle text pulled from Scryfall
(`set:tdm`, 277 printings → 271 unique cards).

## Bottom line

The set is built around **five wedge clan keywords** plus an across-the-board Dragons-matter theme.
Two of the five clan mechanics and the DFC layout are **already supported**; the rest are the real
work. Once the Tier-1 keywords land, the large majority of remaining cards are buildable today
(standard creatures, dual lands, monuments, sieges, exhale/behold spells, planeswalkers).

### Already supported — no new engine work

- **Behold a Dragon** (Temur additional cost) — full handling in `CostHandler` + cast enumerators:
  reveal-from-hand / choose-controlled, optional (`you may behold`) and `behold or pay {1}` forms,
  and the `if a Dragon was beheld` downstream conditional. (Caustic/Dispelling/Molten/Osseous/
  Piercing Exhale, Eshki-adjacent cards, etc.)
- **Omen** double-faced cards (13 Dragon // Omen-spell DFCs) — supported since Bloomburrow; the Omen
  face shuffles its card into the library on resolution. (Bloomvine Regent, Marang River Regent,
  Scavenger Regent, the Stormbrood cycle, …)
- **Sieges** (5 cards) — `EntersWithChoice(ChoiceType.MODE)` gating two ability sets ("As this
  enters, choose [clan A] or [clan B]"). Proven by Outpost Siege. (Barrensteppe / Frostcliff /
  Glacierwood / Hollowmurk / Windcrag / Cori Mountain Monastery siege-style enchantments.)
- **Planeswalkers** — loyalty framework fully supported (many existing PWs incl. a Sarkhan).
  **Ugin, Eye of the Storms**, **Elspeth, Storm Slayer**, **Sarkhan, Dragon Ascendant** need only
  their specific abilities, not new framework.
- **Affinity** (`affinity for creatures`) and **Delve** — `CostCalculator` reductions. (Salt Road
  Packbeast; Teval grants Delve to your spells.)
- Building blocks that several gaps below compose: tapped-and-attacking tokens
  (`ZonePlacement.TappedAndAttacking`), "next end step" delayed triggers
  (`DelayedTriggerTiming.NextEndStep`), distribute-counters, copy-spell-on-stack, mass/dynamic P/T,
  divided damage, exile-until-leaves, impulse `MayPlayFromExile`, keyword counters (flying/first
  strike/lifelink/indestructible/stun/finality), `ExileFromGraveyard` ability cost.

What follows are the **genuine gaps** — elements no current SDK primitive expresses.

> **Update (May 2026):** Tier 1 (all five clan keywords + Decayed) and Tier 2 (keyword counters,
> leaves-graveyard trigger, one-shot counter doubling, group P/T doubling) are **complete**, as are
> all Tier-3 items 11–20. Every identified engine gap is now closed.

---

## Tier 1 — Headline clan keywords (~50 cards, highest leverage) — ✅ **ALL DONE**

Four of the five wedge mechanics plus Harmonize were missing. All are now implemented, unlocking
the overwhelming majority of the set.

1. **Mobilize N** (Mardu, 12 cards) — ✅ **DONE.** *Lowest risk; all building blocks exist.*
   "Whenever this creature attacks, create N tapped and attacking 1/1 red Warrior creature tokens.
   Sacrifice them at the beginning of the next end step." Compose attack trigger +
   `ZonePlacement.TappedAndAttacking` token + `DelayedTriggerTiming.NextEndStep` sacrifice. Needs a
   keyword + a token-shape helper; no new engine subsystem.
   → Zurgo's Vanguard, Voice of Victory, Marshal of the Lost, Shock Brigade, Stadium Headliner,
     War Effort, Sagu Pummeler, Zurgo Thunder's Decree, …

2. **Endure N** (Abzan, 10 cards) — ✅ **DONE.** modal player choice: **put N +1/+1 counters on it OR create an
   N/N white Spirit creature token.** Both halves exist (`AddCounters`, `CreateToken`); needs the
   keyword + the binary choice prompt (and the dynamic N path for "endure X").
   → Anafenza Unyielding Lineage, Descendant of Storms, Inspirited Vanguard, Krumar Initiate, …

3. **Flurry** (Jeskai, 10 cards) — ✅ **DONE.** "Whenever you cast your **second** spell each turn, …"
   The `GameEvent.NthSpellCastEvent` trigger already fired on the Nth cast of the turn (it powers the
   EOE/BLB second-spell cards), so no new trigger type was needed — the gap note above was stale. Added
   the display-only `Keyword.FLURRY` plus a `card { flurry { … } }` builder helper (mirrors
   `prowess()` / `rampage()`) that forces `Triggers.NthSpellCast(2, Player.You)` and prefixes the
   "Flurry — Whenever you cast your second spell each turn," reminder text.
   Implemented: Cori Mountain Stalwart, Devoted Duelist, Jeskai Devotee, Poised Practitioner,
   Wingblade Disciple, Wayspeaker Bodyguard, Equilibrium Adept.
   Still blocked elsewhere: **Taigam, Master Opportunist** (needs Suspend, Tier 3 §14),
   **Cori-Steel Cutter** + **A-Cori-Steel Cutter** (need "create a token, then attach this Equipment
   to it" from a trigger — deferred).

4. **Renew** (Sultai, 12 cards) — ✅ **DONE.** graveyard-activated ability:
   `{cost}, Exile this card from your graveyard: <effect>. Activate only as a sorcery.`
   The SDK **already models** a per-ability `activateFromZone` field and `AbilityCost.ExileFromGraveyard`,
   but `ActivatedAbilityEnumerator.kt:99` only enumerates abilities on **battlefield** permanents.
   **Needs the enumerator extended to surface graveyard-zone activated abilities** + sorcery-timing
   gate. Most architecturally involved of the clan mechanics.
   → Qarsi Revenant, Sage of the Fang, Kheru Goldkeeper, Naga Fleshcrafter, Rot-Curse Rakshasa,
     Lasyd Prowler, Hundred-Battle Veteran, …

5. **Harmonize** (10 cards) — ✅ **DONE.** alternative cast-from-graveyard cost, **may tap a creature to reduce
   that cost by its power (X)**, then exile the spell. Flashback + a Convoke-style power-based
   reduction. Needs a new alternative-cost path (graveyard cast + tap-for-power reduction + exile
   on resolution).
   → Channeled Dragonfire, Glacial Dragonhunt, Mammoth Bellow, Wail of War, …

6. **Decayed** (1 card) — ✅ **DONE.** "can't block; when it attacks, sacrifice it at end of combat." Minor
   keyword; not currently in the Keyword enum.
   → Rot-Curse Rakshasa (also a Renew card).

---

## Tier 2 — Small recurring primitives — ✅ **ALL DONE**

7. **New keyword counter types.** ✅ **DONE.** The engine has flying / first-strike / lifelink / indestructible /
   stun / finality counters, but is **missing `deathtouch`, `trample`, `hexproof`, `decayed`**.
   Needs the `CounterType` constants + their keyword-counter mapping in `StateProjector`.
   → Champion of Dusan (trample), Qarsi Revenant (deathtouch), Kheru Goldkeeper (flying — exists),
     Perennation (hexproof), Rot-Curse Rakshasa (decayed)

8. **Leaves-graveyard trigger.** ✅ **DONE.** "Whenever one or more cards leave your graveyard during your turn."
   A graveyard-departure *condition/tracker* exists, but there is **no `CardsLeftGraveyard` trigger
   event** (only cards-entering and battlefield-only LTB). Needs a new trigger keyed to
   graveyard-exit batching + "once each turn / during your turn" gating.
   → Attuned Hunter, Kishla Skimmer, Kheru Goldkeeper

9. **Double existing +1/+1 counters (one-shot).** ✅ **DONE.** `DoubleCounterPlacement` is a *replacement* on
   future placement, not a one-shot doubling of counters already present.
   → Sage of the Fang

10. ~~**Double power and toughness until end of turn (group).**~~ ✅ **Done.** Modeled as a
    fixed +X/+Y layer-7 modification read per-entity from projected state
    (`DynamicAmount.EntityProperty(EntityReference.IterationEntity, …)`), surfaced as the
    reusable `EffectPatterns.doublePowerAndToughnessForAll(filter, duration)` helper.
    → Roar of Endless Song (and Unnatural Growth, refactored onto the same helper)

---

## Tier 3 — One-off complex cards (each needs unique new functionality)

11. **Copy a card in a zone, then cast the copy.** ✅ **DONE.** Added the atomic
    `CopyCardIntoCollectionEffect` (copy a card in its zone → pipeline collection, Rule 707.12)
    composed with the existing `CastFromCollectionWithoutPayingCostEffect` (wrapped in `MayEffect`)
    — no bespoke executor. Uncast copies are swept by a new Rule 707.10a state-based action
    (`PhantomCardCopiesCheck`). A resolving permanent copy becomes a token, an instant/sorcery
    copy ceases to exist (existing Rule 707.10 paths).
    → **Shiko, Paragon of the Way**

12. **Cost-linked relative mana value target.** ✅ **DONE.** Sacrifice a creature of MV X → return a creature of
    MV **X+1** from graveyard. No new predicate was needed: the existing
    `FilterCollectionEffect` + `CollectionFilter.ManaValueEquals(DynamicAmount)` already evaluate a
    dynamic mana value at resolution, and `DynamicAmount.EntityProperty(EntityReference.Sacrificed(0),
    ManaValue)` reads X off the cost-sacrificed permanent's last-known snapshot (Rule 112.7a). Modeled
    as a resolution-time chain — gather graveyard creatures → keep MV == X+1 → choose one → return —
    rather than a cast-time cost-linked target (target validation runs before cost payment, so
    `EntityReference.Sacrificed` is unresolvable there).
    → **Sidisi, Regent of the Mire**

13. **Choose-and-exile from a target opponent's revealed hand.** ✅ **DONE.** No new SDK was needed —
    the card is a pure pipeline chain (mirrors Deep-Cavern Bat's hand-disruption shape):
    `RevealHandEffect` → `GatherCards` from the opponent's hand → `SelectFromCollection`
    (up to one nonland, `Chooser.Controller`) → `MoveCollection` to exile with `linkToSource = true`.
    The linked LTB payoff is also composed: on `LeavesBattlefield`, re-gather the linked-exiled card
    (it stays exiled — never returned) and create the Spirit via `CreateTokenEffect` with
    `count = VariableReference("…_count")` (0 when nothing was exiled), `dynamicPower/Toughness =
    StoredCardManaValue` (X = the exiled card's mana value), and `controller =
    ControllerOfPipelineTarget` (resolves to the exiled card's owner — an exile-zone card has no
    `ControllerComponent`, so it falls back to `ownerId`).
    → **Severance Priest**

14. **Suspend.** ✅ **DONE.** Time counters on a card in exile + cast-when-last-counter-removed + haste
    payoff. Modeled content-agnostically (CR 702.62): `Effects.Suspend` moves the card to exile, adds N
    time counters, and sets a suspend marker; the engine synthesizes a single owner's-upkeep countdown
    triggered ability (`Suspend.countdownAbility`) for any exiled card carrying that marker, so the same
    machinery serves a card with no printed suspend. The countdown removes a time counter and, when the
    pile empties, grants haste and plays the card for free through the existing
    `CastFromCollectionWithoutPayingCostEffect` pipeline; an intervening-if "has a time counter" gate
    prevents a stale marker from re-casting. Composed from atomics, no bespoke executor.
    → **Taigam, Master Opportunist** (Flurry copies a spell, exiles it with 4 time counters + Suspend)

15. **Event-based "when you next attack this turn" delayed trigger.** ✅ **DONE.** No new trigger
    type was needed — the event-based delayed-trigger machinery (`CreateDelayedTriggerEffect.trigger`
    + `expiry`) already fires a delayed ability on a matching *event*; it was extended with (a) a
    generic `fireOnce` one-shot flag (consumed the first time it fires, then gone — "when you **next**
    … this turn"), and (b) an attack-declaration match branch that delegates to the shared
    `TriggerMatcher` so `Triggers.YouAttack` / `AttackEvent` behave identically to battlefield
    triggers. Composed inline: `CreateDelayedTriggerEffect(trigger = Triggers.YouAttack,
    fireOnce = true, effect = untap each creature you control)`.
    → **All-Out Assault**

16. **Prevent-and-redirect-and-draw.** ✅ **DONE.** Prevent the next damage from a chosen source, then deal the
    *prevented amount* to that source's controller and draw that many cards. No bespoke effect, and
    CR-faithful: the chosen-source prevention shield carries an **arbitrary `onPrevented: Effect?`**
    that resolves as a real triggered ability on the stack. On resolution the engine creates the
    shield **and** a linked event-based delayed trigger (effect = `onPrevented`); when the shield
    prevents an instance it emits an internal `DamagePreventedEvent` that fires only that trigger.
    The payoff is plain effect composition — prevented amount via
    `DynamicAmount.ContextProperty(PREVENTED_DAMAGE_AMOUNT)`, source's controller via
    `EffectTarget.ControllerOfTriggeringEntity` (the pair Tephraderm uses): Deflecting Palm =
    `DealDamage(ControllerOfTriggeringEntity, preventedAmount)`; New Way Forward =
    `Composite(DealDamage(…), DrawCards(preventedAmount))`.
    → **New Way Forward**

17. **Free-cast-from-exile gated by a dynamic MV cap.** ✅ **DONE.** Exile top X of the damaged
    player's library (X = combat damage) and cast any number of those with MV ≤ X for free. The
    dynamic cap is `CollectionFilter.ManaValueAtMost(DynamicAmount.ContextProperty(TRIGGER_DAMAGE_AMOUNT))`;
    the multi-cast itself needed a new primitive,
    `CastAnyNumberFromCollectionWithoutPayingCostEffect`, which casts any number of cards from a
    pipeline collection **during the ability's resolution** — per the rulings the casts can't wait
    until later in the turn and card-type timing is ignored (it reuses
    `CastFromCollectionWithoutPayingCost` per cast through the synthesized-cast path, looped via
    `EffectContinuationRunner`). Chain: gather top X (`Player.TriggeringPlayer`) → exile → keep
    nonland → keep MV ≤ X → cast any number for free.
    → **Kotis, the Fangkeeper**

18. **Per-activation cost reduction by a target's color count.** ✅ **DONE.** Equip cost "costs {1}
    less to activate for each color of the creature it targets." No new cost-reduction machinery was
    needed: `ActivatedAbility.genericCostReduction` already reduces an activated ability's generic
    cost by a `DynamicAmount` evaluated at activation. The gap was that it was evaluated without the
    chosen target. Added the `EntityNumericProperty.ColorCount` numeric property (read off projected
    colors, mirroring `SubtypeCount`) so `DynamicAmounts.targetColorCount()` reads the equip target's
    color count, and threaded `action.targets` into `ActivateAbilityHandler.applyGenericCostReduction`
    so the reduction resolves against the picked target. The legal-action enumerator gates
    affordability on the cheapest reachable cost (largest reduction over the currently-legal targets)
    since no target is chosen at enumeration time, and the handler re-derives the exact per-target cost
    at activation. The `equipAbility(cost, genericCostReduction = …)` DSL form wires it on the card.
    Also added "hexproof from monocolored" (`GrantHexproofFromMonocoloredToGroup` static +
    `HEXPROOF_FROM_MONOCOLORED` projected keyword + a CR-105.2 monocolored-source targeting check) for
    the equipped-creature half of the card.
    → **Dragonfire Blade**

19. **Opponent-scoped continuous cast prohibition.** ✅ **DONE.** "Your opponents can't cast spells
    during your turn." Added the `OpponentsCantCastSpells(onlyDuringYourTurn)` static ability —
    modeled on Mana Maze's `CantCastSpellsSharingColorWithLastCast` (a continuous restriction read at
    cast-legality time, never on the stack). Enforced via one `CastPermissionUtils` helper OR'd into
    the central `cantCastSpells` gate, so it covers every casting zone (hand, flashback/harmonize,
    exile, top of library) uniformly; control is read from projected state. `onlyDuringYourTurn = true`
    gives Voice of Victory; `false` covers Grand Abolisher's cast clause. Deliberately not filtered —
    a "can't cast spells with even mana value" (Void Winnower) prohibition is left as a future sibling.
    → **Voice of Victory**

20. **Sacrifice-a-token as a cost.** ✅ **DONE.** No new SDK was needed — "Sacrifice a token" is the
    existing generic sacrifice cost narrowed by the `GameObjectFilter.Token` filter (`IsToken` →
    `has<TokenComponent>`). `Costs.Sacrifice(GameObjectFilter.Token)` already renders as "Sacrifice a
    token" and is gated by `canPayAbilityCost`, so the ability is only offered (and only payable) when
    a token is on the battlefield; a nontoken permanent can't satisfy it. Fountainport
    (`{2}, {T}, Sacrifice a token: Draw a card`) was already a user. Locked in by
    `HardenedTacticianScenarioTest`, plus a reusable `isToken` flag on the scenario builder's
    `withCardOnBattlefield` so tests can place real tokens.
    → **Hardened Tactician**

---

## Recommended build order

1. ✅ **Mobilize → Endure → Flurry** — every primitive for Mobilize already exists; Endure/Flurry are
   modest. Unlocks ~30 cards quickly.
2. ✅ **Renew** (enumerator extension) + **Harmonize** (new alt-cost) — bigger lifts, ~22 cards.
3. ✅ **Keyword counters + Decayed + leaves-graveyard trigger** (Tier 2) — small, scattered unlocks.
4. ✅ **Tier-3 one-offs** as the relevant legendaries / rares come up. *(complete)*

The clan keywords (Tier 1) cover the bulk of the remaining cards. Behold and Omen already being done
means Temur spells and the 13 DFC dragons are essentially ready once the shared supporting effects
are filled in.
