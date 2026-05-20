# McKeyGuard — Project Plan

A Fabric server-side mod that restricts server access using Ed25519 public key cryptography.
Only players with a valid keypair registered by an admin can join. All others are held in a
silent limbo state until released or disconnected.

---

## Package

`com.thegameratort.mcgatekeeper`

---

## Architecture Overview

```
mcgatekeeper/
├── McKeyKeeper.java              # Mod entrypoint (implements ModInitializer)
├── command/
│   └── GateCommand.java          # Registers and handles all /gate subcommands
├── network/
│   ├── GateChannels.java         # Defines custom packet channel IDs
│   ├── ChallengeHandler.java     # Sends nonce challenges on player connect
│   └── ResponseHandler.java      # Receives and verifies signed responses
├── auth/
│   ├── KeyStore.java             # Loads/saves players.json (public keys per player)
│   ├── ChallengeStore.java       # Tracks active nonce challenges (in-memory, per session)
│   └── Ed25519Util.java          # Key generation, signing, and verification helpers
├── limbo/
│   └── LimboManager.java         # Tracks players in limbo; suppresses events for them
└── config/
    └── GateConfig.java           # Any server-side config (e.g. limbo timeout)
```

---

## Data Storage

### `config/mcgatekeeper/players.json`

Stores registered public keys per player, keyed by UUID.

```json
{
  "uuid-of-thegameratort": {
    "username": "TheGameratorT",
    "keys": [
      {
        "label": "home-pc",
        "publicKey": "<base64-encoded Ed25519 public key>"
      },
      {
        "label": "laptop",
        "publicKey": "<base64-encoded Ed25519 public key>"
      }
    ]
  }
}
```

- Keyed by **UUID** (not username) to survive username changes
- Username stored alongside for display purposes only
- Public keys are Base64-encoded 32-byte Ed25519 keys

---

## Authentication Flow

```
Client connects
      │
      ▼
Server holds player in Limbo
 - No movement
 - No commands
 - No chat
 - Join message suppressed
      │
      ▼
Server sends Challenge Packet
 - Contains: random 32-byte nonce
      │
      ▼
Client signs nonce with private key
Client sends Response Packet
 - Contains: signature (64 bytes)
      │
      ▼
Server looks up player's stored public keys
Server tries to verify signature against each key
      │
      ├── ✅ Any key matches → Release from Limbo, show join message
      │
      └── ❌ No match / timeout → Kick with "Not authorised"
```

---

## Limbo Behaviour

While a player is in limbo, the server must:

- Cancel all `PlayerMoveEvent` / position packets
- Cancel all incoming chat messages and commands
- Suppress the vanilla join message (`PlayerJoinEvent` — do not broadcast)
- Suppress the player from appearing in the tab list until authenticated
- Kick after a configurable timeout (default: 30 seconds) if no valid response received

---

## Keypair Management (Client-Side Companion Mod)

A small client-side companion mod is needed to:

- Generate an Ed25519 keypair on first launch and store it in `.minecraft/mcgatekeeper/`
  - `private.key` — Base64-encoded private key (never leaves the machine)
  - `public.key` — Base64-encoded public key (sent to server during `/gate allow`)
- Listen for the Challenge Packet from the server
- Sign the nonce with the private key
- Send the Response Packet back

> The client mod does **not** need to be the same mod as the server. It can be a separate
> artifact in the same repository: `mcgatekeeper-client`.

---

## Commands

All commands require **operator level 3** or higher.

### `/gate allow <player> <label>`

- Player must currently be **in limbo** (connected but unauthenticated)
- Stores the player's public key (received during the handshake) under the given label
- Releases the player from limbo
- Broadcasts the join message as if they had just joined normally

### `/gate reset <player>`

- Removes **all** stored keys for that player
- Player will be held in limbo on next connection until re-allowed

### `/gate reset <player> <label>`

- Removes only the key matching the given label
- Other keys for that player remain valid

### `/gate list <player>`

- Lists all stored key labels for that player and their truncated public key fingerprint
- Example output:
  ```
  TheGameratorT — 2 key(s):
    [home-pc]  fp: a1b2c3d4...
    [laptop]   fp: e5f6a7b8...
  ```

---

## Packets (Custom Fabric Networking)

### Server → Client: `mcgatekeeper:challenge`

| Field     | Type      | Description              |
|-----------|-----------|--------------------------|
| `nonce`   | byte[32]  | Random challenge bytes   |

### Client → Server: `mcgatekeeper:response`

| Field       | Type      | Description                          |
|-------------|-----------|--------------------------------------|
| `signature` | byte[64]  | Ed25519 signature of the nonce       |

---

## Dependencies

| Dependency         | Purpose                              |
|--------------------|--------------------------------------|
| Fabric API         | Events, networking, command registry |
| Bouncy Castle      | Ed25519 key generation & verification |

Add Bouncy Castle to `build.gradle`:

```groovy
dependencies {
    implementation 'org.bouncycastle:bcprov-jdk18on:1.78.1'
}
```

---

## Milestones

### Phase 1 — Core Infrastructure
- [ ] Mod entrypoint and Fabric API setup
- [ ] `Ed25519Util`: key generation, sign, verify
- [ ] `KeyStore`: load/save `players.json`
- [ ] `ChallengeStore`: in-memory nonce tracking

### Phase 2 — Limbo System
- [ ] `LimboManager`: track unauthenticated players
- [ ] Suppress join message
- [ ] Block movement, chat, and commands for limbo players
- [ ] Kick on timeout

### Phase 3 — Handshake
- [ ] `GateChannels`: register custom packet channels
- [ ] `ChallengeHandler`: send nonce on player connect
- [ ] `ResponseHandler`: receive signature, verify, release or kick

### Phase 4 — Commands
- [ ] `/gate allow <player> <label>`
- [ ] `/gate reset <player>`
- [ ] `/gate reset <player> <label>`
- [ ] `/gate list <player>`

### Phase 5 — Client Companion Mod
- [ ] Keypair generation and local storage
- [ ] Challenge packet listener
- [ ] Response packet sender

### Phase 6 — Polish
- [ ] Configurable limbo timeout
- [ ] Fingerprint display in `/gate list`
- [ ] Logging of all auth attempts with timestamps
- [ ] README and key setup guide for friends
