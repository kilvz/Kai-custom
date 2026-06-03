# Changelog

## v3.7.7

- fix: Ubuntu package install — pre-create `_ssh`/`sshd` groups to avoid `linkat` EPERM under proot + SELinux (shadow-utils `groupadd` nlink check)
- fix: Ubuntu package install — remove stale dpkg lock files before every recovery attempt; split lock cleanup from `dpkg --configure -a` to avoid silent timeout (python rtupdate hooks need >60s)
- fix: Ubuntu package install — final recovery pass reconfigures half-installed packages after all phases complete
- fix: restore download-first apt pattern with per-mirror `apt-get update` before download; increase download timeout to 300s
- feat: add Update Alt-Memory button to Sandbox tab (visible when alt-memory is already installed)
- fix: increase pip download timeout 30→60 for transient DNS failures
