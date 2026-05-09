/**
 * Public facade for the deckbuilder query language.
 *
 * The deckbuilder calls `parseQuery(text)` once per text change. The result
 * carries both a `predicate` (used to filter the in-memory catalog) and an
 * `errors` array (rendered as inline diagnostics in the search bar).
 *
 * The grammar is documented in `parser.ts` and the supported keys in
 * `matchers.ts` (single source of truth — the help panel reads from
 * `ALL_KEYS`).
 */
import type { CardPredicate, ParseResult } from './types'
import { parse } from './parser'
import { compile } from './evaluate'

export type { CardPredicate, ParseError, ParseResult, AtomNode, Node, Op, Span } from './types'
export { isAdvancedQuery } from './parser'
export { ALL_KEYS, MATCHERS } from './matchers'

const ALWAYS: CardPredicate = () => true

export function parseQuery(query: string): ParseResult {
  const trimmed = query.trim()
  if (!trimmed) {
    return { predicate: ALWAYS, errors: [], warnings: [], ast: null }
  }
  const { ast, errors: parseErrors } = parse(query)
  const { predicate, errors: compileErrors } = compile(ast)
  return {
    predicate,
    errors: [...parseErrors, ...compileErrors],
    warnings: [],
    ast,
  }
}
