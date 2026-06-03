# Admin Terminal — AI Copilot Integration

## Objective

Replace the current line-based sandbox terminal with a proper PTY-backed terminal emulator, paired with an AI copilot panel for system administration tasks. The AI can read terminal output and inject commands into the same shell session.

## Architecture

```
┌──────────────────────────────────────────────┐
│  Kai-custom App                              │
│                                              │
│  ┌─────────────┐  ┌──────────────────────┐   │
│  │ AI Chat      │  │ Admin Terminal Page  │   │
│  │ Panel        │  │                      │   │
│  │              │  │  ┌────────────────┐  │   │
│  │  • Chat log  │  │  │ TerminalView   │  │   │
│  │  • Input     │  │  │ (ANSI render)  │  │   │
│  │  • Tool call │  │  │                │  │   │
│  │    results   │  │  │  • scrollback  │  │   │
│  │              │  │  │  • selection   │  │   │
│  │              │  │  │  • keyboard    │  │   │
│  └──────┬───────┘  │  │  • Ctrl/Alt    │  │   │
│         │          │  └───────┬────────┘  │   │
│         │          │          │           │   │
│         └──────────┼──────────┘           │   │
│                    │                      │   │
│         ┌──────────▼──────────┐           │   │
│         │  TerminalSession    │           │   │
│         │  (PTY management)   │           │   │
│         │                     │           │   │
│         │  script -q -c bash  │           │   │
│         │  ┌───────────────┐  │           │   │
│         │  │ proot sandbox │  │           │   │
│         │  │  Ubuntu/Alpine│  │           │   │
│         │  └───────────────┘  │           │   │
│         └─────────────────────┘           │   │
└──────────────────────────────────────────────┘
```

## Phase 1 — PTY for the persistent shell

**Goal**: Programs inside the sandbox see a real TTY so interactive prompts (apt-get, passwd, debconf) work.

### Changes

1. **`PersistentSandboxShell.ensureShell()`** — Wrap bash in `script`:
   ```
   exec script -q -c "exec bash --noprofile --norc" /dev/null
   ```
   `script` allocates a PTY pair. The bash process (and its children) see `isatty(0) = true`. All PTY output flows to `script`'s stdout, which proot pipes to our reader.

2. **Conditional PTY per session** — The AI tool sessions keep the current non-PTY setup to preserve stdout/stderr separation and avoid ANSI garbage in AI output. Only `SandboxSessions.TERMINAL` gets the `script` wrapper.

3. **Terminal input forwarding** — When a command is running, the input bar stays visible. The user types into it and `writeInput()` delivers the text to the PTY master (which appears as keystrokes to the foreground process).

### Testing
- `apt-get install htop` — shows `[Y/n]`, user types `y`, install proceeds
- `passwd` — prompts for password, user types it
- `ssh user@host` — prompts for password, user types it
- `apt-get update` — runs without interactive prompts

## Phase 2 — Proper terminal emulator page

**Goal**: A new "Admin Terminal" page with full ANSI escape rendering, scrollback, keyboard shortcuts, and a split-view AI panel.

### Terminal engine options

| Option | How | Pros | Cons |
|--------|-----|------|------|
| **xterm.js via WebView** | `android.webkit.WebView` loading local xterm.js | Full xterm compat, cross-platform, battle-tested | WebView overhead, JS bridge complexity |
| **termux-terminal-emulator** | Maven lib `io.termux:termux-terminal-emulator` | Native Android, proven, ANSI + selection + gestures | Android-only, JNI dep |
| **Custom Compose Canvas** | Draw terminal cells with Compose Canvas API | No WebView/JNI, pure Compose | Months of work, reinventing xterm |

**Recommendation**: xterm.js in a WebView. Works on both Android (WebView) and Desktop (JavaFX WebView or JCEF). xterm.js handles 100% of ANSI sequences, mouse, clipboard, and is maintained by a large community.

### WebView bridge interface

```
JS → Kotlin:
  terminal.onOutput(line: string)     → read by terminal renderer
  terminal.onResize(cols, rows)       → resize PTY
  terminal.onTitleChange(title)       → update window title

Kotlin → JS:
  terminal.write(data: string)        → output from PTY slave
  terminal.clear()                    → clear screen
  terminal.focus()                    → focus input
```

### Layout

```
┌──────────────────────┬─────────────┐
│                      │             │
│   Terminal View      │  AI Chat    │
│   (xterm.js)         │  Panel      │
│                      │             │
│   ~70% width         │  ~30%       │
│                      │             │
│                      │  • Chat     │
│                      │  • Suggest  │
│                      │  • Explain  │
│                      │  • Diagnose │
│                      │             │
└──────────────────────┴─────────────┘
```

### Pages
- **New**: `AdminTerminalPage.kt` — full page with split layout
- **Reused**: `TerminalSheet.kt` can stay as a quick-access bottom sheet (light terminal usage)

## Phase 3 — AI terminal copilot

**Goal**: The AI can read terminal output, suggest/diagnose, and inject commands into the terminal session.

### AI tool: `terminal_command`

New tool available to the AI when the Admin Terminal page is active:

```
terminal_command:
  description: Execute a command in the shared admin terminal session
  params:
    command: string (required)
    wait: bool (default true — wait for output before returning)
    timeout: int (seconds, default 30)
  returns:
    output: string (terminal output since the command ran)
    exit_code: int
```

### AI terminal awareness
- The AI sees a condensed transcript of recent terminal output (last 50 lines) injected into its system prompt when the Admin Terminal is active
- The AI can suggest commands based on what it sees
- Commands the AI runs appear in the terminal as if typed by a user (shown with a visible marker like `[AI]$ command`)

### User interaction flow
1. User asks AI in the chat panel: "install htop"
2. AI calls `terminal_command(command="apt-get install htop")`
3. Command runs in the terminal, output streams to both the terminal view and the AI's response
4. AI reports result back in chat
5. User sees the command and output live in the terminal view

## Open Questions

1. **Shared vs dedicated sandbox** — Should the Admin Terminal share the same proot sandbox filesystem as chat sessions? Sharing means files installed in the terminal are immediately available to AI tools. Isolation means the AI can't mess with the user's terminal (or vice versa).
2. **Multiple terminal tabs** — Should the Admin Terminal support multiple tabs (like Termux)? Each tab would be a separate bash session in the same proot sandbox.
3. **WebView startup time** — xterm.js loads about 500KB of JS. On slow devices, the terminal page might take 1-2 seconds to become interactive. Acceptable?
4. **Distro choice in terminal** — Should the Admin Terminal default to the same distro as the sandbox setting, or allow independent selection?

## Dependencies to Add

- `androidx.webkit:webkit:1.12+` — WebView on Android (already present in most projects)
- xterm.js (bundled as local asset in `composeApp/src/commonMain/composeResources/`) — terminal emulator
- WebView-JS bridge helpers

## Timeline Estimate

| Phase | Scope | Effort |
|-------|-------|--------|
| 1 | PTY via script, input forwarding | ~2-3 hours |
| 2 | xterm.js WebView page, split layout | ~1-2 days |
| 3 | AI copilot tools, terminal awareness | ~1 day |
| **Total** | | **~3-5 days** |
