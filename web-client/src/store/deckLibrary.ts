/**
 * Deck library — localStorage-backed list of saved custom decks.
 *
 * Decoupled from the main game store: it has no WebSocket integration and is only consumed
 * by the deck picker. Kept as its own tiny Zustand store so subscribers (DeckPicker, future
 * tournament-deck-submission, etc.) can react to library changes without prop drilling.
 *
 * Storage format is a versioned envelope so we can migrate the shape later without losing
 * users' decks.
 */
import { create } from 'zustand'

export interface SavedDeck {
  id: string
  name: string
  cards: Record<string, number>
  /** Optional — populated by the tournament Custom-Decks flow in a later phase. */
  format?: string
  /** Optional — the set code the deck was built against, if any. */
  setCode?: string
  /**
   * Optional — designated commander for Commander/Brawl/Standard Brawl decks. Stored
   * separately from `cards` (the commander begins in the command zone, not the library).
   * Populated by the deckbuilder when the user marks a row with the crown toggle.
   */
  commander?: string
  updatedAt: number
}

interface DeckLibraryStorage {
  version: 1
  decks: SavedDeck[]
}

const STORAGE_KEY = 'argentum.decks'
const STORAGE_VERSION = 1

interface DeckLibraryState {
  decks: SavedDeck[]
  hydrated: boolean

  hydrate: () => void
  saveDeck: (input: Omit<SavedDeck, 'id' | 'updatedAt'> & { id?: string }) => SavedDeck
  deleteDeck: (id: string) => void
  renameDeck: (id: string, newName: string) => void
  getDeck: (id: string) => SavedDeck | undefined
}

function loadFromStorage(): SavedDeck[] {
  if (typeof window === 'undefined') return []
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as DeckLibraryStorage
    if (parsed?.version !== STORAGE_VERSION || !Array.isArray(parsed.decks)) return []
    return parsed.decks
  } catch {
    return []
  }
}

function persist(decks: SavedDeck[]) {
  if (typeof window === 'undefined') return
  const envelope: DeckLibraryStorage = { version: STORAGE_VERSION, decks }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(envelope))
}

function generateId(): string {
  return `deck-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

/**
 * Merge a designated commander into a card map. Used when reading a `SavedDeck`
 * for gameplay or rendering: storage keeps the commander out of `cards` (per the
 * `SavedDeck.commander` contract — and matching the server's `Deck.cards`
 * convention, CR 903.6a), so any consumer that just wants "the full deck list"
 * needs to put it back.
 *
 * Idempotent — won't double-count if the commander is already in `cards` (e.g.
 * legacy decks saved before the strip-on-save convention landed).
 */
export function mergeCommanderIntoCards(
  cards: Record<string, number>,
  commander: string | null | undefined,
): Record<string, number> {
  if (!commander) return cards
  if (cards[commander]) return cards
  return { ...cards, [commander]: 1 }
}

/**
 * Inverse of [mergeCommanderIntoCards]: subtract the designated commander from
 * a card map. Used at storage / server boundaries where `cards` must exclude the
 * commander. Idempotent — does nothing when the commander isn't present.
 */
export function stripCommanderFromCards(
  cards: Record<string, number>,
  commander: string | null | undefined,
): Record<string, number> {
  if (!commander || !(commander in cards)) return cards
  const next = { ...cards }
  const remaining = (next[commander] ?? 0) - 1
  if (remaining > 0) next[commander] = remaining
  else delete next[commander]
  return next
}

export const useDeckLibrary = create<DeckLibraryState>((set, get) => ({
  decks: [],
  hydrated: false,

  hydrate: () => {
    if (get().hydrated) return
    set({ decks: loadFromStorage(), hydrated: true })
  },

  saveDeck: (input) => {
    const now = Date.now()
    const id = input.id ?? generateId()
    const existing = input.id ? get().decks.find((d) => d.id === input.id) : undefined
    const saved: SavedDeck = {
      id,
      name: input.name,
      cards: input.cards,
      ...(input.format !== undefined ? { format: input.format } : {}),
      ...(input.setCode !== undefined ? { setCode: input.setCode } : {}),
      ...(input.commander !== undefined ? { commander: input.commander } : {}),
      updatedAt: now,
    }
    const decks = existing
      ? get().decks.map((d) => (d.id === id ? saved : d))
      : [...get().decks, saved]
    persist(decks)
    set({ decks })
    return saved
  },

  deleteDeck: (id) => {
    const decks = get().decks.filter((d) => d.id !== id)
    persist(decks)
    set({ decks })
  },

  renameDeck: (id, newName) => {
    const decks = get().decks.map((d) =>
      d.id === id ? { ...d, name: newName, updatedAt: Date.now() } : d
    )
    persist(decks)
    set({ decks })
  },

  getDeck: (id) => get().decks.find((d) => d.id === id),
}))
