# Work handoff / current state

Snapshot as of 2026-07-24. This fork re-targets LIME to a new LINE version and modernizes it. Everything below is on GitHub (`andrewliang25/LIME-Reborn`) unless marked **machine-local**.

## Big picture

Three-stage plan for the LINE bump + modernization, plus CI/licensing/docs work done alongside.

1. **Stage 1 — retarget to LINE 26.11.0** (`versionCode 261100124`) on the *legacy* Xposed API. Re-derive obfuscated hook targets. **← in progress (PR #1).**
2. **Stage 2 — migrate to modern libxposed API 102** (rewrite entry point + all 27 hooks; no legacy APIs). Not started.
3. **Stage 3 — reimplement `removeIconLabels` / `removeServiceLabels`** via runtime view hooks (modern API has no resource hooking). Not started.

Detailed original plans lived in `~/.claude/plans/*.md` (machine-local, won't transfer): `jolly-hugging-crane.md` (LINE + libxposed migration) and `jazzy-twirling-balloon.md` (CI). Their content is summarized here and in `CLAUDE.md` / `DEBUG_TESTING.md`.

## Branches & PRs

| Branch | PR | Base | State |
|---|---|---|---|
| `line-26.11-libxposed-102` | **#1** | develop | WIP LINE 26.11.0 (Stage 1). MERGEABLE, but blocked on the 6 targets below + on-device verify. |
| `relicense-gplv3` | **#9** | develop | GPLv3 relicense (upstream stays MIT). MERGEABLE. |
| `master` | — | — | stable; full-release CI |
| `develop` | — | — | integration; pre-release CI. Already merged: PRs #2–#8 (CI, README EN, PR template EN). |

Merged already: #2 signed release+attestation, #3 English PR template, #4 develop→master sync, #5 Node-24 actions, #6/#8 immutable-release fix + `v`-prefix drop, #7 English README.

Rules on GitHub (persist automatically): ruleset "Protect master and develop" (deletion blocked); release signing secrets `STORE_FILE`/`STORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` set.

## Stage 1 status (branch `line-26.11-libxposed-102`)

Done & committed:
- `HOOK_TARGET_VERSION = 261100124`; module `versionCode 1`, `versionName 0.1.0`.
- **Thrift base re-derived:** `REQUEST_HOOK = org.apache.thrift.o#b` (sendBase), `RESPONSE_HOOK = org.apache.thrift.o#a` (receiveBase). Unblocks the 11 Thrift-dependent hooks.
- FQCN drift fixed: `WelcomeFragment` → `com.linecorp.line.registration.ui.fragment`; `InAppBrowserActivity` → `com.linecorp.line.iab.browser.impl`.
- `SendMuteMessage` uses name-based `getIdentifier` (was hardcoded ids). All 26 `getIdentifier` resource names verified present in 26.11.0.
- `DiagnosticLogger.java` added (temporary; registered in `Main.hooks`) + `DEBUG_TESTING.md`.
- Debug APK builds green (`./gradlew assembleDebug`).

**STILL TODO — the 6 obfuscated targets in `hooks/Constants.java` still hold old LINE-15 values and must be re-derived ON-DEVICE** (they can't be pinned statically):
`USER_AGENT_HOOK`, `WEBVIEW_CLIENT_HOOK`, `MUTE_MESSAGE_HOOK`, `MARK_AS_READ_HOOK`, `ARCHIVE_HOOK`, `NOTIFICATION_READ_HOOK`. Their features won't work until derived. See **`DEBUG_TESTING.md`** for the exact procedure (install LINE 26.11.0 + debug module → LSPosed → read `LIME-DIAG` log → fill in Constants). After deriving: **remove `DiagnosticLogger`** (delete the file + its `Main.hooks` line), rebuild, verify the full feature matrix, then this becomes the real "Support LINE 26.11.0".

## To resume on another machine

1. `git clone` the fork, `git fetch`, check out the branch you're continuing (`line-26.11-libxposed-102` for Stage 1).
2. **Recreate machine-local bits (all gitignored):**
   - `local.properties` with `sdk.dir=<Android SDK path>`.
   - JDK 21 (build requires it); point `JAVA_HOME` at it.
   - **git commit signing** (repo-local, must reconfigure): `git config gpg.format ssh`, `git config user.signingkey <your ssh pubkey>`, `git config commit.gpgsign true`, `git config tag.gpgsign true`, and set `git config user.email andrewliang25@gmail.com`. Your SSH key must be registered as a *signing* key on GitHub for the Verified badge.
   - **Release keystore for local release builds:** `app/release.jks` is gitignored and NOT in the repo. Either copy it over, or recreate it from the `STORE_FILE` GitHub secret. Password (also in secrets): kept out of this file — see your password manager. Alias `lime-reborn`. (CI already has the secrets, so CI releases work without the local file.)
3. **Re-derivation tooling (machine-local, under `/tmp` here — recreate as needed):** download the target LINE `.apkm`, extract `base.apk`, decompile with `jadx` (Java) and `apktool d` (resources → `res/values/public.xml`). Tooling was fetched to `/tmp/tools` (jadx 1.5.6, apktool 3.0.3) and output to `/tmp/line2611`.
4. Read **`CLAUDE.md`** (build/architecture/branching/CI/re-derivation rules) and **`DEBUG_TESTING.md`** (on-device derivation steps) — both are in-repo and transfer.

## Conventions reminder
- Feature branch → PR into `develop` → PR `develop` into `master`. All commits SSH-signed. PRs use the English template (`pr_moderation` enforces the tasklist).
- Release tags: `master` → `<ver>-<sha>` (full release), `develop` → `<ver>-dev-<sha>` (pre-release); both signed + attested.
