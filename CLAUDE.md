# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

LIME is an **Xposed/LSPosed module** that modifies the LINE Android app (`jp.naver.line.android`) at runtime — removing ads, altering read receipts, blocking tracking, and modifying network traffic. It is not a standalone app; the code runs inside the LINE process after being loaded by the Xposed framework (Root via LSPosed, or non-Root via LSPatch). The module also ships a small settings `MainActivity` for configuring which features are enabled.

## Build & release

```bash
./gradlew assembleDebug      # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease    # release APK (uses release.jks if present, else debug key)
./gradlew build              # full build including lint
./gradlew clean
```

- **Requires JDK 21.** `compileSdk`/`targetSdk` = 35, `minSdk` = 28.
- **Local setup:** create `local.properties` with `sdk.dir=<Android SDK path>` (gitignored). The build needs the SDK (`ANDROID_HOME`/`sdk.dir`); on macOS a JDK 21 is typically at `~/Library/Java/JavaVirtualMachines/jbr-21.*/Contents/Home` — set `JAVA_HOME` to it if the default JDK differs.
- Debug builds are signed with the checked-in `app/android.jks` (password/alias/key all `android`). Release builds use `app/release.jks` + `STORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` env vars; run `./keystore.sh` to set these up locally. If `release.jks` is absent the build falls back to the debug key.
- There is **no test suite** — verification is manual (install the module, enable in LSPosed against LINE, exercise the feature). `lint.checkReleaseBuilds` is disabled.
- The Xposed API is `compileOnly` (provided by the framework at runtime). See **Branching, commits & CI** below for the workflows.

## Version coupling — important

This module is tightly coupled to a **specific LINE version**. `BuildConfig.HOOK_TARGET_VERSION` in `app/build.gradle` (currently targeting `"261100124"` = LINE 26.11.0) is the LINE `versionCode` this build targets. `CheckHookTargetVersion` warns the user when the installed LINE version doesn't match. LINE ships **R8-obfuscated**, so hook targets change between releases and must be re-derived from the new APK.

### Re-deriving hook targets when bumping LINE support

1. **Version code**: set `HOOK_TARGET_VERSION` to the target APK's `versionCode` (also found in an APKMirror `.apkm` bundle's `info.json`, or the filename). CI derives the LINE `versionName` from it (first 2 digits = major, next 2 = minor, 5th = patch).
2. **Decompile the target APK** (the DEX/resources live in `base.apk` inside a `.apkm` bundle): `jadx` for readable Java, `apktool d` for resources — `res/values/public.xml` maps resource name ↔ numeric id. jadx can stall on the full app; a full **smali** decode (`apktool d`, no `-s`) is faster and gives complete coverage for grepping string/class anchors.
3. **`hooks/Constants.java`** obfuscated `HookTarget`s — re-derive by behavior, not old name:
   - `REQUEST_HOOK`/`RESPONSE_HOOK` = the Thrift `TServiceClient` base, `org.apache.thrift.<obfuscated>` — method `b` = `sendBase` (writes message begin), `a` = `receiveBase` (reads response, has the "out of sequence response" check). The class letter drifts between versions (e.g. `l` → `o` for 26.11.0); the method letters `b`/`a` have been stable. **11 hooks depend on these — derive them first.**
   - The other 6 (`USER_AGENT_HOOK`, `WEBVIEW_CLIENT_HOOK`, `MUTE_MESSAGE_HOOK`, `MARK_AS_READ_HOOK`, `ARCHIVE_HOOK`, `NOTIFICATION_READ_HOOK`) are obfuscated app classes/coroutines/Runnables that generally **cannot be pinned down statically** — derive them **on-device** with `hooks/DiagnosticLogger.java` (see below), then confirm via the LSPosed log.
4. **"Stable" FQCNs drift too** — verify every `loadClass("...")`/`findClass` literal still exists in the new APK. Known 26.11.0 moves: `InAppBrowserActivity` → `com.linecorp.line.iab.browser.impl`, `WelcomeFragment` → `com.linecorp.line.registration.ui.fragment`.
5. **Resource references** — verify `getIdentifier(name, ...)` names in `public.xml`; **prefer name-based `getIdentifier` over hardcoded numeric resource ids** (numeric ids shift every version — `SendMuteMessage` was refactored for this).
6. Also sanity-check the version-fragile **Thrift request-name strings**, enum/payload markers, and the LINE SQLite schema used by `ReadChecker`/`UnsentCap`/`Archived`.

> **Migration in progress:** the module currently uses the legacy `de.robv.android.xposed:api`. A migration to the modern **libxposed API 102** is planned (rewrites the entry point + all hooks; the modern API has no resource hooking, so `removeIconLabels`/`removeServiceLabels` must move to runtime view hooks).

## Architecture

Everything lives under `app/src/main/java/io/github/chipppppppppp/lime/`.

- **`Main.java`** — the Xposed entry point (declared in `app/src/main/assets/xposed_init`). Implements the three Xposed lifecycle interfaces. `handleLoadPackage` bails unless the package is LINE, loads user preferences, then iterates a single **`hooks` array** applying each hook. `handleInitPackageResources` handles the few resource/layout replacements that must happen at inflation time (icon labels, service icon sizing).
- **`hooks/IHook.java`** — every feature implements this one-method interface: `void hook(LimeOptions, LoadPackageParam)`. Each hook first checks its own `limeOptions.<flag>.checked` and returns early if disabled, then installs its XposedBridge/XposedHelpers method hooks. **To add a feature: create a new `IHook` in `hooks/`, register it in the `Main.hooks` array, and add its toggle to `LimeOptions`.**
- **`LimeOptions.java`** — the single source of truth for feature toggles. Each `Option` binds a preference key (string, persisted in SharedPreferences) to an `R.string` label and a default. The `options[]` array is what `Main` and `MainActivity` both iterate.
- **`hooks/Constants.java`** — package names plus the obfuscated `HookTarget` (class + method) definitions. This is the file most sensitive to LINE version changes.
- **`MainActivity.java`** — the LIME settings screen, built entirely in code (no XML layout). Reads/writes SharedPreferences via `MODE_WORLD_READABLE` so the module can read them from inside LINE; catches `SecurityException` to detect "module not enabled."

### Two preference-delivery paths
Settings can reach the hooked LINE process two ways, controlled by the `unembed_options` toggle: normally options are read from LINE's own prefs (`xPackagePrefs`, written via `EmbedOptions`), but when unembedded they come from the module's `XSharedPreferences` (`xModulePrefs`). `Main.xPrefs` resolves to whichever is active.

### Network hooks & JS modification
`OutputRequest`/`OutputResponse` log Thrift traffic; `ModifyRequest`/`ModifyResponse` let users run **Rhino JavaScript** (dependency `org.mozilla:rhino`) to mutate requests/responses. The JS runs against a `Communication` instance exposed as `data` with `getMember`/`setMember` helpers. `BlockTracking` drops specific tracking calls (`noop`, `pushRecvReports`, etc.). See `communication_modification_sample.md` and the README's "JavaScript で通信内容を改変する" section for the scripting contract (note: Rhino requires `.equals` for Java string comparison).

## Branching, commits & CI

- **Branches:** `master` = stable (full release); `develop` = integration (pre-release). Do work on a feature branch → PR into `develop` → PR `develop` into `master`. Both `master` and `develop` are protected by a repository ruleset (**cannot be deleted**).
- **Commits must be SSH-signed** (repo has `commit.gpgsign=true`, `gpg.format=ssh`). Keep author `Andrew Liang <andrewliang25@gmail.com>` for this repo.
- **PRs** use the (English) template `.github/pull_request_template.md`. The `pr_moderation.yml` check enforces its task-list — keep the `<!-- Choice,multiple -->` markers and tick the boxes.
- **CI workflows:**
  - `build.yml` — builds on every push/PR; `workflow_dispatch` with `release: true` cuts an (upstream-oriented) GitHub release and can build an LSPatch-embedded variant.
  - `release.yml` — on push to **`master`** publishes a signed **full release**; on push to **`develop`** publishes a **pre-release** (`v<ver>-dev-<sha>`). Each is a per-commit tag with a GitHub **build-provenance attestation** (`actions/attest-build-provenance`). Requires repo secrets `STORE_FILE` (base64 of `app/release.jks`), `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; fails fast if absent. Verify a published APK with `gh attestation verify <apk> --repo <owner>/<repo>`.
- GitHub Actions are pinned to their **Node-24 majors** (checkout v5, setup-java v5, setup-gradle v5, upload-artifact v5, attest-build-provenance v3, action-gh-release v3).

## Conventions

- Hooks resolve LINE internals reflectively via `loadPackageParam.classLoader.loadClass(...)` + `XposedHelpers`/`XposedBridge`. Prefer routing obfuscated names through `Constants.HookTarget` rather than inlining string literals.
- Much of the codebase (README, code comments) is in **Japanese**.
- **Non-shipping files (do not ship / remove before release):** `hooks/test.java` is a scratch/experimental file not in the `Main.hooks` array. `hooks/DiagnosticLogger.java` is a **temporary** re-derivation aid — when registered in `Main.hooks` it logs all Thrift traffic + caller stack traces and traces `setWebViewClient`/mute-enum usage to help pin down obfuscated hook targets on-device; remove it (and its `Main.hooks` entry) before release.
