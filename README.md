# McGatekeeper

A Fabric mod for Minecraft 1.21.x that replaces username/password login with **cryptographic key authentication**. Instead of a shared password, every player holds a unique digital key that the server recognises. No password can be guessed or stolen — only players with the right key can connect.

Designed for offline-mode servers where the operator personally knows every player.

---

## How it works (simple version)

Think of it like a physical lock and key:

- When you connect to a McGatekeeper server for the first time, the mod **generates a unique key pair** for that server and stores it on your computer. You never see or type this key — it happens automatically.
- Every time you connect after that, your game quietly **proves to the server that it holds the right key**, without ever sending the key itself over the network.
- The server checks your key against its approved list. If your key is on the list, you're let in. If not, the connection is held until an admin approves it.

No passwords. No shared secrets. Nothing to forget or leak.

---

## Requirements

- **Minecraft 1.21.x** with the [Fabric](https://fabricmc.net/) mod loader
- **Fabric API** (required dependency)
- Both the **server** and **every client** must have the mod installed

---

## Installation

### Server

1. Install [Fabric](https://fabricmc.net/use/server/) and [Fabric API](https://modrinth.com/mod/fabric-api) on your server.
2. Drop `McGatekeeper-<version>.jar` into the server's `mods/` folder.
3. Set `online-mode=false` in `server.properties`. McGatekeeper replaces Mojang's online-mode authentication with its own.
4. Start the server. A `config/mcgatekeeper/` directory is created automatically with default settings.

> **Important:** With `online-mode=false` and no McGatekeeper, anyone who knows a player's username can impersonate them. McGatekeeper eliminates that risk — but it means **you must approve every new player** before they can join.

### Client

1. Install [Fabric](https://fabricmc.net/use/installer/) and [Fabric API](https://modrinth.com/mod/fabric-api) on your Minecraft instance.
2. Drop `McGatekeeper-<version>.jar` into your `mods/` folder.
3. That's it. Keys are generated and managed automatically.

---

## Connecting for the first time

The first time a player connects to a McGatekeeper server:

1. The game generates a unique key pair for that server behind the scenes.
2. The connection screen shows a message saying the server is waiting for admin approval.
3. An operator with permission level 3 or higher runs:

```
/gate allow <username> <label>
```

Where `<label>` is a short name you choose for the key (e.g. `home-pc`, `laptop`). Once approved, the player is let in immediately and their key is remembered for all future connections.

---

## Admin commands

All commands require operator level 3 (`ops.json` entry with `"level": 3` or higher).

### `/gate allow <player> <label>`

Approves a player who is currently waiting for admission and registers their key under the given label. The player is released into the game immediately.

Tab-complete suggests the names of players currently awaiting approval.

### `/gate reset <player>`

Removes **all** registered keys for a player. Their next connection will be held for admin approval again.

### `/gate reset <player> <label>`

Removes only the key with the given label. Useful when a player loses a device (e.g. their laptop's key should be revoked, but their desktop key should remain).

### `/gate list <player>`

Shows all registered keys for a player, along with a short fingerprint for each one so you can identify which device is which.

---

## Configuration

The config file is created automatically at `config/mcgatekeeper/config.json`:

```json
{
  "authTimeoutSeconds": 30,
  "replaceOfflineModeWarning": true
}
```

| Option | Default | Description |
|---|---|---|
| `authTimeoutSeconds` | `30` | How long (in seconds) a connecting player has to complete authentication before being disconnected. Increase this if players have very slow connections. |
| `replaceOfflineModeWarning` | `true` | Suppresses Minecraft's four console warnings about running in offline mode and replaces them with a single info line confirming McGatekeeper is active. |

---

## Files created

| Path | What it stores |
|---|---|
| `config/mcgatekeeper/config.json` | Server configuration (see above) |
| `config/mcgatekeeper/server.key` | The server's own key pair, generated once. **Back this up.** If lost, all client keys become invalid and every player must be re-approved. |
| `config/mcgatekeeper/players.json` | Approved player keys, indexed by UUID |
| *(client)* `config/mcgatekeeper/server-keys.json` | Per-server key pairs, stored on each player's computer |

---

## Frequently asked questions

**Can a player connect without the mod?**
No. Clients without the mod are disconnected immediately with a message telling them to install it.

**What if a player gets a new computer?**
Their new device generates a new key. On first connection it will be held for admin approval, just like the first time. You can approve the new key with `/gate allow` — their old keys (from other devices) remain valid.

**Can I revoke a specific device?**
Yes. Use `/gate list <player>` to see the labels and fingerprints of each registered key, then `/gate reset <player> <label>` to remove just that one.

**Does this work with proxies (BungeeCord / Velocity)?**
Only on the backend servers where McGatekeeper is installed. Proxy-level setups vary — test carefully.

**Is my key the same on every server?**
No. A separate key pair is generated for each server you connect to. This prevents one server from impersonating another.

---

## Technical details and security model

This section is for operators who want to understand the security properties in depth.

### Cryptographic algorithm

McGatekeeper uses **Ed25519**, an elliptic-curve digital signature algorithm. It produces 64-byte signatures over arbitrary messages using a 32-byte private key. Ed25519 is:

- Fast (signing and verification take microseconds)
- Compact (small keys and signatures)
- Widely considered secure against all known classical attacks
- Not vulnerable to weak random number generation during signing (unlike ECDSA)

### What is actually proved at login

The server issues a **nonce** (a random 32-byte challenge, used only once) together with a signature over that nonce made with the server's own private key. The client:

1. Verifies the server's signature — confirming it is talking to the expected server and not a relay/proxy that is forwarding its packets.
2. Signs a message derived from the server's public key and the nonce using its own private key.
3. Sends its public key and signature back.

The server checks that the submitted public key matches an approved entry for the player's UUID, and that the signature over the message is valid. If both checks pass, the player is admitted.

### Relay attack protection

The server signs the nonce with its own private key and sends the signature to the client. The client verifies this signature before responding. A relay or man-in-the-middle that forwards challenges from a different server would have a different server public key, causing client-side verification to fail. The client will refuse to sign.

### Session management

Authentication occurs entirely during Minecraft's **configuration phase**, before the player enters the world. Minecraft holds the connection at this phase until the mod explicitly releases it. This means:

- No player can enter the world without completing authentication.
- If the player disconnects during authentication, the pending state is cleaned up immediately.
- If authentication takes longer than `authTimeoutSeconds`, the connection is closed.
- If a second connection from the same account arrives while one is already authenticated or transitioning to the play state, the new connection is rejected.

### Key storage

Private keys are stored in plaintext in the Fabric config directory (`config/mcgatekeeper/`). They are **not** encrypted at rest. Operating system file permissions are your only protection for the private key files. Ensure:

- The server's `config/mcgatekeeper/server.key` is readable only by the server process user.
- Players' `config/mcgatekeeper/server-keys.json` is on a machine they control.

### What McGatekeeper does NOT protect against

- **Compromised client machine.** If an attacker has access to a player's computer and can read their `server-keys.json`, they can impersonate that player. Revoke the key with `/gate reset <player> <label>` if a device is compromised.
- **Compromised server files.** The server stores approved public keys in `players.json`. If an attacker can modify this file, they can add their own key. This is a server file-system security concern, not a protocol concern.
- **Denial of service.** McGatekeeper does not rate-limit connection attempts. A flood of connections will consume server resources during the authentication phase. The `authTimeoutSeconds` timeout limits how long each unauthenticated connection can hold a slot.

---

## License

[LGPL-3.0](LICENSE)

---

## Disclaimer

This software is provided "as is", without warranty of any kind, express or implied. The authors make no guarantees about the security, correctness, or fitness for purpose of this mod. You are solely responsible for the security of your server and the safety of any data on it. The authors are not liable for any loss, breach, or damage arising from the use or misuse of this software.

McGatekeeper is a best-effort authentication layer. It substantially raises the bar for unauthorised access on offline-mode servers, but no software can guarantee absolute security. Conduct your own risk assessment before deploying it in any context where a security breach would have serious consequences.
