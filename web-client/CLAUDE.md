# web-client

React 19 + TS + Zustand + Vite. **Dumb terminal** — no game logic; server is the source of truth for
rules, legal actions, and state.

## Commands

```bash
npm run dev          # Dev server at localhost:5173 (proxies /game and /api to :8080)
npm run build        # Type-check + production build
npm run typecheck    # Type-check only
npm run preview
```

E2E tests live in the sibling `e2e-scenarios/` directory, not here.

## Where to look

- **Frontend architecture, slice layout, WebSocket API, decision/targeting/combat flows**:
  [`../docs/web-client-architecture.md`](../docs/web-client-architecture.md)
- **Client/server payloads**: [`../docs/data-contracts.md`](../docs/data-contracts.md)
- Components under `src/components/` — directory names are the categories (game, decisions, combat,
  ui, animations, etc.). Read directly.

## Load-bearing rules

- **Never compute legal actions in the client.** Use `legalActions` from the server's state update.
  Filtering, validation, and "is this allowed" checks are server-side.
- **Strict TS is on**, including `noUncheckedIndexedAccess` and `exactOptionalPropertyTypes`. Path
  alias `@/` → `src/`.
- **One store, five slices** combined in `src/store/gameStore.ts`. Prefer derived selectors in
  `src/store/selectors.ts` over computing in components.
