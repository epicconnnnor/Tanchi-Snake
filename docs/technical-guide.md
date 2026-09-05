# Technical guide

## Project layout

```text
src/main/java/dev/connor/tanchi_snake/
├── game/       # Board rules, snakes, food, collisions, and scoring
├── room/       # Players, room lifecycle, lobby state, and commands
├── net/        # WebSocket endpoint, connection handling, and room registry
└── loop/       # The fixed-rate game loop

src/main/resources/
├── application.properties
└── static/     # Browser client: HTML, CSS, JavaScript, and fonts

src/test/java/dev/connor/tanchi_snake/
└── ...         # Unit and WebSocket integration tests mirroring main packages
```

## Runtime flow

```text
Browser input → WebSocket handler → command queue → game loop
                                                   ↓
Browser render ← full room state ← room simulation ←┘
```

Socket threads validate and queue client commands. The `GameLoop` is the only code that mutates a room’s `GameState`; it runs every 100 milliseconds, applies queued commands, advances each running room, and broadcasts a fresh state message. That single writer keeps the mutable game model free from locking.

## Rooms and players

Rooms support eight players on a 48 × 48 wrapping board. A room remains available for 20 seconds after its final connection drops. A disconnected player’s seat and snake are retained for a 20-second reconnect window.

Each player has two identifiers:

- A public player ID, included in room state so the browser can locate its snake.
- A private reconnect token, sent only in the join response and stored in the browser session. A reconnect requires this token, so another participant cannot reclaim a seat using an ID visible in the state message.

Rooms are in memory. They are intended for short-lived games and disappear when the application restarts.

## WebSocket endpoint

The browser connects to `/ws`. Client messages use JSON and include the following types:

| Type | Purpose |
| --- | --- |
| `create` | Create a room and join it. |
| `join` | Join a room or reconnect with a private `token`. |
| `ready` | Toggle lobby readiness. |
| `start` | Start a round when sent by the host. |
| `turn` | Change direction using `UP`, `DOWN`, `LEFT`, or `RIGHT`. |
| `rename` | Update the player name. |
| `playAgain` | Return a completed room to its lobby when sent by the host. |
| `leave` | Give up the seat immediately. |

The server replies with `joined`, `state`, or `error` messages. `state` is a full snapshot rather than a delta. With at most eight players on a 48 × 48 board, this keeps the protocol simple and predictable.

## Game rules

Players collect food to gain levels. The first snake to reach level 10 wins. Snakes cannot reverse direction; collisions and stun behaviour are resolved by the server, and the client draws only the state it receives.

## Tests

Unit tests cover room membership, reconnect windows, colour assignment, game rules, and protocol parsing. The WebSocket end-to-end tests start the application on a random local port and exercise a real browser-compatible WebSocket client.
