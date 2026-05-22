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
    limbo/        – empty; legacy directory from a prior architecture
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
5. `PendingAuthManager.tick()` (every server tick via `ServerTickEvents.END_SERVER_TICK`): players whose challenge has expired (`limboTimeoutSeconds`) are disconnected.
6. If the player disconnects during configuration, `ServerConfigurationConnectionEvents.DISCONNECT` cleans up `PendingAuthManager`.

### In-transition state

`PendingAuthManager.inTransition` is a `ConcurrentHashMap`-backed set of UUIDs that have been authenticated but have not yet appeared in `PlayerManager`. It closes a race between authentication and play-state entry:

- `markInTransition(uuid)` is called **before** `complete(handler)` removes the entry from `pending` and invokes `completeTask`.
- Between `pending.remove` and `ServerPlayConnectionEvents.JOIN`, `PlayerManager.getPlayer` returns `null` for the transitioning UUID. Without `inTransition`, a concurrent sibling connection arriving in this window would see no conflicting `pending` entry and no in-play player, and `tryAwaitAdmin` would incorrectly park it on the awaiting-admin screen — even though the authorised user is about to occupy that UUID slot.
- `tryAwaitAdmin` checks `inTransition.contains(uuid)` and rejects the sibling outright if the UUID is transitioning.
- `clearInTransition(uuid)` is called by `ServerPlayConnectionEvents.JOIN` once the player is safely in `PlayerManager` and the normal `getPlayer` check takes over.

### Why configuration phase instead of packet interception

The configuration phase is a natural hold point: Minecraft doesn't advance the player to the play state until all configuration tasks call `completeTask`. No custom packet interception is needed. The old architecture used limbo packet queuing with two mixins; that has been removed.

### Key storage

- **Server** (`config/mcgatekeeper/players.json`): maps player UUID → list of `{label, username, publicKey}` entries (raw 32-byte Ed25519 keys, Base64-encoded). Managed via `/gate allow|reset|list`.
- **Client** (`config/mcgatekeeper/server-keys.json`): maps server public key (Base64) → Ed25519 `{privateKey, publicKey}` pair. Keys are generated automatically on first connection to each server.
- **Server identity** (`config/mcgatekeeper/server.key`): persisted Ed25519 keypair (JSON). Generated once. The public key identifies the server; the private key signs challenges so the client can detect relay attacks.

### Configuration

`config/mcgatekeeper/config.json` is created on first run with defaults:

```json
{
  "limboTimeoutSeconds": 30,
  "replaceOfflineModeWarning": true
}
```

`replaceOfflineModeWarning`: when `true`, the four vanilla offline-mode WARN lines are suppressed and replaced with a single `[mcgatekeeper] INFO: Protected by McGatekeeper!` message.

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

Fabric Loom downloads Yarn-remapped Minecraft jars into the Gradle cache. These are the jars the compiler sees and are the right place to look when writing mixins.

### Locating the jars

```
~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/
  minecraft-common/<version>/minecraft-common-<version>.jar   ← server+shared classes
  minecraft-clientonly/<version>/minecraft-clientonly-<version>.jar
```

The version string encodes both the MC version and the Yarn build:

```
1.21.11-net.fabricmc.yarn.1_21_11.1.21.11+build.5-v2
```

All class and method names in these jars use Yarn's human-readable names (e.g. `MinecraftDedicatedServer`, `setupServer`, `PlayerManager`).

### Listing classes in a jar

```sh
jar tf ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common/<version>/minecraft-common-<version>.jar \
  | grep -i dedicated
```

### Disassembling a class (javap)

Extract the `.class` file then run `javap`:

```sh
cd /tmp
jar xf ~/.gradle/caches/.../minecraft-common-<version>.jar \
  net/minecraft/server/dedicated/MinecraftDedicatedServer.class

# Signatures only (good for finding method names)
javap -p MinecraftDedicatedServer.class

# Full bytecode
javap -c -p MinecraftDedicatedServer.class
```

**Finding `@Redirect` ordinals** — the `ordinal` field in `@At` counts from 0 among all invocations of the target method *with the same descriptor* within the enclosing method. To count them correctly, extract the method body and grep for the specific invoke instruction:

```sh
javap -c -p /tmp/net/minecraft/server/dedicated/MinecraftDedicatedServer.class \
  | awk '/boolean setupServer/{found=1} found && /^  [a-zA-Z]/ && !/setupServer/{exit} found{print}' \
  | grep "invokevirtual.*isOnlineMode"
```

Each matching line corresponds to an ordinal (0, 1, 2, …) in declaration order. Methods with different descriptors (`warn(String)V` vs `warn(String, Object)V`) have independent ordinal sequences.

**Checking the branch direction** — after finding the call site, look at the instruction immediately after to know what return value skips the block. For the offline-mode check in `setupServer`:

```
509: invokevirtual isOnlineMode()Z
512: ifne 559        ← "if not equal to zero" (i.e. if true), jump to 559
515: getstatic LOGGER
518: ldc "**** SERVER IS RUNNING..."
521: invokeinterface Logger.warn
...
559: ...             ← block is skipped when isOnlineMode() returns true
```

Returning `true` from the redirect causes `ifne` to jump over the block, suppressing all four warns.

**Checking the constant pool owner** — when the invoke instruction references `#NNN`, use `-verbose` to resolve the full class:

```sh
javap -c -p -verbose /tmp/.../MinecraftDedicatedServer.class | grep -A 2 "#643"
# → #643 = Methodref  net/minecraft/server/dedicated/MinecraftDedicatedServer.isOnlineMode:()Z
```

Use this fully-qualified descriptor as the `@At` target string.

### Decompiling a class

`javap` shows bytecode; for source-level reading use a decompiler. CFR is convenient:

```sh
# Download CFR once
curl -L https://github.com/leibnitz27/cfr/releases/latest/download/cfr.jar -o /tmp/cfr.jar

# Decompile a single class from inside the jar
java -jar /tmp/cfr.jar \
  ~/.gradle/caches/.../minecraft-common-<version>.jar \
  --classname net.minecraft.server.dedicated.MinecraftDedicatedServer
```

The output is readable Java that closely matches what Yarn-named sources look like.

### Intermediary names

The `-intermediary` jars use Mojang's obfuscated names remapped to stable `class_XXXX`/`method_XXXXX` intermediary names instead of Yarn. You rarely need these directly — Loom handles the translation — but they are useful if a Yarn mapping is missing and you need to reference the stable intermediary name in a mixin target.

## Mixins

All mixins live under `com.thegameratort.mcgatekeeper.mixin` (server) or `com.thegameratort.mcgatekeeper.mixin.client` (client) and are registered in `mcgatekeeper.mixins.json` / `mcgatekeeper.client.mixins.json`.

| Mixin | Target | Purpose |
|---|---|---|
| `MinecraftDedicatedServerMixin` | `MinecraftDedicatedServer` | Redirects the first `isOnlineMode()` call in `setupServer`. When the server is offline and `replaceOfflineModeWarning` is enabled, logs one INFO line and returns `true`, skipping the four vanilla WARN lines. |
| `ServerConfigurationNetworkHandlerAccessor` | `ServerConfigurationNetworkHandler` | `@Accessor` to read the `profile` field (a `GameProfile`) from the configuration handler. Used by `GateConfigurationTask` and `ResponseHandler`. |
| `ServerLoginNetworkHandlerMixin` | `ServerLoginNetworkHandler` | `@Redirect` suppressing `disconnectDuplicateLogins` during `tickVerify`. Vanilla calls it before auth completes; `ResponseHandler` handles the kick once the auth outcome is known. |
| `ConnectScreenMixin` *(client)* | `ConnectScreen` | `@ModifyArg` on the `render` method: replaces the connection status text with a countdown when `ClientAuthState.isAwaitingAdmin()` is true. |

### `@Shadow @Final` on records

When shadowing fields of a record (which has a canonical constructor that assigns all finals), do **not** give the shadow field an initializer — use bare `@Shadow @Final`:

```java
@Shadow @Final private PacketListener listener;
```

Adding `= null` causes Mixin to inject a null-assignment into the canonical constructor, which corrupts the field.
