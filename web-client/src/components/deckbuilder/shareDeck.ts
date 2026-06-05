/**
 * Deck share-link codec — encode a deck into a compact, URL-safe string and back.
 *
 * Sharing is entirely client-side: the whole deck travels inside the link, so there's no
 * server storage, no token to expire, and a link works forever as long as the named cards
 * still exist in the catalog. This mirrors how the deckbuilder already keeps filter/view
 * state in the URL — the deck just rides along in a `?d=` param.
 *
 * ## Wire shape
 * A [SharedDeck] is serialised to a compact JSON payload with short keys (card names dominate
 * the size, so the envelope is kept tiny) and then base64url-encoded so it's safe to drop into
 * a query string without escaping:
 *
 * ```
 * { v: 1, n: name, f?: format, c?: commander, cp?: [set, collector],
 *   d: [ [name, count] | [name, count, set, collector], ... ] }
 * ```
 *
 * Each deck row is a tuple — `[name, count]` for the common name-only case, extended to
 * `[name, count, setCode, collectorNumber]` when the row pins a specific printing. Folding the
 * (sparse) printing pins into the same list keeps the payload flat and avoids a second map.
 *
 * [decodeSharedDeck] treats its input as fully untrusted (it came from a URL someone pasted):
 * it never throws, validates every field, drops malformed rows, and returns `null` rather than
 * a half-built deck when the payload isn't a usable v1 share code.
 */
import type { PrintingRef } from '@/types'

/**
 * The shareable subset of a deck. A near-identity slice of the persisted `SavedDeck`
 * (minus library bookkeeping like `id` / `updatedAt`), so it round-trips to/from both the
 * deckbuilder's working state and a saved deck with no lossy mapping.
 */
export interface SharedDeck {
  name: string
  /** Card name → copies. Excludes the commander (which lives in the command zone, CR 903.6a). */
  cards: Record<string, number>
  /** Optional deck-construction format (e.g. `STANDARD`, `COMMANDER`). */
  format?: string
  /** Optional designated commander name. */
  commander?: string
  /** Optional pinned printing for the commander. */
  commanderPrinting?: PrintingRef
  /** Optional sparse per-card printing pins, keyed by card name. */
  printings?: Record<string, PrintingRef>
}

/** Current share-payload version. Bump only on a breaking wire-shape change. */
const SHARE_VERSION = 1

/** Query-param key the deckbuilder reads/writes a share code under. */
export const SHARE_PARAM = 'd'

// A deck row: name + count, optionally followed by the printing's set code + collector number.
type ShareRow = [string, number] | [string, number, string, string]

interface SharePayload {
  v: number
  n: string
  f?: string
  c?: string
  cp?: [string, string]
  d: ShareRow[]
}

// --- base64url <-> bytes (Unicode-safe via TextEncoder/TextDecoder) ------------------------

function bytesToBase64Url(bytes: Uint8Array): string {
  let binary = ''
  const CHUNK = 0x8000 // chunk so we don't blow the call-stack on String.fromCharCode(...spread)
  for (let i = 0; i < bytes.length; i += CHUNK) {
    binary += String.fromCharCode(...bytes.subarray(i, i + CHUNK))
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function base64UrlToBytes(code: string): Uint8Array {
  const b64 = code.replace(/-/g, '+').replace(/_/g, '/')
  const pad = b64.length % 4 === 0 ? '' : '='.repeat(4 - (b64.length % 4))
  const binary = atob(b64 + pad)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes
}

// --- encode --------------------------------------------------------------------------------

/** Encode a deck into a URL-safe share code. Inverse of [decodeSharedDeck]. */
export function encodeSharedDeck(deck: SharedDeck): string {
  const printings = deck.printings ?? {}
  const rows: ShareRow[] = []
  for (const [name, count] of Object.entries(deck.cards)) {
    if (count <= 0) continue
    const p = printings[name]
    rows.push(p ? [name, count, p.setCode, p.collectorNumber] : [name, count])
  }

  const payload: SharePayload = { v: SHARE_VERSION, n: deck.name, d: rows }
  if (deck.format) payload.f = deck.format
  if (deck.commander) payload.c = deck.commander
  if (deck.commanderPrinting) {
    payload.cp = [deck.commanderPrinting.setCode, deck.commanderPrinting.collectorNumber]
  }

  return bytesToBase64Url(new TextEncoder().encode(JSON.stringify(payload)))
}

/** Build the full shareable deckbuilder URL for a code. */
export function buildShareUrl(origin: string, code: string): string {
  return `${origin}/deckbuilder?${SHARE_PARAM}=${code}`
}

// --- decode --------------------------------------------------------------------------------

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function printingFromTuple(value: unknown): PrintingRef | undefined {
  if (!Array.isArray(value)) return undefined
  const [setCode, collectorNumber] = value
  if (typeof setCode !== 'string' || typeof collectorNumber !== 'string') return undefined
  return { setCode, collectorNumber }
}

/**
 * Decode a share code back into a [SharedDeck]. Returns `null` for anything that isn't a
 * usable v1 payload (bad base64, wrong version, no resolvable cards). Never throws — the
 * input is untrusted URL content.
 */
export function decodeSharedDeck(code: string): SharedDeck | null {
  let raw: unknown
  try {
    raw = JSON.parse(new TextDecoder().decode(base64UrlToBytes(code)))
  } catch {
    return null
  }
  if (!isRecord(raw) || raw.v !== SHARE_VERSION || !Array.isArray(raw.d)) return null

  const cards: Record<string, number> = {}
  const printings: Record<string, PrintingRef> = {}
  for (const row of raw.d) {
    if (!Array.isArray(row)) continue
    const [name, count, setCode, collectorNumber] = row
    if (typeof name !== 'string' || typeof count !== 'number') continue
    if (!Number.isFinite(count) || count <= 0) continue
    cards[name] = (cards[name] ?? 0) + Math.floor(count)
    if (typeof setCode === 'string' && typeof collectorNumber === 'string') {
      printings[name] = { setCode, collectorNumber }
    }
  }
  if (Object.keys(cards).length === 0) return null

  const out: SharedDeck = { name: typeof raw.n === 'string' ? raw.n : '', cards }
  if (Object.keys(printings).length > 0) out.printings = printings
  if (typeof raw.f === 'string') out.format = raw.f
  if (typeof raw.c === 'string') out.commander = raw.c
  const commanderPrinting = printingFromTuple(raw.cp)
  if (commanderPrinting) out.commanderPrinting = commanderPrinting
  return out
}
