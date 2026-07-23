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
- Debug builds are signed with the checked-in `app/android.jks` (password/alias/key all `android`). Release builds use `app/release.jks` + `STORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` env vars; run `./keystore.sh` to set these up locally. If `release.jks` is absent the build falls back to the debug key.
- There is **no test suite** — verification is manual (install the module, enable in LSPosed against LINE, exercise the feature). `lint.checkReleaseBuilds` is disabled.
- CI (`.github/workflows/build.yml`) builds on every push; `workflow_dispatch` with `release: true` cuts a GitHub release and can also build an LSPatch-embedded variant. The Xposed API is `compileOnly` (provided by the framework at runtime).

## Version coupling — important

This module is tightly coupled to a **specific LINE version**. `BuildConfig.HOOK_TARGET_VERSION` in `app/build.gradle` (currently `"150000454"`) is the LINE `versionCode` this build targets. When bumping LINE support you almost always also update the **obfuscated class/method names** in `hooks/Constants.java` — LINE ships R8-obfuscated, so hook targets like `Sg1.c#j` or `Lf1.b#I` change between releases and must be re-derived from the new APK. `CheckHookTargetVersion` warns the user when the installed LINE version doesn't match.

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

## Conventions

- Hooks resolve LINE internals reflectively via `loadPackageParam.classLoader.loadClass(...)` + `XposedHelpers`/`XposedBridge`. Prefer routing obfuscated names through `Constants.HookTarget` rather than inlining string literals.
- Much of the codebase (README, code comments, PR descriptions) is in **Japanese**. PRs are expected to fill in the task-list checkboxes in the template — a CI check (`pr_moderation.yml`) enforces this.
- `hooks/test.java` is a scratch/experimental file, not part of the shipped hook array.
