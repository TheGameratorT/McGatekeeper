---
name: gen-decomp
description: >
  Generates (or regenerates) the `game_decomp/` directory of Yarn-named Minecraft decompiled sources using Fabric Loom's `genSources` task and Vineflower. Use this skill whenever the user wants to decompile Minecraft sources, regenerate game_decomp/, work with Yarn-mapped class names, or set up a Minecraft modding workspace with human-readable decompiled code. Trigger even if the user just says "run genSources", "decompile the game", or "set up game_decomp".
---

# gen-decomp

Generates the `game_decomp/` directory containing Yarn-named, human-readable Minecraft decompiled sources. Run from the repo root.

## Steps

### 1. Run `genSources`

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew genSources
```

This invokes Vineflower to decompile the full game. **Takes a few minutes on first run.** Subsequent runs are faster due to caching.

### 2. Locate the decompiled JARs

```sh
ROOT=$(pwd)
COMMON_SRC=$(find .gradle/loom-cache/minecraftMaven/net/minecraft -name "minecraft-common-*-sources.jar" | head -1)
CLIENT_SRC=$(find .gradle/loom-cache/minecraftMaven/net/minecraft -name "minecraft-clientOnly-*-sources.jar" | head -1)
```

The `find` commands handle the project-specific content hash in the JAR filenames automatically.

### 3. Extract sources into `game_decomp/`

```sh
mkdir -p game_decomp/common game_decomp/client
(cd game_decomp/common && jar xf "$ROOT/$COMMON_SRC")
(cd game_decomp/client && jar xf "$ROOT/$CLIENT_SRC")
```

## Output

| Directory | Contents |
|---|---|
| `game_decomp/common/` | Server-side and shared classes |
| `game_decomp/client/` | Client-only classes |

All classes use **Yarn human-readable names** (not obfuscated intermediary names).

## Full script (copy-paste)

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew genSources

ROOT=$(pwd)
COMMON_SRC=$(find .gradle/loom-cache/minecraftMaven/net/minecraft -name "minecraft-common-*-sources.jar" | head -1)
CLIENT_SRC=$(find .gradle/loom-cache/minecraftMaven/net/minecraft -name "minecraft-clientOnly-*-sources.jar" | head -1)

mkdir -p game_decomp/common game_decomp/client
(cd game_decomp/common && jar xf "$ROOT/$COMMON_SRC")
(cd game_decomp/client && jar xf "$ROOT/$CLIENT_SRC")
```

## Troubleshooting

- **`JAVA_HOME` not found**: Adjust the path to match your Java 21 installation (e.g. `/usr/lib/jvm/java-21-openjdk-amd64` on some Debian systems).
- **`COMMON_SRC` or `CLIENT_SRC` is empty**: `genSources` may have failed — check Gradle output for errors before running the extract step.
- **Regenerating**: Simply re-run the full script. Existing `game_decomp/` contents will be overwritten.
