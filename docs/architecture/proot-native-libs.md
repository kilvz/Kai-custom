# proot Native Libraries

## Overview

The sandbox feature runs a full Linux userspace via `proot` on Android. The proot binary and its dependency `talloc` are built from source as Android native shared libraries (`.so`) and packaged into the APK under `androidApp/src/main/jniLibs/`.

## Target ABIs

| ABI | Directory | Status |
|-----|-----------|--------|
| `arm64-v8a` | `jniLibs/arm64-v8a/` | Production |
| `armeabi-v7a` | `jniLibs/armeabi-v7a/` | Production |
| `x86_64` | `jniLibs/x86_64/` | Production (emulator/desktop) |
| `x86` | Not built | Skipped — no device target |

## Patches

Two patches are applied to the upstream proot source (`4dba3af`) during build:

### 1. seccomp `FILTER_SYSEXIT` for linkat

`build-proot.sh` applies a `sed` substitution to `src/syscall/seccomp.c`, changing the seccomp filter flags for `PR_link` and `PR_linkat` from `0` (entry-only) to `FILTER_SYSEXIT` (trace both entry and exit).

**Why**: On Android 14+, `linkat()` returns `-EPERM` or `-EACCES`. Without the exit trap, proot never sees the failure and the syscall silently fails. With the exit trap, the exit handler can take corrective action.

### 2. Exit-handler copy fallback for linkat

`patches/exit.c` adds a handler in the `PR_link`/`PR_linkat` exit path. When the kernel returns `-EPERM` or `-EACCES`, proot falls back to an open-source read/write copy (emulating a hard link by duplicating file contents).

## Build Process

From the project root, using WSL (Ubuntu):

```powershell
wsl -d Ubuntu ANDROID_NDK_HOME=/mnt/c/Users/zethk/AppData/Local/Android/Sdk/ndk/29.0.14206865 /usr/bin/bash -c "cd /mnt/f/Kai && bash build-proot.sh"
```

This:
1. Clones the proot repo to `.build-native/proot-src/`
2. Applies the exit.c patch and seccomp filter fix
3. Builds talloc from source for each ABI
4. Builds proot against the NDK sysroot for each ABI
5. Copies `libproot.so`, `libproot-loader.so`, `libtalloc.so` to `androidApp/src/main/jniLibs/{abi}/`

## Deletion Artifacts

The talloc build produces `.so.2` versioned symlink files. These are not needed at runtime (Android loads only `lib*.so`) and are deleted before committing.

## CI Note

The `release.yml` workflow does **not** rebuild native libs. Pre-built `.so` files are committed to the repo and packaged as-is. To update them, run `build-proot.sh` locally and commit the result.
