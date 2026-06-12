# Web Client Architecture

## Overview

The Argentum Engine web client is an MTG Arena-style 3D browser application built with React and React-Three-Fiber. It follows a **"dumb terminal" architecture** - the client contains no game rules logic, only renders server state and captures player intent.

## Technology Stack

| Technology | Purpose |
|------------|---------|
| React 18+ | UI framework |
| React-Three-Fiber (R3F) | Three.js declarative wrapper |
| @react-three/drei | R3F helpers (Text, OrbitControls, etc.) |
| Zustand | State management |
| Framer Motion 3D | Animations |
| TypeScript 5 | Strict typing |
| Vite | Build tool |

## Architecture Principles

### 1. Dumb Terminal Pattern

The client is purely presentational:
- **No game rules** - Server validates all actions
- **No state computation** - Server sends complete game state
- **Intent capture only** - Client sends player clicks/selections to server

### 2. Server Authority

All game logic lives on the server:
- Client requests actions → Server validates → Server sends new state
- Legal actions list comes from server, not computed locally
- Animation events come from server event stream

### 3. Optimistic UI (Future)

For responsiveness, we may later add:
- Immediate visual feedback on clicks
- Rollback if server rejects action

## Data Flow

```
┌─────────────┐     WebSocket      ┌─────────────┐
│   Server    │◄──────────────────►│   Client    │
│             │                    │             │
│ GameState   │────stateUpdate────►│ Zustand     │
│ Events      │────events─────────►│ Store       │
│ LegalActions│────legalActions───►│             │
│             │                    │             │
│             │◄───submitAction────│ User Click  │
└─────────────┘                    └─────────────┘
```

## WebSocket Protocol

### Connection Flow

1. **Connect** → `{ type: "connect", playerName: "Alice" }`
2. **Connected** ← `{ type: "connected", playerId: "p1" }`
3. **Create/Join** → `{ type: "createGame", deckList: {...} }`
4. **Game Started** ← `{ type: "gameStarted", players: [{ playerId, name, seatIndex, isYou, isAi }, …] }`
   (N-player seat roster from this recipient's perspective; "the opponent" is the non-`isYou` seat
   in a 2-player game)
5. **Mulligan Phase** ↔ `mulliganDecision` / `keepHand` / `mulligan`
6. **Game Loop** ← `stateUpdate` with state, events, legalActions

### Server Messages (ServerMessage)

| Type | Description |
|------|-------------|
| `connected` | Connection confirmed with player ID |
| `gameCreated` | Game created, waiting for opponent |
| `gameStarted` | Both players connected, game beginning |
| `stateUpdate` | Game state + events + legal actions |
| `mulliganDecision` | Player must keep/mulligan |
| `chooseBottomCards` | Player must choose cards for bottom |
| `mulliganComplete` | Mulligan phase finished |
| `gameOver` | Game ended with winner/reason |
| `error` | Error with code and message |

### Client Messages (ClientMessage)

| Type | Description |
|------|-------------|
| `connect` | Connect with player name |
| `createGame` | Create game with deck list |
| `joinGame` | Join game with session ID + deck |
| `submitAction` | Submit a GameAction |
| `keepHand` | Keep current hand |
| `mulligan` | Take a mulligan |
| `chooseBottomCards` | Select cards for library bottom |
| `concede` | Concede the game |

## State Management (Zustand)

### Store Structure

```typescript
interface GameStore {
  // Connection state
  connectionStatus: 'disconnected' | 'connecting' | 'connected';
  playerId: string | null;
  sessionId: string | null;

  // Game state (from server)
  gameState: ClientGameState | null;
  legalActions: LegalActionInfo[];

  // Mulligan state
  mulliganState: MulliganState | null;

  // UI state (local only)
  selectedCardId: EntityId | null;
  targetingMode: TargetingState | null;

  // Animation queue
  pendingEvents: ClientEvent[];

  // Actions
  connect: (playerName: string) => void;
  createGame: (deckList: Record<string, number>) => void;
  joinGame: (sessionId: string, deckList: Record<string, number>) => void;
  submitAction: (action: GameAction) => void;
  selectCard: (cardId: EntityId | null) => void;
}
```

### Selectors

Memoized selectors extract derived state:

```typescript
// Get cards in a specific zone
const selectZoneCards = (zoneId: ZoneId) => (state: GameStore) => ...

// Get legal actions for a card
const selectCardLegalActions = (cardId: EntityId) => (state: GameStore) => ...

// Check if it's the player's turn
const selectIsMyTurn = (state: GameStore) => ...
```

## Component Hierarchy

```
App
├── GameScene (R3F Canvas)
│   ├── Camera
│   ├── Lighting
│   ├── Table
│   ├── OpponentArea
│   │   ├── Hand (face-down)
│   │   ├── Library
│   │   └── Graveyard
│   ├── Battlefield
│   │   ├── OpponentLands
│   │   ├── OpponentCreatures
│   │   ├── PlayerLands
│   │   └── PlayerCreatures
│   ├── Stack
│   ├── PlayerArea
│   │   ├── Hand
│   │   ├── Library
│   │   └── Graveyard
│   └── TargetArrow
├── GameUI (2D overlay)
│   ├── PhaseIndicator
│   ├── LifeCounters
│   ├── ManaPool
│   ├── ActionMenu
│   └── MulliganUI
└── EventEffects
    ├── DamageEffect
    └── DeathEffect
```

## 3D Layout

### Coordinate System

- **X-axis**: Left (-) to Right (+)
- **Y-axis**: Down (-) to Up (+) (height)
- **Z-axis**: Opponent (-) to Player (+)

### Zone Positions

| Zone | Position | Orientation |
|------|----------|-------------|
| Player Hand | (0, 0.5, 4) | Fan layout facing camera |
| Player Lands | (0, 0, 2.5) | Grid layout |
| Player Creatures | (0, 0, 1.5) | Grid layout |
| Stack | (3.5, 0, 0) | Vertical pile |
| Opponent Creatures | (0, 0, -1.5) | Grid layout, rotated 180° |
| Opponent Lands | (0, 0, -2.5) | Grid layout, rotated 180° |
| Opponent Hand | (0, 0.5, -4) | Face-down, card backs |
| Libraries | (±4, 0, ±3) | Stacked pile |
| Graveyards | (±4, 0, ±2) | Spread pile |

### Card Dimensions

- **Standard card**: 2.5" × 3.5" ratio → 0.63 × 0.88 units
- **Scaling**: ~0.8 for hand, ~0.7 for battlefield

## Animation System

### Event Queue Processing

1. Events arrive with state update
2. Events queue in `pendingEvents`
3. `EventProcessor` plays events sequentially
4. Each event type has animation mapping
5. State renders final positions after animations

### Animation Types

| Event | Animation |
|-------|-----------|
| `cardDrawn` | Card slides from library to hand |
| `permanentEntered` | Card moves from hand/stack to battlefield |
| `damageDealt` | Red number popup |
| `creatureDied` | Fade + fall animation |
| `spellCast` | Card moves to stack with glow |
| `permanentTapped` | 90° rotation |

## Interaction System

### Click Handling

1. Raycaster detects card click
2. Check if card has legal actions
3. If single action → execute immediately
4. If multiple actions → show action menu
5. If action needs target → enter targeting mode

### Targeting Mode

1. Action requires target(s)
2. Filter valid targets from state
3. Highlight valid targets with glow
4. User clicks target → add to selection
5. When enough targets → submit action

## Type Mapping

### Backend → Frontend

| Kotlin Type | TypeScript Type |
|-------------|-----------------|
| `EntityId` | `string` (branded) |
| `ZoneId` | `{ type: ZoneType, ownerId?: string }` |
| `Phase` | `enum Phase` |
| `Step` | `enum Step` |
| `Color` | `enum Color` |
| `Keyword` | `enum Keyword` |
| `CounterType` | `enum CounterType` |
| `ClientGameState` | `interface ClientGameState` |
| `ClientCard` | `interface ClientCard` |
| `ServerMessage` | `type ServerMessage = Connected | StateUpdate | ...` |
| `ClientMessage` | `type ClientMessage = Connect | SubmitAction | ...` |
| `GameAction` | `type GameAction = PlayLand | CastSpell | ...` |

## File Structure

```
web-client/
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
└── src/
    ├── main.tsx
    ├── App.tsx
    ├── types/
    │   ├── index.ts
    │   ├── enums.ts
    │   ├── entities.ts
    │   ├── gameState.ts
    │   ├── messages.ts
    │   ├── events.ts
    │   └── actions.ts
    ├── network/
    │   ├── websocket.ts
    │   └── messageHandlers.ts
    ├── store/
    │   ├── gameStore.ts
    │   ├── animationStore.ts
    │   └── selectors.ts
    ├── components/
    │   ├── scene/
    │   │   ├── GameScene.tsx
    │   │   ├── Camera.tsx
    │   │   ├── Lighting.tsx
    │   │   └── Table.tsx
    │   ├── zones/
    │   │   ├── ZoneLayout.tsx
    │   │   ├── Battlefield.tsx
    │   │   ├── Hand.tsx
    │   │   ├── Library.tsx
    │   │   ├── Graveyard.tsx
    │   │   └── Stack.tsx
    │   ├── card/
    │   │   ├── Card3D.tsx
    │   │   ├── CardHighlight.tsx
    │   │   ├── PowerToughnessDisplay.tsx
    │   │   └── CounterDisplay.tsx
    │   ├── targeting/
    │   │   ├── TargetArrow.tsx
    │   │   └── TargetingOverlay.tsx
    │   ├── effects/
    │   │   ├── DamageEffect.tsx
    │   │   └── DeathEffect.tsx
    │   ├── interaction/
    │   │   └── ClickHandler.tsx
    │   ├── ui/
    │   │   ├── GameUI.tsx
    │   │   ├── LifeCounter.tsx
    │   │   ├── ManaPool.tsx
    │   │   ├── PhaseIndicator.tsx
    │   │   └── ActionMenu.tsx
    │   └── mulligan/
    │       └── MulliganUI.tsx
    ├── animation/
    │   ├── AnimatedCard.tsx
    │   ├── EventProcessor.tsx
    │   ├── eventAnimations.ts
    │   └── useCardAnimation.ts
    └── hooks/
        ├── useCardTexture.ts
        ├── useInteraction.ts
        ├── useLegalActions.ts
        └── useTargeting.ts
```

## Development Workflow

### Local Development

```bash
# Start Vite dev server
cd web-client
npm run dev
# Opens http://localhost:5173

# Start game server (separate terminal)
cd game-server
./gradlew bootRun
# WebSocket at ws://localhost:8080/game
```

### Testing

```bash
# Type checking
npm run typecheck

# Build for production
npm run build

# Preview production build
npm run preview
```

## Future Considerations

### Performance Optimization

- Texture atlasing for card images
- Instanced rendering for many cards
- Level-of-detail for distant cards
- WebWorker for animation calculations

### Features to Add

- Card zoom on hover
- Deck builder UI
- Game history replay
- Spectator mode
- Sound effects
- Mobile touch support
