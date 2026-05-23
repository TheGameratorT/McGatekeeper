---
name: add-lang
description: Add or update a fun Minecraft language variant translation file for a mod — specifically Pirate (en_pt), Shakespearean (enws), or Anglish (enp). Use this skill whenever the user asks to add, update, write, or fix a translation for one of these three language variants in a Minecraft mod project, or when they mention language files under src/main/resources/assets/.../lang/. The skill handles looking up Minecraft's own vanilla translations for style reference, consulting the Anglish wordbook for gaps, and writing idiomatic translations that match Minecraft's own tone.
---

# add-lang: Minecraft Fun Language Variants

Adds or updates translation files for Pirate (`en_pt`), Shakespearean (`enws`), or Anglish (`enp`) in a Minecraft mod.

Lang files live at:
```
src/main/resources/assets/<modid>/lang/
```

---

## Step 1: Identify the target language and source keys

Read the mod's `en_us.json` to get all translation keys:

```bash
cat src/main/resources/assets/*/lang/en_us.json
```

Also check if a target lang file already exists:

```bash
ls src/main/resources/assets/*/lang/
```

---

## Step 2: Look up Minecraft's vanilla translation for style reference

PrismLauncher assets (Flatpak path):

```bash
ASSETS=~/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/assets
INDEX=$ASSETS/indexes/17.json

# Replace en_pt with enws or enp as needed
HASH=$(python3 -c "import json; d=json.load(open('$INDEX')); print(d['objects']['minecraft/lang/en_pt.json']['hash'])")
cat $ASSETS/objects/${HASH:0:2}/$HASH | python3 -m json.tool | head -80
```

Use Minecraft's file as the **primary style and vocabulary reference**. Match its tone exactly — don't invent your own register.

If the assets index or hash lookup fails (e.g. assets not downloaded yet), note this and proceed from knowledge of the language's style alone, but flag it.

---

## Step 3: Language-specific guidance

### Pirate (`en_pt`)
- Match Minecraft's swashbuckling register: "Ahoy", "ye", "me hearties", "landlubber", "shiver me timbers"
- Replace "you" → "ye", "my" → "me", "the" → "th'", "is" → "be"
- Add nautical flavour where natural; don't force it on every line
- Keep technical terms recognisable — players still need to understand the UI

### Shakespearean (`enws`)
- Match Minecraft's Early Modern English register: "thee", "thou", "thy", "dost", "hath", "art", "verily", "forsooth"
- Second person: thou (subject), thee (object), thy/thine (possessive)
- Verb conjugation: thou art, thou dost, thou hast, he/she/it hath
- Don't over-archaise — Minecraft's enws is theatrical, not impenetrable

### Anglish (`enp`)
Anglish replaces Latinate/French loanwords with Germanic English roots.

**Established Minecraft terms — always use these:**

| Anglish      | English     |
|--------------|-------------|
| webthew      | server      |
| besitting    | session     |
| reckoning    | account     |
| dright       | admin       |
| dwale        | error       |
| leaved       | allowed     |
| shirm        | screen      |

For gaps not in Minecraft's `enp.json`:
1. Check the Anglish Wordbook: https://wordbook.anglish.org/
2. Check the word list: https://anglish.org/wiki/Helpful_Anglish_Words
3. If nothing fits, keep the English word rather than invent something jarring

Common substitutions:
- connect → link, join
- message → word, writ
- player → player (already Germanic)
- login/logout → sign in / sign out → "infare" / "outfare" (Anglish)
- password → watchword
- profile → likeness, shape
- version → build, eld

---

## Step 4: Write the translation file

Edit (or create) the target `.json` file:

```
src/main/resources/assets/<modid>/lang/<lang_code>.json
```

- Mirror **every key** from `en_us.json` — don't skip any
- Preserve all format placeholders exactly: `%s`, `%d`, `%1$s`, `$(thing)`, etc.
- Keep JSON valid — double-check trailing commas
- Preserve newlines (`\n`) where present in the source

### Quality checklist before saving
- [ ] All keys from `en_us.json` are present
- [ ] No placeholders are missing or altered
- [ ] Tone matches Minecraft's own file for this language
- [ ] Anglish: established terms used; wordbook consulted for gaps
- [ ] JSON is valid (run `python3 -m json.tool <file>` to verify)

```bash
python3 -m json.tool src/main/resources/assets/*/lang/en_pt.json > /dev/null && echo "Valid JSON" || echo "INVALID JSON"
```
