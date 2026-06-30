# Agent Handoff — Kai-custom (FOSS)

Public FOSS fork of [Kai](https://github.com/SimonSchubert/Kai). Kotlin Multiplatform (Compose Multiplatform 1.11.1, Kotlin 2.4.0, Gradle 9.5.1, Java 21). **Primary target: Android** (arm64-v8a, armeabi-v7a, x86, x86_64, `foss` product flavor). Desktop (JVM) builds on Win/Linux/Mac but less tested. WasmJs/iOS exist, not tested.

- **Package name**: `com.kai.custom` — `androidApp/` is thin shell, `composeApp/` has all logic
- **Modules**: `:composeApp` (KMP library) + `:androidApp` (Android application)
- **Source sets**: `commonMain`, `androidMain`, `desktopMain`, `iosMain`, `wasmJsMain`, `jvmShared` (shared between androidMain + desktopMain)
- **Generated code**: `composeApp/build/generated/src/commonMain/kotlin/com/kai/custom/Version.kt` — auto-generated from `gradle/libs.versions.toml` on build
- **Upstream**: `https://github.com/SimonSchubert/Kai.git`
- **This file MUST be updated** by every agent when they modify code, merge upstream, or change architecture. Do not let it go stale.

## Version

Version in `VERSION` (root) + `appVersion` + `android-versionCode` in `gradle/libs.versions.toml`. Format `v{a}.{b}.{c}`:
- **c** = bug fix, **b** = feature, **a** = major/breaking
- `versionCode` always +1
- Upstream base tracked as `appVersionBase` (e.g. `"2.7.1"`)

Current: `v3.30.7` (versionCode 204).

## Upstream Merges

Every time upstream is merged into our fork, log it here so agents know what's been integrated.

| Date | Upstream Tag | Our Tag | Merge Commit | Notes |
|------|-------------|---------|-------------|-------|
| 2026-06-16 | v2.7.1 | v3.30.0 | `b7f72f6f` | `git merge upstream/main`; resolved conflicts in `.gitignore`, `CHANGELOG.md`, `aur/*`, `flatpak/*`, `gradle/libs.versions.toml`, `ModelCatalog.kt`, `iosApp/Config.xcconfig`. Deleted upstream's `com.inspiredandroid.kai` files (we use `com.kai.custom`). Added 1 new model from upstream: `mimo-v2-flash-thinking`. Accepted upstream's `filekitCore = "0.14.2"` version bump. |
| 2026-07-01 | — | v3.30.7 | `a75fa312` | `git merge upstream/main`; resolved conflicts in `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `kotlin-js-store/wasm/yarn.lock`. Accepted all upstream version bumps: Gradle 9.6.1, compileSdk 37, lifecycle 2.11.0, koinCompose 4.2.2, ktor 3.5.1, spotless 8.8.0, foundationAndroid 1.11.3. ModelCatalog updated (10 new models, arena scores, DeepSeek V4 context 1M). New `glm-5.2`, `qwen3.7-plus`, `ring-flash-2.0`. Reload conversations after settings import. |

To check for new upstream commits since last merge:
```bash
git fetch upstream
git log --oneline upstream/main --not main
```

## Dev Commands (Linux)

```bash
# Fast compile check — don't run full output then grep
./gradlew :composeApp:compileAndroidMain 2>&1 | grep -E "^e:|FAILED"

# Full APK (arm64-v8a debug)
./gradlew :androidApp:assembleFossDebug

# Desktop dev run
./gradlew :composeApp:run

# Desktop packaging (needs --no-configuration-cache)
./gradlew :composeApp:createDistributable --no-configuration-cache
```

Windows equivalents use `.\gradlew.bat`. Do NOT build or deploy unless asked.

## Build Quirks

- **BouncyCastle ProGuard fix**: `composeApp/build.gradle.kts:221-238` replaces the ProGuard-processed `bcprov` jar with the original signed jar to avoid `SHA-256 digest error` at runtime. If you touch ProGuard or BouncyCastle config, verify this still works.
- **Compose stability config**: `composeApp/compose_stability.conf` marks composable stability — if you add new composable-heavy code, consider updating it.
- **iOS framework is dynamic** (`isStatic = false` in `composeApp/build.gradle.kts:51`) to avoid symbol duplication with LiteRT-LM's `-all_load`. Framework not tested but if you touch it, don't flip to static without testing.

## ⚠️ HARD RULES

- **NEVER `adb uninstall`** the app — it nukes all data, memories, settings, persona, everything. Always `adb install -r` (reinstall), or `adb install -r -d` if downgrade needed.
- **Package name is `com.kai.custom`** — NOT `com.inspiredandroid.kai`. The upstream package name appears in some error messages but our app is `com.kai.custom`.
- **APK files are per-abi**: `androidApp-foss-{arm64-v8a,armeabi-v7a,x86,x86_64}-debug.apk` — there is no universal `androidApp-foss-debug.apk`.
- **ZERO build warnings in CI** — always fix deprecation warnings first. If fixing would break code, suppress the specific warning. NEVER leave warnings visible in CI output.
- **Match upstream dependency versions exactly** — on `git merge upstream/main`, accept ALL upstream version changes without cherry-picking. Our fork-only deps (shizuku, jsch, tensorflow-*, etc.) stay as-is.
- **Never clear WhatsApp auth** unless server explicitly returns 401 (loggedOut).

## Tests (Known Flaky)

Tests are NOT run in CI (pre-existing failures: `ChatViewModel*Test`, `Sandbox*Test`, `SettingsViewModelTest`). To run anyway:

```bash
./gradlew :composeApp:check
```

## Linting

Spotless with ktlint in root `build.gradle.kts`. Wildcard imports, package name, function naming, and several comment rules are **disabled**. Quick check:

```bash
./gradlew spotlessKotlinCheck
./gradlew spotlessKotlinApply     # auto-fix
```

## SpAIder Service (port 8890)
- SpAIder now uses `spaider-server` on port 8890 (not 8899)
- 28 models total: 6 base models × (image + music + video + base) + 4 research variants
- Models: `spaider-lite`/`-ext`, `spaider`/`-ext`, `spaider-pro`/`-ext` each with `-image`, `-music`, `-video`, and where researchable `-research`
- Generation models (`spaider-image`, `spaider-music`, `spaider-video` etc.) work via standard `/v1/chat/completions` — just use the model name
- Context windows: flash models 1M, pro models 2M (set in ModelCatalog.kt)
- Research models: `spaider-research`, `spaider-ext-research`, `spaider-pro-research`, `spaider-pro-ext-research`

## Debug API (Port 18500)

Enabled in Settings. `/health` returns token; all other endpoints need `Authorization: Bearer <token>`. 77+ endpoints documented in `docs/debug-api.md`.

```bash
adb forward tcp:18500 tcp:18500
curl -s http://127.0.0.1:18500/health
curl -s -X POST http://127.0.0.1:18500/sandbox/setup
curl -s -X POST "http://127.0.0.1:18500/sandbox/exec?timeout=10" -d "uname -a"
```

## Native Builds

- **Proot**: `bash build-proot.sh` (requires Android NDK, WSL on Windows). Outputs `.so` to `androidApp/src/main/jniLibs/{abi}/`.
- **GGUF** (llama.cpp JNI): `build-gguf.bat` (Windows, requires WSL + NDK). Source in `gguf/`. Plugin: `GgufInferenceEngine`, `PluginManager`.

## CI / GitHub Actions

- `.github/workflows/release.yml` — triggered on `v*` tag push
  - `apk` job (required): builds + signs + uploads APK
  - Desktop jobs (`dmg`, `msi`, `deb`, `rpm`, `linux-tar`, `appimage`): `continue-on-error: true`
  - `release` job: creates GitHub Release (depends only on `apk`)
- `.github/workflows/winget.yml` — manual WinGet publish trigger (harmless)

## Git Rules

- **Never `git add -A` or `git add .`** — stage only files that changed
- **Never rewrite history or force push** `main`
- **Never create/delete a GitHub release** without explicit instruction (tagging+committing+pushing is fine)
- AGENTS.md is gitignored — don't commit it
- Public FOSS fork: never mention internal/private details in commits or changelogs

## Feature Docs

Per `CLAUDE.md`: feature specs in `docs/features/`. When modifying a feature with a corresponding doc, update the doc + "Last verified" date + Key Files table.

## Fork-Specific Architecture

### Two-Tier Memory (`protected` field)
- `MemoryEntry.protected = true` = behavior learnings (hidden from deletion UI, `memory_forget` rejects)
- `false` = user facts (deletable in `MemoryManagementSheet`)
- `MemoryStore` has `storeProtected()`, `getUserMemories()`, `getBehaviorMemories()`
- `SqliteMemoryStore` DB v4 migration adds `protected` column
- `MemoryManagementSheet` filters with `.filter { !it.protected }` + "Show protected" toggle

### Soul Split (`soul_user` + `soul_auto`)
- `soul_user`: user-edited portion (Settings UI)
- `soul_auto`: auto-generated behavior summary (heartbeat updates)
- `getSoulText()` combines both; `setSoulText()` writes to `soul_user` only
- Legacy `soul_text` key auto-migrated to `soul_user`

### Persona
- `Persona(name, description)` with `toSoulSegment()` — `getSoulText()` prepends `"You are {name}."`
- Persisted in AppSettings under `current_persona` key

### Memory Toggle
- `isMemoryEnabled()` defaults ON. When OFF: no memory dump in prompt, no memory/KG/diary tools, no extraction. Gated at `Platform.android.kt:291`.

### ChatSystemPromptBuilder
Removed sections: Tool Use, When to Act, Memory System, Automation, Structured Learning, email policy. Order: Soul → Language → Honesty → What I Know About You (1KB cap) → Email → Scheduled Tasks → Heartbeat → Context → kai-ui. Memory budget: 1024 chars.

### AutoMemoryLearner
Batched extraction every 5 exchanges via `askSilently()`. Stores unprotected memories. Dedup by key. Silently fails.

### HeartbeatMemoryExtractor
Post-heartbeat behavior extraction via `askSilently()`. Stores **protected** memories. Wired in `TaskScheduler.runHeartbeat()`.

### MCP Servers
- Popular servers in `composeApp/src/commonMain/kotlin/com/kai/custom/mcp/PopularMcpServers.kt`
- Jina AI (URL `https://mcp.jina.ai/v1`) uses `Authorization: Bearer <key>` header stored in `McpServerConfig.headers`
- API key field in `McpServerCard` for Jina AI only (detected by `url.contains("mcp.jina.ai")`)
- Key saved via `onUpdateMcpApiKey` → `dataRepository.updateMcpServerHeaders` → reconnects with new header
- Jina AI's `listTools()` returns 19 tools even without auth; auth checked at `callTool()` time
- Other MCP servers use URL + optional headers configured in the "Add Server" dialog

## Desktop Plan

Full plan at `docs/desktop-plan.md`. Approach: replace proot→Docker, Shizuku→AdminManager (UAC/sudo/pkexec), add `jvmShared` source set for pure-JVM tools. WhatsApp bridge runs in Docker on port 8317.

## Structure Verification

When Kotlin files have brace/paren mismatches (syntax errors cascading across many lines), count delimiters:

```bash
python3 -c "
data = open('composeApp/src/commonMain/kotlin/com/kai/custom/ui/settings/FILENAME.kt').read()
b_o, b_c = data.count('{'), data.count('}')
p_o, p_c = data.count('('), data.count(')')
print(f'braces {b_o}/{b_c}', 'OK' if b_o==b_c else f'MISMATCH diff={b_o-b_c}')
print(f'parens {p_o}/{p_c}', 'OK' if p_o==p_c else f'MISMATCH diff={p_o-p_c}')
"
```

To spot agent-introduced brace corruption: compare unchanged-strings-only diff. A change from `},\n)` (closing actions lambda + ToggleableHeadline) to just `}` or `)` is the classic sign.
