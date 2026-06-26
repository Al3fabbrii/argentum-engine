# SDK Effect-Atom Audit (2026-06-26)

A full survey of **all 284 effect types** across the 41 files in
`mtg-sdk/.../scripting/effects/`, rated against the architecture vision in
[`docs/architecture-principles.md`](../docs/architecture-principles.md) §1.5
*Atomic Effect Pipelines*: **complex behaviour composed from small reusable primitives,
so new sets ship with zero new effect code.**

Complements the existing redundancy backlog
[`sdk-reusability-consolidation.md`](sdk-reusability-consolidation.md) — that file is the
canonical migration tracker; this one is the evidence-gathering pass that re-derived the
picture bottom-up (read every definition, grepped real card reuse) and grades each cluster.

Rating scale per effect:

- **A** — exemplary atom: small, composable, reused at scale, well parameterized.
- **B** — solid primitive, minor improvements possible.
- **C** — smell: too specific, overlaps another effect, or should be a parameter/composition.
- **D** — one-off monolith that violates "compose, don't add" — bespoke for one card.

---

## Headline verdict

**The vision is real and largely working — but 284 effect types is itself the finding.**
A small spine of heavily-reused, well-parameterized atoms carries thousands of cards. Layered
on top is a long tail of **single-card monoliths** and **near-duplicate families
distinguished by one field** that the project keeps meaning to fold in. The SDK is roughly
**~75% of the way to its own ideal** — the architecture is sound and the discipline is mostly
applied; the remaining gap is cleanup of known holdouts, not any structural flaw.

The most telling signal: **the codebase's own kdoc repeatedly admits the duplication.**
`CopyTargetSpellOrAbilityEffect` says it "generalizes" two effects that still ship separately;
`ChangeTriggeringObjectTargetsEffect` calls itself "the non-random counterpart of
`ReselectTargetRandomlyEffect`"; `GrantToxicEffect` admits it just emits a `TOXIC_<n>` string
keyword; `GrantStaticAbility`'s kdoc prescribes a `ForEachInGroup`+`Self` composition that
three sibling `*Group` effects ignore. The SDK knows where its smells are; they just haven't
been swept.

---

## Cluster scorecard

| Cluster | Grade | State |
|---|---|---|
| **Control-flow combinators** (ForEach, Gated, Conditional, Modal, Composite) | **A** | Flagship. `ForEachEffect/IterationSpace` and `GatedEffect/Gate` each collapsed a whole wrapper zoo into one executor + one sealed axis; legacy names survive as thin facades. The model. |
| **Library / Draw / Reveal / Pipeline** | **A−** | Gather→Filter→Select→Move spine is exemplary (`GatherCards`+`CardSource`, `FilterCollection`+`CollectionFilter`). Marred by 3 monoliths and central atoms accreting boolean riders. |
| **Mana** | **A−** | `AddMana` + `AddManaOfChoice` plus orthogonal `ManaExpiry`/`ManaRestriction`/`ManaSpellRider` axes are textbook. 4 add-effect variants + 5 fixed `ManaRestriction` objects still owe consolidation. |
| **Damage / Combat / Life** | **B+** | `PreventDamageEffect` ("replaces all previous prevention types with one parametrized type") and `SetSuspected` are best-in-class. Drag: the `CantAttack/CantBlock` ×4 quartet and the redirect family. |
| **Keyword / Type / Transform / Permanent / Tap** | **B** | Anchored by `GrantKeyword` (704 uses), `TapUntap` (`tap:Boolean`), `BecomeCreature`. Largest cluster carries the most filter-variant + single-keyword-grant duplication. |
| **Control / Removal / Player / Protection** | **B** | Strong base atoms (`GainControl`, `Regenerate`, `Sacrifice`). Pervasive *recipient/quality fragmentation*. |
| **Counters / Stats** | **B−** | `AddCounters`/`ModifyStats`/`Proliferate` excellent; `GrantCounterPlacementModifier` is a model "general by construction" atom. ~6 single-card counter-move/distribute one-offs that cross-reference each other in their own kdocs. |
| **Token / Copy / Stack** | **B−** | Contains both the best (`CounterEffect` absorbed 7 types; `ChainCopyEffect`) and the worst (5 `CreateTokenCopy*` fragmented on copy-source; stalled spell-copy merge). |

---

## What's exemplary (proof the vision works)

Every new effect should imitate these — single executor, single sealed "axis," reused at scale:

- **`ForEachEffect` + `IterationSpace`** and **`GatedEffect` + `Gate`** — the two flagship
  unifications. The entire legacy wrapper set (`MayEffect`, `OptionalCost`, `Conditional`
  (234 uses), `IfYouDo`, `MayPayMana/X`, five `ForEach*`) survives only as facades lowering
  onto these.
- **`CounterEffect`** — kdoc proudly names the **7 effects it replaced**.
- **`PreventDamageEffect`** — one parametrized type expresses CoP, Deflecting Palm, Eye for an
  Eye, Samite Ministration.
- **`AddManaOfChoiceEffect`** — replaced 5 legacy mana effects via one `ManaColorSet` + riders.
- **`GrantKeyword`** (704), **`ModifyStats`** (911), **`DealDamage`** (~930),
  **`AddCounters`** (346), **`GatherCards`** (384), **`MoveCollection`** (370) — high-traffic
  leaf atoms.
- **`GrantSuspendEffect`** / **`SetSuspectedEffect`** — pure marker atoms that let
  `MoveToZone`+`AddCounters`+`GrantKeyword` do the real work. Gold standard for compose-don't-add.

---

## The five systemic smells

The duplication isn't random — it falls into five repeating patterns.

### 1. Recipient/quality fragmentation — *same effect, differs only in who/what receives it*

- **Control:** `GainControlByActivePlayer`, `GainControlByMost`, `GiveControlToTargetPlayer`
  → one `GainControl(recipient: ControlRecipient)` (preserve the good `PlayerRankMetric` sealed
  type as a `ControlRecipient.PlayerWithMost`).
- **Grants:** `GrantShroud`/`GrantHexproof`, `GrantHexproofFromChosenColor`/`GrantProtectionFromChosenColor`,
  `GrantFlashToSpells`/`GrantSpellsCantBeCountered` → keyword/quality parameter.
- `GrantToxic` → `GrantKeyword("TOXIC_$n")` facade (the codebase already does exactly this for
  `GrantProtectionFromColor`).

### 2. Filter/`*Group` variants re-implementing a single-target atom

The codebase's own guidance (`GrantStaticAbility` kdoc) says to use `ForEachInGroup`+`Self`.
Offenders ignoring it: `CantAttackGroup`/`CantBlockGroup`, `SetGroupCreatureSubtypes`,
`ChangeGroupColor`, `GrantActivatedAbilityToGroup`, `AddCountersToCollection`.

### 3. "Choose source" fragmentation — *same operation, differs only in how input is selected*

- **Worst single offender:** five `CreateTokenCopy{OfSource,OfTarget,OfChosenPermanent,OfEquippedCreature,Random…}`
  → one `CreateTokenCopy(source: CopySource, mods: CopyModifications)`. `…OfTarget` has grown
  ~20 modifier fields while siblings re-declare uneven subsets.
- The retarget quartet: `ChangeSpellTarget`/`ChangeTarget`/`ReselectTargetRandomly`/`ChangeTriggeringObjectTargets`
  → one `RetargetEffect(scope, chooser, constraint)`.
- The counter-move family: `MoveCounters`/`MoveChosenCountersToTarget`/`MoveCountersEachKindMissing`/`MoveAllLastKnownCounters`
  + the two `Distribute` + `RemoveAll`/`RemoveAnyNumber` → one `MoveCounters` over
  (source × destination × kind-selector × amount).

### 4. Stalled merges — *generalizing type exists, specific siblings still ship*

- Spell-copy family: `CopyTargetSpellOrAbility` already "generalizes" `CopyTargetSpell` +
  `CopyTargetTriggeredAbility`; finish into `CopyOnStackEffect(scope, riders)` (also folds
  `CopyEachTargetSpell`).
- `ChooseOptionEffect(CREATURE_TYPE)` is "the generic replacement" yet legacy
  `ChooseCreatureTypeEffect` still has ~15 uses.
- Near-duplicate pairs: `GrantHarmonize`/`GrantFlashback` (→ `GrantGraveyardCastKeyword`),
  `ChangeCreatureTypeText`/`ChangeWordInText` (→ `ChangeTextWords(category)`),
  `CopyNextSpellCast`/`CopyEachSpellCast` (→ `consumption` param),
  the three coin effects (→ `FlipCoins` + a gate), `SetBasePower`/`SetBasePowerToughness`
  (→ `SetBaseStats(power: DynamicAmount?, toughness: DynamicAmount?)`, also fixes the
  `DynamicAmount`-vs-`Int` asymmetry), `AddCreatureType`/`AddSubtype` (differ only by a
  creature type-check), `ChangeColorToChosen`/`BecomeChosenManaColor` (differ only in context slot).

### 5. True one-card monoliths (the D's) — *bespoke executors that should be compositions*

| Effect | Card | Should be |
|---|---|---|
| `ExileFromTopRepeatingEffect` | Demonlord Belzenlok | `RepeatWhile(GatherUntilMatch → Move)` + damage tail |
| `EachPlayerDiscardsOrLoseLifeEffect` | Strongarm Tactics | `ForEachPlayer(discard)` + conditional life-loss tail |
| `EachPlayerDrawsForDamageDealtToSourceEffect` | Grothama | per-player count `DynamicAmount` + draw |
| `GrantAttackBlockTaxPerCreatureTypeEffect` | Whipgrass Entangler | generic pay-tax grant (raw `String` cost + baked oracle text — worst offender) |
| `GrantToEnchantedCreatureTypeGroupEffect` | Onslaught crowns | Gather(shares-type) → `ForEachInGroup`(ModifyStats + GrantKeyword + GrantProtection) |
| `GrantCastCreaturesFromGraveyardWithForageEffect` | Osteomancer Adept | graveyard-cast permission + forage cost + enters-with-counter |
| `MoveCounters` / `MoveChosenCountersToTarget` / `MoveCountersEachKindMissing` | Tester / Goldberry | the unified `MoveCounters` (see smell #3) |

**Justified singletons (keep as-is):** `OpenLifeBid`, `SecretBid`, `UnlockDoor`,
`ExchangeLifeAndPower`, `HijackNextTurn` — genuine custom decision-loops / distinct
game-actions. These are correct `add-feature` boundaries, not smells.

**Dead code:** `PutOnLibraryPositionOfChoiceEffect` and `PreventLandPlaysThisTurnEffect` have
**zero card references** — confirm live or delete.

---

## A sixth, subtler smell: god-atom creep

The biggest *long-term* risk isn't the monoliths — it's central atoms accreting boolean riders:

- `SelectFromCollection` — `matchChosenCreatureType`, `useTargetingUI`, `showAllCards`, `alwaysPrompt`
- `MoveCollection` — `faceDown`, `linkToSource`, `markEnteredViaSourceAbility`, `addCounterType`, …
- `CreateTokenEffect` — card-type booleans (`artifactToken`/`enchantmentToken`/`legendary`) +
  enter-state (`tapped`/`attacking`) + `exileAtStep`/`sacrificeAtStep`/`colorsFromChoice`
- `GrantMayPlayFromExile` — `withAnyManaType`, `condition`, `landEntersTapped`, `onPlayRider`, …
- `ModalEffect` — `chooseAllIfBlightPaid` (one card's mechanic on the core type)

Each new "...this way" clause becomes a flag instead of a sealed-vocabulary variant. This is the
*gradual* form of the same monolith smell — push these into the sealed types
(`SelectionRestriction`, `CardSource`, `CollectionFilter`, a structured enter-state value) rather
than more flags.

---

## Recommended refactor roadmap (prioritized by payoff)

1. **Collapse the 5 `CreateTokenCopy*` effects** into
   `CreateTokenCopy(source: CopySource, mods: CopyModifications)` — highest fragmentation,
   clearest win, removes ~20 duplicated fields.
2. **Unify the counter-move/distribute family** into one `MoveCounters` over
   (source × destination × kind-selector × amount) — folds ~6 single-card types into one.
3. **`ControlRecipient` parameter on `GainControl`** — kills 3 effect types; preserves
   `PlayerRankMetric`.
4. **Single-keyword grants → `GrantKeyword` facades** (`GrantToxic`, `GrantShroud`,
   `GrantHexproof`) — removes Effect *and* executor with zero card-author churn.
5. **Adopt the project's own `ForEachInGroup`+`Self` rule** for all `*Group` filter-variants.
6. **Finish the stalled merges** — spell-copy family, retarget quartet, the named pairs in
   smell #4.
7. **Decompose the ~7 D-grade monoliths** into pipelines; delete the 2 dead effects.
8. **Push central-atom boolean riders into sealed vocabularies** to halt god-atom creep.
9. **Add a hygiene test** (in the spirit of `FacadeBoundaryTest` / `TapEventEnforcementTest`)
   that flags a new effect type used by ≤1 card, forcing the "can this compose?" question at
   authoring time.

None of this is firefighting — the engine is healthy. It's paying down a known, well-bounded
duplication debt so the effect count drifts *down* even as the card corpus grows, which is the
real test of the compose-don't-add thesis.

---

## Per-cluster detail

The grading below is condensed; each cluster's full per-effect table was produced during the
audit and can be regenerated. Smell-level findings are captured above; this section records the
cluster-specific notes worth keeping.

### Library / Draw / Reveal / Pipeline (A−)

Strongest evidence the vision is real: the Gather (`GatherCardsEffect` + `CardSource`) → Filter
(`FilterCollectionEffect` + `CollectionFilter`) → Select (`SelectFromCollectionEffect`) → Move
(`MoveCollectionEffect`) spine carries hundreds of cards each, and `IfYouDo`/`MayPayMana`/`MayPayX`
lower to a single `GatedEffect`. Edge problems: 3 D-monoliths (`ExileFromTopRepeating`,
`EachPlayerDiscardsOrLoseLife`, `EachPlayerDrawsForDamageDealtToSource`); three `Emit*Event` twins
(scried/surveiled/manifest-dread) → `EmitLibraryActionEvent(kind)`; legacy `ChooseCreatureTypeEffect`
vs `ChooseOptionEffect`; god-atom creep on Select/Move.

### Damage / Combat / Life (B+)

`PreventDamageEffect` (`scope`/`direction`/`sourceFilter`/`onPrevented`/`nextInstanceOnly`) and
`SetSuspectedEffect` (status-flag atom, composed by `Effects.Suspect`) are templates for the module.
Smells: collapse `CantAttack`/`CantAttackGroup`/`CantBlock`/`CantBlockGroup` to two subject-parameterized
effects; fold `RedirectCombatDamageToController` + `ReflectCombatDamage` into `RedirectNextDamage` /
the `onPrevented` reflection idiom; `DividedDamageEffect` carries an awkward dual `totalDamage:Int` +
`dynamicTotal:DynamicAmount?` (collapse to one `DynamicAmount`); `OwnerGainsLife` ≡
`GainLife(target = OwnerOfTarget)`; `PayLife`/`PayDynamicLife` should be one type.

### Counters / Stats (B−)

Healthy core (`AddCounters` 346, `AddDynamicCounters` 36, `Proliferate`, exemplary
`GrantCounterPlacementModifier`). The counter-move/distribute cluster (smell #3) is the headline
refactor; `DoubleCounters` should evaporate into `AddDynamicCounters(amount = CountersOnTarget(type))`
once that `DynamicAmount` exists; `SetBasePower` + `SetBasePowerToughness` → `SetBaseStats`.

### Token / Copy / Stack (B−)

Best: `CounterEffect` (absorbed 7 types), `ChainCopyEffect` ("unified chain copy for all Chain of X"),
`CreatePredefinedTokenEffect` (data-driven, 134+ cards), `CopyCardIntoCollectionEffect`. Worst: the 5
`CreateTokenCopy*` (smell #3), the stalled spell-copy merge (smell #4), the retarget quartet, and
`WardCost` being a parallel cost vocabulary that should re-express over the shared `CostAtom`.

### Control / Removal / Player / Protection (B)

Excellent base atoms (`GainControl`, `Regenerate` 126, `Sacrifice`/`SacrificeSelf` 324, `ForceSacrifice`,
`MoveToZone`, `CreateGlobalTriggeredAbility`, `ChooseColorThen`, `TheRingTemptsYou` 45, split
`AddCombatPhase`/`AddMainPhase`). Dominant smell: recipient/quality fragmentation (smell #1) plus a
fragmented `Skip*` step family (`SkipCombatPhases`/`SkipUntap`/`SkipNextDrawStep`/`SkipNextTurn` →
`SkipStep(kind, count)`) and player-restriction trio (`CantCastSpells`/`CantPlayCardsFromHand`/`CantActivateLoyaltyAbilities`
→ `RestrictPlayer(action)`). Only true D: `GrantCastCreaturesFromGraveyardWithForage`.

### Keyword / Type / Transform / Permanent / Tap (B)

Anchored by `GrantKeyword` (704), `TapUntap` (`tap:Boolean`, 52), `BecomeCreature` (39), the
`Grant{Triggered,Activated,Static}Ability` payload trio. Smells: single-keyword grants duplicating
`GrantKeyword` (smell #1); `*Group` filter-variants (smell #2); near-duplicate pairs (smell #4);
`AnimateLand` ⊂ `BecomeCreature`, `BecomeArtifact` ∥ `BecomeCreature` (extract a `BecomePermanent`
base or make CREATURE optional); lone D = `GrantToEnchantedCreatureTypeGroup`.

### Control-flow combinators (A)

`ForEachEffect/IterationSpace` and `GatedEffect/Gate` are exemplary; base `Effect` interface
(`description`/`runtimeDescription`/`TextReplaceable`/auto-flattening `then`) is minimal and right;
`FaceDownMode` and `SuccessCriterion` are clean extension points. Holdouts: fold
`RepeatDynamicTimesEffect` into `IterationSpace.Times` or `RepeatCondition.NTimes` (two repeat
executors today); lower `PayOrSufferEffect` (41 uses) onto `GatedEffect(Gate.MayPay, otherwise=suffer)`;
converge the three "choose-one-of-N-effects" surfaces (`ModalEffect.chooseOne`, `ChooseActionEffect`,
`BudgetModalEffect`) onto one modal executor.

### Mana + mechanic-specific (A− / mixed)

`AddMana` (268) and `AddManaOfChoice` (replaced 5 legacy effects) are textbook; the three orthogonal
axes `ManaExpiry`/`ManaRestriction`/`ManaSpellRider` are a clean design. Owed: fold `AddColorlessMana`
(nullable `color`), `AddDynamicMana` (`distribution` flag), `AddOneManaOfEachColorAmong` ("one-of-each"
mode), and `AddAnyColorManaSpendOnChosenType` (deferred restriction) into the two add-atoms; replace the
five fixed spell-predicate `ManaRestriction` objects (`InstantOrSorceryOnly`, `CreatureSpellsOnly`,
`LegendarySpellsOnly`, `SpellsMV4OrGreater`, `CreatureMV4OrXCost`) with one `SpellMatching(filter)`.
`GrantSuspendEffect` is the gold standard; `LevelUpClass` is a thin mechanic marker; the bid/guess
mini-games (`OpenLifeBid`, `SecretBid`) are justified `add-feature` boundaries.
