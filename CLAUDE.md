# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# McGatekeeper

A Fabric mod for Minecraft 1.21.11 that enforces Ed25519 public-key authentication on offline-mode servers. Players are held in Minecraft's **configuration phase** (before entering the play state) and only released once they produce a valid cryptographic signature over a server-issued nonce. Unrecognized keys wait for an admin `/gate allow` before being admitted.

## Architecture

```
src/
  main/           – server-side (and shared) code
    auth/         – Ed25519 primitives, PendingAuthManager, KeyStore, ServerIdentity
    command/      – /gate allow|reset|list (operator-only, OP level 3)
    config/       – GateConfig: JSON config file loader/saver
    mixin/        – server mixins (see Mixins section below)
    network/      – custom payload types, GateConfigurationTask, ResponseHandler
    Mcgatekeeper.java – ModInitializer: wires everything together

  client/         – client-side code (split source set)
    auth/         – ClientKeyStore (per-server Ed25519 keypairs), ClientAuthState
    network/      – ClientResponseHandler: responds to challenges, handles results
    mixin/client/ – client mixins (connection screen overlay)
    McgatekeeperClient.java – ClientModInitializer
```

### Authentication flow

Authentication happens entirely during Minecraft's **configuration phase** — the player never enters the play state until auth succeeds.

1. Player enters configuration phase → `ServerConfigurationConnectionEvents.CONFIGURE` fires → `GateConfigurationTask` is added to the handler's task queue. Players without the mod are disconnected immediately.
2. `GateConfigurationTask.sendPacket` fires → `PendingAuthManager.register` stores a nonce + handler → `ChallengePayload` is sent (server public key + server signature over the nonce + nonce + timeout).
3. Client receives `ChallengePayload` → verifies server signature (relay-attack protection) → looks up or generates an Ed25519 keypair keyed by the server's public key → signs the message → sends `ResponsePayload` (client public key + signature).
4. `ResponseHandler` verifies the signature against `KeyStore` entries for that player UUID.
   - **Success**: `PendingAuthManager.markInTransition(uuid)` is called first (see below), then `disconnectOthers` kicks any sibling pending or awaiting-admin sessions, then `disconnectDuplicateLogins` kicks any in-play duplicate, and finally `PendingAuthManager.complete` calls `completeTask(GateConfigurationTask.KEY)`, releasing the player into the play state.
   - **Unknown key**: `PendingAuthManager.tryAwaitAdmin` attempts to claim the awaiting-admin slot for this UUID. If another session for the same UUID is already in `pending`, already in `inTransition`, or already awaiting admin, the new connection is rejected immediately — `AwaitingAdminPayload` is only sent when no sibling exists. Admin runs `/gate allow <player> <label>` to register the key and call `complete`.
5. `PendingAuthManager.tick()` (every server tick via `ServerTickEvents.END_SERVER_TICK`): players whose challenge has expired (`authTimeoutSeconds`) are disconnected.
6. If the player disconnects during configuration, `ServerConfigurationConnectionEvents.DISCONNECT` cleans up `PendingAuthManager`.

### In-transition state

`PendingAuthManager.inTransition` is a `ConcurrentHashMap`-backed set of UUIDs that have been authenticated but have not yet appeared in `PlayerManager`. It closes a race between authentication and play-state entry:

- `markInTransition(uuid)` is called **before** `complete(handler)` removes the entry from `pending` and invokes `completeTask`.
- Between `pending.remove` and `ServerPlayConnectionEvents.JOIN`, `PlayerManager.getPlayer` returns `null` for the transitioning UUID. Without `inTransition`, a concurrent sibling connection arriving in this window would see no conflicting `pending` entry and no in-play player, and `tryAwaitAdmin` would incorrectly park it on the awaiting-admin screen — even though the authorised user is about to occupy that UUID slot.
- `tryAwaitAdmin` checks `inTransition.contains(uuid)` and rejects the sibling outright if the UUID is transitioning.
- `clearInTransition(uuid)` is called by `ServerPlayConnectionEvents.JOIN` once the player is safely in `PlayerManager` and the normal `getPlayer` check takes over.

### Why configuration phase instead of packet interception

The configuration phase is a natural hold point: Minecraft doesn't advance the player to the play state until all configuration tasks call `completeTask`. No custom packet interception is needed.

### Key storage

- **Server** (`config/mcgatekeeper/players.json`): maps player UUID → list of `{label, username, publicKey}` entries (raw 32-byte Ed25519 keys, Base64-encoded). Managed via `/gate allow|reset|list`.
- **Client** (OS data directory, outside the instance folder): maps server public key (Base64) → Ed25519 `{privateKey, publicKey, lastKnownAddress}` entries. Keys are generated automatically on first connection to each server; `lastKnownAddress` is updated on every successful authentication from `ServerInfo.address`. The file is stored at `$XDG_DATA_HOME/mcgatekeeper/<hash>/server-keys.json` on Linux (Flatpak-compatible), `~/Library/Application Support/mcgatekeeper/<hash>/server-keys.json` on macOS, and `%APPDATA%/mcgatekeeper/<hash>/server-keys.json` on Windows, where `<hash>` is the first 16 hex digits of SHA-256 of the absolute game directory path. This prevents key leakage when sharing or exporting an instance.
- **Server identity** (`config/mcgatekeeper/server.key`): persisted Ed25519 keypair (JSON). Generated once. The public key identifies the server; the private key signs challenges so the client can detect relay attacks.

### Configuration

`config/mcgatekeeper/config.json` is created on first run with defaults:

```json
{
  "authTimeoutSeconds": 30,
  "replaceOfflineModeWarning": true
}
```

`replaceOfflineModeWarning`: when `true`, the four vanilla offline-mode WARN lines are suppressed and replaced with a single `[mcgatekeeper] INFO: Protected by McGatekeeper!` message.

## Translations

Language files live in `src/main/resources/assets/mcgatekeeper/lang/`. Most are hand-written. The exception is `en_ud.json` (upside-down English), which is **generated** from `en_us.json` — do not edit it directly.

After adding or changing strings in `en_us.json`, regenerate it from the repo root:

```sh
python3 tools/generate_en_ud.py
```

The script (`tools/generate_en_ud.py`) applies Minecraft's character-flip algorithm (reversing each string and mapping each character to its upside-down Unicode equivalent) and writes `en_ud.json` in place. It prints a self-check against three known Minecraft `en_ud` values before writing.

### Adding or updating Pirate (en_pt), Shakespearean (enws), and Anglish (enp)

These are hand-written, using Minecraft's own translations for those languages as style and vocabulary reference, supplemented by the Anglish dictionary for `enp`.

**Locating Minecraft's language files** (PrismLauncher / Flatpak on this machine):

```
~/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/assets/
  indexes/   – one JSON per asset index version (e.g. 17.json for 1.21.x)
  objects/   – actual files, keyed by SHA-1 hash (first two hex chars = subdirectory)
```

To extract a language file, look up its hash in the index and read from `objects/`:

```sh
ASSETS=~/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/assets
INDEX=$ASSETS/indexes/17.json

# Find the hash for a language (e.g. en_pt, enws, enp, en_ud)
python3 -c "import json; d=json.load(open('$INDEX')); print(d['objects']['minecraft/lang/en_pt.json']['hash'])"

# Then read the file (replace <hash> with the result above)
HASH=<hash>
cat $ASSETS/objects/${HASH:0:2}/$HASH | python3 -m json.tool | less
```

Minecraft's language codes for these variants: `en_pt` (Pirate), `enws` (Shakespearean), `enp` (Anglish), `en_ud` (upside-down).

**Vocabulary references for Anglish (`enp`):**
- https://anglish.org/wiki/Anglish — overview and principles
- https://anglish.org/wiki/Helpful_Anglish_Words — common word pairs
- https://wordbook.anglish.org/ — searchable dictionary

Use Minecraft's `enp.json` as the primary style reference (it has established terms for gaming concepts: *webthew* = server, *besitting* = session, *reckoning* = account, *dright* = admin, *dwale* = error, *leaved* = allowed, *shirm* = screen). The wordbook fills gaps for mod-specific vocabulary.

## Build

Requires Java 21. The system default may be Java 17; always pass `JAVA_HOME` explicitly:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew build
```

Output jar is `build/libs/McGatekeeper-<version>.jar`. The remapped, production-ready jar is the one without the `-dev` or `-sources` suffix.

Other useful tasks:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew compileJava   # compile only
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew remapJar       # remap after compile
```

## Exploring Minecraft sources for mixin development

The `game_decomp/` directory contains Yarn-named Minecraft sources decompiled by Fabric Loom. Browse them directly with the Read tool when writing or debugging mixins:

```
game_decomp/
  common/    – server+shared classes  (net/minecraft/server/…, etc.)
  client/    – client-only classes    (net/minecraft/client/…, etc.)
```

All class and method names use Yarn's human-readable names (e.g. `MinecraftDedicatedServer`, `PlayerManager`, `ClientConfigurationNetworkHandler`).

### Generating game_decomp

If the directory is missing, generate it with:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew genSources

ROOT=$(pwd)
COMMON_SRC=$(find .gradle/loom-cache/minecraftMaven/net/minecraft -name "minecraft-common-*-sources.jar" | head -1)
CLIENT_SRC=$(find .gradle/loom-cache/minecraftMaven/net/minecraft -name "minecraft-clientOnly-*-sources.jar" | head -1)

mkdir -p game_decomp/common game_decomp/client
(cd game_decomp/common && jar xf "$ROOT/$COMMON_SRC")
(cd game_decomp/client && jar xf "$ROOT/$CLIENT_SRC")
```

`genSources` takes a few minutes the first time (Vineflower decompiles the full game). The sources JARs land in `.gradle/loom-cache/minecraftMaven/net/minecraft/` with a project-specific hash in the name; the `find` command handles that automatically.

### Intermediary names

Fabric API source jars use intermediary names (`class_XXXX` / `method_XXXXX`) instead of Yarn. These appear when reading Fabric API sources extracted from the Gradle cache. Loom handles the translation at compile time; you rarely need to reference intermediary names directly, but `~/.gradle/caches/fabric-loom/1.21.11/intermediary-v2.tiny` maps them if needed.

## Mixins

All mixins live under `com.thegameratort.mcgatekeeper.mixin` (server) or `com.thegameratort.mcgatekeeper.mixin.client` (client) and are registered in `mcgatekeeper.mixins.json` / `mcgatekeeper.client.mixins.json`.

| Mixin | Target | Purpose |
|---|---|---|
| `MinecraftDedicatedServerMixin` | `MinecraftDedicatedServer` | Redirects the first `isOnlineMode()` call in `setupServer`. When the server is offline and `replaceOfflineModeWarning` is enabled, logs one INFO line and returns `true`, skipping the four vanilla WARN lines. |
| `ServerConfigurationNetworkHandlerAccessor` | `ServerConfigurationNetworkHandler` | `@Accessor` to read the `profile` field (a `GameProfile`) from the configuration handler. Used by `GateConfigurationTask` and `ResponseHandler`. |
| `ServerLoginNetworkHandlerMixin` | `ServerLoginNetworkHandler` | `@Redirect` suppressing `disconnectDuplicateLogins` during `tickVerify`. Vanilla calls it before auth completes; `ResponseHandler` handles the kick once the auth outcome is known. |
| `ConnectScreenMixin` *(client)* | `ConnectScreen` | `@ModifyArg` on the `render` method: replaces the connection status text with a countdown when `ClientAuthState.isAwaitingAdmin()` is true. |
| `ClientCommonNetworkHandlerAccessor` *(client)* | `ClientCommonNetworkHandler` | `@Accessor` to read the `serverInfo` field (a `ServerInfo`). Used by `ClientResponseHandler` to record `ServerInfo.address` as `lastKnownAddress` after each successful authentication. `getCurrentServerEntry()` on `MinecraftClient` cannot be used here because it delegates to the play-phase handler, which is null during the configuration phase. |

### `@Shadow @Final` on records

When shadowing fields of a record (which has a canonical constructor that assigns all finals), do **not** give the shadow field an initializer — use bare `@Shadow @Final`:

```java
@Shadow @Final private PacketListener listener;
```

Adding `= null` causes Mixin to inject a null-assignment into the canonical constructor, which corrupts the field.
