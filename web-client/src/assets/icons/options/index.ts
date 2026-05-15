/**
 * Icon mappings for mode-choice options on cards using
 * `EntersWithChoice(ChoiceType.MODE,...)`.
 *
 * Each key is a stable `iconKey` declared on a `ModeOption` in the card
 * definition; the value is the imported SVG URL. Cards that do not supply
 * an `iconKey` get a textual-only choice — see `optionIcon()` below for
 * the lookup helper used by the decision UI.
 */
import dragonsSvgUrl from './dragons.svg'
import khansSvgUrl from './khans.svg'

export const optionIconMap: Record<string, string> = {
  dragons: dragonsSvgUrl,
  khans: khansSvgUrl,
}

/** Look up an option icon URL by `iconKey`, or `null` if none is registered. */
export function optionIcon(iconKey: string | null | undefined): string | null {
  if (!iconKey) return null
  return optionIconMap[iconKey] ?? null
}
