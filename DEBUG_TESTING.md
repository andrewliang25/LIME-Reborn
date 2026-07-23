# Debug testing / hook re-derivation guide

How to test a debug build on-device and derive the obfuscated hook targets when bumping LINE support. Applies to the current WIP branch (LINE **26.11.0**, `HOOK_TARGET_VERSION = 261100124`).

> Status on this branch: the two Thrift hooks are re-derived (`org.apache.thrift.o#b`/`#a`). The other six obfuscated targets in `hooks/Constants.java` still hold **old LINE-15 values** and must be re-derived on-device (below). Features that depend on them will misbehave until then.

## 0. Prerequisites
- A device or emulator with **LSPosed** (or JingMatrix **Vector**) installed, rooted (Magisk). *(Non-root LSPatch also works, but derivation via logs is easiest on LSPosed.)*
- `adb` connected (`adb devices` shows the device).
- The target **LINE 26.11.0** APK and the built **LIME debug** APK.

## 1. Install LINE 26.11.0
The download is an APKMirror `.apkm` bundle (base + splits); install all parts together:
```bash
cd /tmp/line2611   # base.apk was already extracted here during decompile
APK="/Users/andrewliang/Downloads/jp.naver.line.android_26.11.0-261100124_1arch_2dpi_b35561f3f55323937880bc586a544841_apkmirror.com.apkm"
unzip -o "$APK" 'split_config.*.apk' -d /tmp/line2611
adb install-multiple /tmp/line2611/base.apk /tmp/line2611/split_config.*.apk
```
(Or copy the `.apkm` to the device and use SAI / "Split APKs Installer".)

## 2. Install the LIME debug build
```bash
adb install -r /Users/andrewliang/Documents/projects/LIME-Reborn/app/build/outputs/apk/debug/app-debug.apk
```
Rebuild it first if needed:
```bash
JAVA_HOME=/Users/andrewliang/Library/Java/JavaVirtualMachines/jbr-21.0.6/Contents/Home ./gradlew assembleDebug
```

## 3. Enable the module
- LSPosed → **Modules → LIME** → enable it, tick **LINE** (`jp.naver.line.android`) in the scope.
- **Force-stop LINE** (Settings → Apps → LINE → Force stop) so hooks load fresh.
- Open LINE. If you see an "incompatible version" toast, `HOOK_TARGET_VERSION` doesn't match the installed LINE — fix that first.

## 4. Watch the log
```bash
adb logcat | grep LIME-DIAG
```
(Or read LSPosed's per-app log for `jp.naver.line.android`.) You should immediately see `DiagnosticLogger active …`, which confirms the module loaded. `console.log`/`XposedBridge.log` output also lands here.

## 5. Derive the 6 obfuscated targets
`DiagnosticLogger` (registered in `Main.hooks`) prints the data needed. Do each action, read the matching log line, and drop the class/method into `hooks/Constants.java`:

| Action in LINE | Log line to read | Fills in |
|---|---|---|
| Tap a web link → in-app browser opens | `[WEBVIEW] setWebViewClient -> <class>` | `WEBVIEW_CLIENT_HOOK` = that class, method `onPageFinished` |
| Open a chat with unread messages | `SIGNAL 'sendChatChecked' …` → first LINE frame with `run` | `MARK_AS_READ_HOOK` (that class + `run`) |
| Have someone read your message (or read from a 2nd device) | `SIGNAL 'NOTIFIED_READ_MESSAGE' …` → the `…invokeSuspend` frame | `NOTIFICATION_READ_HOOK` |
| Hide/archive a chat | `SIGNAL 'hidden:true' …` → the `…invokeSuspend` frame | `ARCHIVE_HOOK` |
| Long-press send → "Mute message", then send normally | `[MUTE] <class>#<method> args=…TO_BE_SENT_SILENTLY…` + `[MEMBERS]` dump | `MUTE_MESSAGE_HOOK` |
| (niche) "secondary Android" spoof flow | not auto-traced — see the header comment in `DiagnosticLogger.java` | `USER_AGENT_HOOK` |

For the stack-trace signals, the target is the **first LINE-owned frame** (short obfuscated name like `xy1.b`) that has `run` (Runnable) or `invokeSuspend` (coroutine). The `[MEMBERS]` dump lists a class's methods so you can pick the exact one. Also sanity-check `PreventUnsendMessage`'s reflective field walk (`sync` → `NOTIFIED_DESTROY_MESSAGE`) if unsend-prevention misbehaves.

## 6. Apply, clean up, re-verify
1. Put the derived class/method names into `hooks/Constants.java`.
2. **Remove `DiagnosticLogger`**: delete `hooks/DiagnosticLogger.java` and its two lines in `Main.java`.
3. Rebuild, reinstall, force-stop LINE.
4. Walk the full **feature matrix** and record pass/fail — no crash, no "incompatible version" toast:
   - **Thrift-dependent:** ad removal, block tracking, prevent unsend, keep-unread, read checker, archived-chat, ringtone, send-mute-message.
   - **Resource/UI:** remove bottom-bar icons, remove icon labels, remove service labels, embedded LIME settings button, redirect WebView, navbar color.
5. If a feature fails, use its `LIME-DIAG` / `XposedBridge.log` output to see which hook didn't fire and re-derive that single target.

## Notes
- `hooks/DiagnosticLogger.java` and this file are **debugging aids — remove `DiagnosticLogger` before any release** (it logs all Thrift traffic).
- Feature toggles live in `LimeOptions`; most non-default features must be enabled in the LIME settings screen before testing.
