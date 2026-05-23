#!/usr/bin/env python3
"""Generate en_ud.json (upside-down English) from the mod's en_us.json.
Run from the repo root: python3 tools/generate_en_ud.py"""

import json

LANG_DIR = "src/main/resources/assets/mcgatekeeper/lang"
IN_FILE  = f"{LANG_DIR}/en_us.json"
OUT_FILE = f"{LANG_DIR}/en_ud.json"

FLIP_MAP = {
    'a': 'ɐ', 'b': 'q', 'c': 'ɔ', 'd': 'p', 'e': 'ǝ', 'f': 'ɟ',
    'g': 'ƃ', 'h': 'ɥ', 'i': 'ᴉ', 'j': 'ɾ', 'k': 'ʞ', 'l': 'ꞁ',
    'm': 'ɯ', 'n': 'u', 'o': 'o', 'p': 'd', 'q': 'b', 'r': 'ɹ',
    's': 's', 't': 'ʇ', 'u': 'n', 'v': 'ʌ', 'w': 'ʍ', 'x': 'x',
    'y': 'ʎ', 'z': 'z',
    'A': '∀', 'B': 'ᗺ', 'C': 'Ɔ', 'D': 'ᗡ', 'E': 'Ǝ', 'F': 'Ⅎ',
    'G': '⅁', 'H': 'H', 'I': 'I', 'J': 'ſ', 'K': 'ʞ', 'L': '⅂',
    'M': 'W', 'N': 'N', 'O': 'O', 'P': 'Ԁ', 'Q': 'Q', 'R': 'ᴚ',
    'S': 'S', 'T': '⊥', 'U': '∩', 'V': 'Λ', 'W': 'M', 'X': 'X',
    'Y': 'ʎ', 'Z': 'Z',
    '!': '¡', '?': '¿', ',': "'", '.': '˙', '(': ')', ')': '(',
    '[': ']', ']': '[', '{': '}', '}': '{', "'": ',',
}

def flip(text):
    return ''.join(FLIP_MAP.get(c, c) for c in reversed(text))

# Verify mapping against known Minecraft en_ud values
checks = [
    ("Done",     "ǝuoᗡ"),
    ("Disabled", "pǝꞁqɐsᴉᗡ"),
    ("Enabled",  "pǝꞁqɐuƎ"),
]
for src, expected in checks:
    result = flip(src)
    status = "OK" if result == expected else f"MISMATCH (got {result!r}, expected {expected!r})"
    print(f"  check flip({src!r}) = {result!r}  [{status}]")

print()

with open(IN_FILE, encoding="utf-8") as f:
    en_us = json.load(f)

en_ud = {key: flip(value) for key, value in en_us.items()}

for key, value in en_ud.items():
    print(f"  {key}: {value}")

with open(OUT_FILE, "w", encoding="utf-8") as f:
    json.dump(en_ud, f, ensure_ascii=False, indent=2)
    f.write("\n")

print(f"\nWritten to {OUT_FILE}")
