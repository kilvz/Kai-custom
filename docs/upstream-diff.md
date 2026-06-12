# Upstream Diff â€” Kai-custom vs upstream/main

Merge base: `727d3dbbf94` | Upstream HEAD: `bc7d622` (v2.7.0) | Our HEAD: `9047a65`

## Upstream commits we have NOT ported

Since merge base (727d3db), upstream has 12 new commits. We've cherry-picked 2 (`cfe9c0b` OpenAPI fix, `61a6ede` model scores). Remaining:

| Commit | Description | Status |
|--------|-------------|--------|
| `04c5407` | Add skill.md capability to linux sandbox | **PORTED** (our version) |
| `3b2acf4` | Merge remote-tracking branch | Skip (merge) |
| `90ae7a7` | Auto-fix spotless | Skip (formatting) |
| `79e1a30` | Merge remote-tracking branch | Skip (merge) |
| `4fbe2de` | Add create-skill.md | **PORTED** (our version) |
| `b30b809` | Release v2.7.0 | Skip (versioning) |
| `be68085` | Apply spotless | Skip (formatting) |
| `4be3a6d` | Auto-fix spotless + updateScreenshots | Skip |
| `59f0709` | Update Flatpak manifest to 2.7.0 | Skip |
| `bc7d622` | Update AUR package to 2.7.0 | Skip |

---

## 1. Files added upstream (not in our fork)

### Skills system (need to create with `com.kai.custom` package)
All already created in Phase A â€” location `com/kai/custom/skills/`:

| Upstream file | Our file | Status |
|---------------|----------|--------|
| `skills/SkillManifest.kt` | `skills/SkillManifest.kt` | âœ… |
| `skills/SkillFrontmatterParser.kt` | `skills/SkillFrontmatterParser.kt` | âœ… |
| `skills/SkillMarketplaces.kt` | `skills/SkillMarketplaces.kt` | âœ… |
| `skills/SkillRegistry.kt` | `skills/SkillRegistry.kt` | âœ… |
| `skills/SkillManager.kt` | `skills/SkillManager.kt` | âœ… |
| `ui/SandboxUriHandler.kt` | `ui/SandboxUriHandler.kt` | âœ… |
| `ui/chat/composables/SkillAutocomplete.kt` | `ui/chat/composables/SkillAutocomplete.kt` | âœ… |
| `ui/settings/SkillsSection.kt` | `ui/settings/SkillsSection.kt` | âœ… |
| `composeResources/files/skills/create-skill/SKILL.md` | `composeResources/files/skills/create-skill/SKILL.md` | âœ… |

### Test files (NOT created â€” different architecture)

| Upstream test file | Our status |
|--------------------|------------|
| `ChatSystemPromptBuilderTest.kt` | âŒ Skipped â€” we inject active skill via History.SYSTEM message, not via builder |
| `GitHubSkillUrlTest.kt` | âœ… Created |
| `SkillFrontmatterParserTest.kt` | âœ… Created |
| `SkillManagerTest.kt` | âœ… Created |
| `SkillMarketplaceManifestTest.kt` | âœ… Created |
| `FakeSandboxController.kt` | âœ… Created |
| `SandboxUriHandlerTest.kt` | âœ… Created |
| `ChatViewModelSkillTest.kt` | âŒ Skipped â€” our ChatViewModel doesn't parse `/cmd` internally; skill selected via UI then `ask()` separately |
| `DetectSlashQueryTest.kt` | âŒ Skipped â€” `detectSlashQuery` is inlined in QuestionInput, not a standalone function |

### Docs (NOT created â€” updated)
| Upstream doc | Our status |
|-------------|------------|
| `docs/features/skills.md` | âœ… Created |

---

## 2. Files that differ â€” must port

### Core skills integration â€” 16 files

| File | Key difference | Priority |
|------|---------------|----------|
| `DataRepository.kt` | +5 skill interface methods, `ask(activeSkillId)` | âœ… DONE |
| `RemoteDataRepository.kt` | `ask()`â†’`askInternal()`, skill methods impl, activeSkill plumbing | âœ… DONE |
| `AppModule.kt` | +`SkillManager` singleton wiring | âœ… DONE |
| `App.kt` | +`SandboxAwareUriHandler`, `LocalUriHandler` provider | âœ… PORTED |
| `ChatSystemPromptBuilder.kt` | +`activeSkill` param + `appendActiveSkillSection()` | âŒ NOT PORTED (cancelled â€” active skill injected as system message in ask()) |
| `ChatUiState.kt` | +`installedSkills: ImmutableList<SkillManifest>` | âœ… DONE |
| `ChatViewModel.kt` | +`parseSkillInvocation()`, installedSkills refresh | âœ… DONE |
| `ChatScreen.kt` | +installedSkills to QuestionInput | âœ… DONE |
| `QuestionInput.kt` | +slash autocomplete, `detectSlashQuery()` | âœ… DONE |
| `SettingsActions.kt` | +4 skill actions | âœ… DONE |
| `SettingsUiState.kt` | +6 skill state fields + `PendingDeletion.Skill` | âœ… DONE |
| `SettingsViewModel.kt` | +skill management logic | âœ… DONE |
| `SettingsScreen.kt` | +Skills section in Tools tab | âœ… DONE |
| `ToolsSettings.kt` | +SkillsSection integration | âœ… DONE |
| `FakeDataRepository.kt` | +skill stubs | âœ… DONE |
| `strings.xml` | +22 skill strings | âœ… DONE |

### Model scores update
| File | Difference | Status |
|------|-----------|--------|
| `ModelCatalog.kt` | +`hunyuan-hy3-preview`, updated Elo scores 2026-05-31 | âœ… Partially (scores updated, `hunyuan-hy3-preview` not confirmed) |

---

## 3. Files that differ â€” may port

| File | Difference | Priority |
|------|-----------|----------|
| `docs/features/sandbox.md` | Skills mention, SandboxUriHandler, updated date | LOW |
| `docs/features/system-prompts.md` | +"Active skill" section | LOW |

---

## 4. Files that differ â€” ignore

| File | Reason |
|------|--------|
| `CHANGELOG.md` | Fork has own versioning |
| `gradle/libs.versions.toml` | Fork at 3.1.0/133 |
| `aur/.SRCINFO`, `aur/PKGBUILD` | Linux packaging |
| `flatpak/*` | Linux packaging |
| `iosApp/Configuration/Config.xcconfig` | Android-only |
| All `values-*/strings.xml` (56 files) | Upstream has skill strings in all locales; we only have them in `values/strings.xml` |
| `screenshots/*` | Visual assets |

---

## 5. Our fork-only files (64 upstream doesn't have)

### Custom platform layer (18 files)
- `com/kai/custom/Platform.android.kt` + desktopMain/iosMain/wasmJsMain variants
- `com/kai/custom/SpeechToText.android.kt` + common
- `com/kai/custom/SshConnectionManager.android.kt` + common
- `com/kai/custom/data/dimension/DimensionModule.kt`
- `com/kai/custom/data/dimension/SqliteDimensionStore.kt`

### Memory system (12 files)
- Two-tier memory (protected field): `MemoryStore.kt`, `MemoryStoreProvider.kt`, `AltMemoryClient.kt`, `SqliteMemoryStore.kt`
- Soul split: `soul_user` + `soul_auto` in `AppSettings.kt` + `DataRepository`
- Dimension subsystem: `DimensionConfig.kt`, `DimensionStore.kt`, `EntityData.kt`, `ExportFormat.kt`
- MCP: `AltMemoryLifecycleManager.kt`

### Wake word system (5 files)
- `WakeWordService.kt`, `WakeWordInterpreter.kt`, `MfccProcessor.kt`
- `WakeWordController.kt` (common)
- `hey_kai.tflite` model

### Shizuku / privileged tools (4 files)
- `ShizukuManager.kt`, `CommandService.kt`, `ICommandService.kt`, `CommandResultDto.kt`

### Custom tools (9 files)
- `AdbTool.kt`, `OpenCodeTool.kt`, `ShellCommandTool.kt`, `SpeakTextTool.kt`
- `SshCommandTool.kt`, `SshConnectTool.kt`, `SshDisconnectTool.kt`
- `MicrophonePermissionController.android.kt`, `PhoneTools.kt`

### Custom UI (6 files)
- `EditMessageDialog.kt`, `GeneralSettings.kt`, `SshSettings.kt`, `SshTerminalContent.kt`, `SshViewModel.kt`
- `MemoryManagementSheet.kt` (with protected filter + toggle)

### Other
- `ChatActions.kt` (separate file, not inside ChatViewModel)
- `ChatSystemPromptBuilder.kt` (trimmed version â€” removed 6 sections)
- `Persona.kt` + `PersonaManager.kt`
- `AutoMemoryLearner.kt`, `HeartbeatMemoryExtractor.kt`
- `ToolsContent` (separate component, upstream keeps in SettingsScreen)

---

## 6. Architecture divergences

| Aspect | Upstream | Fork |
|--------|----------|------|
| **Memory** | SQLite + simple memory | Two-tier (protected/unprotected), alt-memory MCP server, dimension store |
| **Soul** | Single `soul_text` | Split `soul_user` + `soul_auto`, persona system |
| **Sandbox** | Alpine only | Alpine + Ubuntu distro selection |
| **Tools** | Basic set | +SSH, ADB, Shizuku, TTS, OpenCode, enhanced shell |
| **ChatSystemPrompt** | All sections | Trimmed (removed 6 sections) |
| **Wiring** | Monolithic DI | Koin-based, `SkillManager` via DI |
| **Active skill** | Via `ChatSystemPromptBuilder` | Via `History.SYSTEM` message in `ask()` |
| **Version** | 2.7.0 (107) | 3.1.0 (133) |
| **Package** | `com.inspiredandroid.kai` | `com.kai.custom` |

---

## 7. Remaining work

### Before build verification
- (none â€” all Phase B edits done)

### All done
1. âœ… **App.kt SandboxAwareUriHandler** â€” `LocalUriHandler` provided for `file://` links
2. âœ… **Test files** â€” 6 created (GitHubSkillUrl, SkillFrontmatterParser, SkillManager, SkillMarketplaceManifest, SandboxUriHandler, FakeSandboxController); 3 skipped (ChatSystemPromptBuilderTest, ChatViewModelSkillTest, DetectSlashQueryTest â€” architecture diff)
3. âœ… **skills.md doc** â€” created
4. âœ… **hunyuan-hy3-preview** already in ModelCatalog (from earlier port)
5. âœ… **Skill strings in all 56 locale files** â€” translated per locale (snackbar_skill_removed only)
6. âœ… **SandboxUriHandler wired in App.kt**
