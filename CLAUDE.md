# McGatekeeper

A Fabric mod for Minecraft 1.21.11 that enforces Ed25519 public-key authentication on offline-mode servers. Players are held in a "limbo" state on join and only released into the world once they produce a valid cryptographic signature over a server-issued nonce. Unrecognized keys wait for an admin `/gate allow` before being admitted.

## Architecture

```
src/
  main/           – server-side (and shared) code
    auth/         – Ed25519 primitives, nonce/challenge store, server identity
    command/      – /gate allow|reset|list (operator-only, OP level 3)
    config/       – GateConfig: JSON config file loader/saver
    limbo/        – LimboManager (in-limbo set), LimboPacketQueue (queued S2C packets)
    mixin/        – server mixins (see Mixins section below)
    network/      – custom payload types and server-side packet handlers
    Mcgatekeeper.java – ModInitializer: wires everything together

  client/         – client-side code (split source set)
    auth/         – ClientKeyStore (per-server Ed25519 keypairs), ClientAuthState
    network/      – ClientResponseHandler: responds to challenges, handles results
    mixin/client/ – client mixins (loading screen overlay)
    McgatekeeperClient.java – ClientModInitializer
```

### Authentication flow

1. Player connects → `ChallengeHandler.onPlayerJoin` → player added to limbo, `ChallengePayload` sent (contains server identity UUID + 32-byte nonce + timeout).
2. Client receives challenge → looks up or generates an Ed25519 keypair for this server identity → signs the nonce → sends `ResponsePayload` (public key + signature).
3. `ResponseHandler` verifies the signature against stored keys for that UUID. On success: limbo released, queued world packets flushed, join message broadcast. On failure (unknown key): `AwaitingAdminPayload` sent, player stays in limbo until `/gate allow`.
4. Limbo tick (every server tick): players whose challenge has expired (configurable `limboTimeoutSeconds`) are kicked.

### Limbo packet interception

Two mixins cooperate to freeze a player in limbo without disconnecting them:

- **`ServerCommonNetworkHandlerMixin`** – intercepts `sendPacket` on the Netty thread. All S2C packets for limbo players are queued in `LimboPacketQueue` instead of being sent. Keep-alive, disconnect, custom payload, and ping packets pass through so the connection stays alive.
- **`PacketApplyBatcherEntryMixin`** – intercepts `PacketApplyBatcher$Entry.apply` on the main thread. All C2S game packets for limbo players are dropped. `CustomPayloadC2SPacket` passes through so `ResponsePayload` is received.

### Key storage

- **Server** (`config/mcgatekeeper/players.json`): maps player UUID → list of `{label, publicKey}` entries (Base64 Ed25519 public keys). Managed via `/gate allow|reset|list`.
- **Client** (`config/mcgatekeeper/server-keys.json`): maps server identity UUID → Ed25519 `{privateKey, publicKey}` pair. Keys are generated automatically on first connection to each server.
- **Server identity** (`config/mcgatekeeper/server.id`): random UUID generated once and persisted. Lets the client maintain a separate keypair per physical server.

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
| `PlayerManagerMixin` | `PlayerManager` | Tracks the player currently connecting so join messages can be suppressed while in limbo; suppresses join broadcast for limbo players. |
| `ServerCommonNetworkHandlerMixin` | `ServerCommonNetworkHandler` | Queues all S2C packets for limbo players instead of sending them. |
| `PacketApplyBatcherEntryMixin` | `PacketApplyBatcher$Entry` | Drops all C2S game packets for limbo players (except `CustomPayloadC2SPacket`). |
| `MinecraftDedicatedServerMixin` | `MinecraftDedicatedServer` | Redirects the first `isOnlineMode()` call in `setupServer`. When the server is offline and `replaceOfflineModeWarning` is enabled, logs one INFO line via the class's own LOGGER and returns `true`, which makes the `if (!isOnlineMode())` branch skip the four vanilla WARN lines entirely. |
| `LevelLoadingScreenMixin` *(client)* | `LevelLoadingScreen` | Replaces the loading screen text with a countdown message while the player is awaiting admin authorization. |

### Inner-class mixin targets

`PacketApplyBatcher$Entry` is an inner class. Reference it with the `targets` string form instead of the class literal:

```java
@Mixin(targets = "net.minecraft.network.PacketApplyBatcher$Entry")
```

### `@Shadow @Final` on records

When shadowing fields of a record (which has a canonical constructor that assigns all finals), do **not** give the shadow field an initializer — use bare `@Shadow @Final`:

```java
@Shadow @Final private PacketListener listener;
```

Adding `= null` causes Mixin to inject a null-assignment into the canonical constructor, which corrupts the field.
