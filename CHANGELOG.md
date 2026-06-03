# Changelog

## v3.8.0

### WhatsApp Integration
- feat: full WhatsApp bridge via Baileys — connect your WhatsApp account inside the Linux sandbox
- feat: QR code pairing with auto-refresh — scan to connect, just like WhatsApp Web
- feat: read-only mode (default on) — AI reads messages for context without replying
- feat: read-only mode stores incoming messages as memories so all personas stay in the loop
- feat: Baileys settings (browser name/version, markOnline, syncHistory, linkPreviews) configurable from app and Debug API
- feat: WhatsApp install uses direct Node.js v22 binary download (avoids dpkg/symlink issues under proot)
- fix: QR code now renders as a proper image (280dp) instead of raw base64 text

### Alt-Memory Backend
- feat: dual-write — `store()`, `updateContent()`, `reinforceMemory()`, `forget()` now write to both SQLite and alt-memory when connected
- feat: `POST /alt-memory/restart` Debug API endpoint for recovering from stale processes
- fix: alt-memory retry loop now calls `startMcpServer()` on each attempt (not just once), properly handling port conflicts and stale processes
- feat: alt-memory backend and embedder info shown in Memory settings tab when connected
- feat: `alt_memory_migration_complete` setting settable via Debug API

### Debug API
- feat: 77+ endpoints (up from 50) — full coverage of WhatsApp, alt-memory, and sandbox operations
- feat: `POST /alt-memory/restart` — restart alt-memory server on demand
- feat: WhatsApp endpoints — install, QR refresh, restart, settings, status
- feat: Baileys config keys exposed in settings endpoints

### Settings UI
- feat: Integrations tab with WhatsAppSection — toggle, read-only switch, Install button, QR display, Connected status
- feat: Memory tab shows alt-memory backend/embedder info when connected
- feat: alt-memory backend and embedder fields in SettingsUiState

### Sandbox
- feat: Ubuntu distro support with xz-utils in package list (required for Node.js binary extraction)
- feat: improved dpkg lock cleanup and retry logic for interrupted apt installs
- fix: apt-get timeout increased for slow mirrors, per-mirror recovery on failure

### Infrastructure
- feat: CI release workflow — per-ABI signed APK upload using glob patterns
- fix: version bump and release pipeline hardening
- docs: README rewritten — promotional summary reflecting current app (WhatsApp, alt-memory, Ubuntu/Alpine sandbox, 70+ endpoint Debug API, personas, two-tier memory)
- docs: full Debug API reference at docs/debug-api.md

## v3.7.7

- fix: Ubuntu package install — pre-create `_ssh`/`sshd` groups to avoid `linkat` EPERM under proot + SELinux (shadow-utils `groupadd` nlink check)
- fix: Ubuntu package install — remove stale dpkg lock files before every recovery attempt; split lock cleanup from `dpkg --configure -a` to avoid silent timeout (python rtupdate hooks need >60s)
- fix: Ubuntu package install — final recovery pass reconfigures half-installed packages after all phases complete
- fix: restore download-first apt pattern with per-mirror `apt-get update` before download; increase download timeout to 300s
- feat: add Update Alt-Memory button to Sandbox tab (visible when alt-memory is already installed)
- fix: increase pip download timeout 30→60 for transient DNS failures
