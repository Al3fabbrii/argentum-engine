import { describe, it, expect } from 'vitest'
import {
  encodeSharedDeck,
  decodeSharedDeck,
  buildShareUrl,
  SHARE_PARAM,
  type SharedDeck,
} from './shareDeck'

describe('shareDeck codec', () => {
  it('round-trips a plain name-only deck', () => {
    const deck: SharedDeck = {
      name: 'Mono-Red Aggro',
      cards: { 'Lightning Bolt': 4, 'Mountain': 20, 'Goblin Guide': 4 },
    }
    const decoded = decodeSharedDeck(encodeSharedDeck(deck))
    expect(decoded).toEqual(deck)
  })

  it('round-trips format, commander, and printing pins', () => {
    const deck: SharedDeck = {
      name: 'Atraxa Superfriends',
      cards: { 'Sol Ring': 1, 'Arcane Signet': 1, 'Forest': 10 },
      format: 'COMMANDER',
      commander: 'Atraxa, Praetors’ Voice',
      commanderPrinting: { setCode: 'C16', collectorNumber: '28' },
      printings: { 'Sol Ring': { setCode: 'C21', collectorNumber: '263' } },
    }
    const decoded = decodeSharedDeck(encodeSharedDeck(deck))
    expect(decoded).toEqual(deck)
  })

  it('preserves Unicode and split-card (DFC) names', () => {
    const deck: SharedDeck = {
      name: 'Æther — déjà vu',
      cards: { 'Unholy Annex // Ritual Chamber': 2, 'Lim-Dûl’s Vault': 1 },
    }
    const decoded = decodeSharedDeck(encodeSharedDeck(deck))
    expect(decoded).toEqual(deck)
  })

  it('produces a URL-safe code (no +, /, = or whitespace)', () => {
    const code = encodeSharedDeck({
      name: 'Test',
      cards: { 'Some Very Long Card Name That Compresses Poorly': 4 },
    })
    expect(code).toMatch(/^[A-Za-z0-9_-]+$/)
  })

  it('builds the share URL with the deckbuilder route and param', () => {
    expect(buildShareUrl('https://play.example.com', 'ABC123')).toBe(
      `https://play.example.com/deckbuilder?${SHARE_PARAM}=ABC123`,
    )
  })

  it('returns null for malformed / non-share input', () => {
    expect(decodeSharedDeck('')).toBeNull()
    expect(decodeSharedDeck('not-base64-!!!')).toBeNull()
    // Valid base64url of JSON that isn't a v1 share payload.
    expect(decodeSharedDeck(encodeAsCode({ hello: 'world' }))).toBeNull()
    // Right version but no usable cards.
    expect(decodeSharedDeck(encodeAsCode({ v: 1, n: 'x', d: [] }))).toBeNull()
  })

  it('drops malformed rows but keeps the valid ones', () => {
    const code = encodeAsCode({
      v: 1,
      n: 'Mixed',
      d: [
        ['Good Card', 2],
        ['Zero', 0], // dropped: non-positive count
        ['Bad Count', 'three'], // dropped: count not a number
        [42, 1], // dropped: name not a string
        'nonsense', // dropped: not a tuple
      ],
    })
    expect(decodeSharedDeck(code)).toEqual({ name: 'Mixed', cards: { 'Good Card': 2 } })
  })

  it('sums duplicate rows for the same card name', () => {
    const code = encodeAsCode({
      v: 1,
      n: 'Dupes',
      d: [
        ['Island', 2],
        ['Island', 3],
      ],
    })
    expect(decodeSharedDeck(code)?.cards).toEqual({ Island: 5 })
  })
})

// Encode an arbitrary object the same way the codec does, so tests can craft
// payloads the public encoder would never emit (wrong version, malformed rows).
function encodeAsCode(obj: unknown): string {
  const json = JSON.stringify(obj)
  const bytes = new TextEncoder().encode(json)
  let binary = ''
  for (const b of bytes) binary += String.fromCharCode(b)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}
