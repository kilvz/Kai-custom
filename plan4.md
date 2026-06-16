# Persona Prompt Fix Plan

## Goal Matrix

| Persona | Chat prompt | Heartbeat prompt | RenderMode |
|---|---|---|---|
| **Kai** (default) | Full upstream Kai 9000 behavior, including user soul edits | Upstream Kai 9000 heartbeat (no personality injection) | `UPSTREAM_COMPAT` |
| **Alt** (operator) | Full technical stack (tools, memory, automation, context) | Same as Kai — functional, no personality injection | `FORK_ENHANCED` |
| **Built-in others** (sage, butler, etc.) | Full technical stack + `defaultSoul` identity | Same as Kai/Alt — functional heartbeat | `FORK_ENHANCED` |
| **Custom/Generated** (UI, generator, `set_character`) | CHARACTER mode — pure persona, minimal tech sections | **Should know its identity** — inject `getSoulText()` so heartbeat acts in-character | `CHARACTER` |

---

## Change 1: `buildCustomSoul()` — UPSTREAM_COMPAT fix

**File**: `UnifiedPromptBuilder.kt:381-389`

**Current**: Early return `null` for UPSTREAM_COMPAT, dropping `soulUserText` and `soulAutoText`.

**Change**: Remove the early return. `buildCustomSoul()` works for all render modes so Kai persona gets user soul edits + auto behavior notes.

```diff
 private fun buildCustomSoul(context: PromptContext): String? {
-    if (context.renderMode == RenderMode.UPSTREAM_COMPAT) return null
     val user = context.soulUserText
     ...
```

**Why**: Fork intentionally added soul editing. Upstream Kai 9000 may not have had this — the `null` was a compat guard. Now user soul edits should reach the model for all personas.

---

## Change 2: Heartbeat — persona-aware identity injection

**File**: `HeartbeatManager.kt:146-176`

**Current**: Heartbeat uses only `soulUserText` + `soulAutoText` + `toBehaviorTraitBlock()`. Never uses `getSoulText()`, so personality `defaultSoul` is missing for custom personas.

**Change**: Inject `getSoulText()` into heartbeat lead preamble for CHARACTER-mode personas only.

```diff
 val activePersona = personaManager.getActivePersona()
+val soul = appSettings.getSoulText(activePersona.id)
 val soulUserText = appSettings.getSoulUser(activePersona.id)
 val soulAutoText = appSettings.getSoulAuto(activePersona.id)
 
 val heartbeatLead = buildString {
+    if (activePersona.renderMode == RenderMode.CHARACTER && soul.isNotBlank()) {
+        append(soul)
+        append("\n\n")
+    }
     if (soulUserText.isNotBlank()) {
         append(soulUserText)
         append("\n\n")
     }
     append(if (customPrompt.isNotBlank()) customPrompt else DEFAULT_HEARTBEAT_PROMPT)
 }
```

**Why CHARACTER-only**: Kai, Alt, and built-in personas use UPSTREAM_COMPAT/FORK_ENHANCED. Upstream Kai 9000 heartbeat doesn't inject personality — it's a functional system check. Only custom personas (which have no other prompt context) need identity injected to maintain character during heartbeat.

---

## Change 3 (new): `getSoulText()` — skip prefix when `defaultSoul` already self-identifies

**File**: `AppSettings.kt:285-303`

**Current**: `getSoulText()` always prepends `"You are {name}.\n\n"` before `defaultSoul`. For 41/42 built-in personas `defaultSoul` already starts with `"You are {name}"`, and for generated personas it starts with `**Identity**:`. Only Kai needs the prefix (its `defaultSoul` starts with `"You're not a chatbot..."`).

**Change**: If the first line of `defaultSoul` starts with `"You are "` or `"**Identity**:"`, skip the prefix — the content already self-identifies.

```diff
     if (!defaultSoul.isNullOrBlank()) {
+        val firstLine = defaultSoul.lineSequence().firstOrNull()?.trim() ?: ""
+        if (firstLine.startsWith("You are ") || firstLine.startsWith("**Identity**:")) {
+            return defaultSoul
+        }
         if (localModel) {
             ...
         }
         return "$prefix\n\n$defaultSoul"
     }
```

**Result**:
- **Kai**: `"You are Kai.\n\nYou're not a chatbot..."` (prefix kept — no self-ID in first line)
- **Sage**: `"You are Sage — a measured, thoughtful advisor.\n\n..."` (prefix skipped — defaultSoul starts with `You are`)
- **Generated**: `"**Identity**: You are [full text]"` (prefix skipped — starts with `**Identity**:`)

---

## Change 4: Remove dead `CorePersonalitySection`

**File**: `UnifiedPromptBuilder.kt:80-84, 324-333`

**Current**: `CorePersonalitySection(defaultSoul)` always returns null because `defaultSoul` defaults to `""` — never passed. Content already reaches model via `SoulIdentitySection` + `getSoulText()`.

**Change**: Delete the class, remove from list, remove unused `defaultSoul` constructor param.

```diff
-internal class CorePersonalitySection(private val defaultSoul: String) : PersonaSection {
-    override fun build(context: PromptContext): String? {
-        if (defaultSoul.isBlank()) return null
-        return defaultSoul
-    }
-}

 internal class UnifiedPromptBuilder(
-    private val defaultSoul: String = "",
 ) {
     private val personaSections: MutableList<PersonaSection> = mutableListOf(
         LocalStyleSection(),
         SoulIdentitySection(),
-        CorePersonalitySection(defaultSoul),
         HonestyRuleSection(),
         LanguageSection(),
     )
```

---

## Change 5: CHARACTER mode — optional minimal technical sections (deferred)

**File**: `UnifiedPromptBuilder.kt:352-361`

**Current**: CHARACTER mode skips ALL technical sections (no tool use, no memory, no context, no automation, no email, no dynamic UI). Only `taskAdapters` run (memory dump, file context).

**Options**:
- **A**: Add `ContextSection` (time, platform, model) — cheap, useful
- **B**: Add 1-line tool use reminder
- **C**: Leave as-is (minimalist by design)

**Waiting for user decision.**

---

## Change 6: `PersonaManager` — unused methods (drive-by)

**File**: `Persona.kt:92-93`

`getSoulUserKey()` and `getSoulAutoKey()` are public but never called externally. Keys are constructed inline in `AppSettings.kt`. Optional cleanup.

---

## Files Touched

| File | Change |
|---|---|---|
| `AppSettings.kt` | `getSoulText()` — skip prefix when defaultSoul self-identifies |
| `UnifiedPromptBuilder.kt` | Remove `CorePersonalitySection`, remove `defaultSoul` param, remove UPSTREAM_COMPAT skip |
| `HeartbeatManager.kt` | Inject `getSoulText()` for CHARACTER-mode personas |
| `Persona.kt` | Optional: remove unused key helpers |

No changes to `RemoteDataRepository.kt`, `PersonaCatalog.kt`, `CommonTools.kt`, `AgentSettings.kt`, `SettingsViewModel.kt`.
