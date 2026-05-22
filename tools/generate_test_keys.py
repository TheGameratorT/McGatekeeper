#!/usr/bin/env python3
"""
Generate a test key file with 32 entries for McGatekeeper import testing.
Uses libsodium via ctypes — no pip install required.
"""
import base64
import ctypes
import ctypes.util
import json
import os

_libsodium = ctypes.CDLL(ctypes.util.find_library("sodium"))

def gen_keypair() -> tuple[str, str]:
    """Return (privateKey_b64, publicKey_b64) for a fresh Ed25519 keypair."""
    seed = os.urandom(32)
    pk = ctypes.create_string_buffer(32)   # crypto_sign_ed25519_PUBLICKEYBYTES
    sk = ctypes.create_string_buffer(64)   # crypto_sign_ed25519_SECRETKEYBYTES (unused)
    _libsodium.crypto_sign_ed25519_seed_keypair(pk, sk, seed)
    return base64.b64encode(seed).decode(), base64.b64encode(pk.raw).decode()


entries: dict[str, dict[str, str]] = {}
for _ in range(32):
    _, server_pub = gen_keypair()
    client_priv, client_pub = gen_keypair()
    entries[server_pub] = {
        "privateKey": client_priv,
        "publicKey": client_pub,
    }

output = "test-keys.json"
with open(output, "w") as f:
    json.dump(entries, f, indent=2)

print(f"Generated {len(entries)} entries → {output}")
