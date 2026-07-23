# <img src="app/src/main/ic_launcher-playstore.png" width="60px"> LIME: Ad-killer for LINE

[![Latest Release](https://img.shields.io/github/v/release/andrewliang25/LIME-Reborn?label=latest)](https://github.com/andrewliang25/LIME-Reborn/releases/latest)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## Overview

This is an Xposed Module to clean [**LINE**](https://line.me).

## Usage
In the LINE app, go to <kbd>Home</kbd> > <kbd>⚙</kbd> to open **Settings**, then open it from the **LIME** button in the top right. Root users can also configure it from the LI**M**E app. With clone apps and the like, it seems configuration is sometimes only possible from the LI**M**E side.

<details><summary>View image</summary>

<a href="#"><img src="https://github.com/Chipppppppppp/LIME/assets/78024852/2f344ce7-1329-4564-b500-1dd79e586ea9" width="400px" alt="Sample screenshot"></a>

</details>

You can also turn on a switch from the <kbd>⁝</kbd> menu in the top right of a chat screen to **read messages while keeping them unread**. (This switch can be removed in the settings.)

Note: replying clears the unread status, so be careful.

<details><summary>View image</summary>

<a href="#"><img src="https://github.com/Chipppppppppp/LIME/assets/78024852/bd391a83-b041-4282-9eec-fe71b3b19aa0" width="400px" alt="Sample screenshot"></a>

</details>

## Features

- Remove unwanted bottom-bar icons
- Remove bottom-bar icon labels
- Remove ads and recommendations
- Remove service labels
- Set the navigation bar color to black
- Remove the "Turn off notifications" action from notifications
- Open WebViews in the default browser
- Never mark messages as read
- Check who has read a group message
  - Check via the "R" button shown at the top of a group chat
- Read while keeping messages unread
  - Configurable from the switch in the top-right menu of a chat screen (the switch can be removed)
- Reject message unsends
  - The content and time of the unsent message are saved
- Always send as a mute message
  - Selecting "Normal message" when sending will still notify
- Prevent hidden chats from reappearing
- Automatic chat-history backup (reference: https://github.com/areteruhiro/LIMEs/issues/10)
- Block tracking traffic
  - Blocks `noop`, `pushRecvReports`, `reportDeviceState`, `reportLocation`, `reportNetworkStatus`
- Log communication content
- Modify communication content
  - Communication can be modified with JavaScript (described below)

### Modifying communication content with JavaScript

<details>

In the settings' "Modify request" and "Modify response", you can freely modify communication content by writing Rhino JavaScript code. New features have been confirmed to be implementable this way (`communication_modification_sample.md`).

A variable named `data` is provided in advance, containing the following properties:

- `type`: an `Enum` that is either `REQUEST` or `RESPONSE`
- `name`: the name of the communication
- `value`: the communication content

Note: `data` is an instance of [this class](https://github.com/andrewliang25/LIME-Reborn/blob/master/app/src/main/java/io/github/chipppppppppp/lime/hooks/Communication.java), which you can inspect via "Log communication content".

The functions `getMember` and `setMember` are provided in advance to get and set member variables (using `.` may access a method rather than the member variable, so it is safer to use these functions).

```js
console.log(getMember(data.value, "a")); // get the value of member variable a
setMember(data.value, "a", false); // set member variable a to false
```

You can also output logs to `XposedBridge` with `console.log`. Errors are also output there.
For both requests and responses, the JavaScript runs earlier than other processing, and "Log communication content" runs last.
Note the specifics of Rhino — in particular, you must use **`equals` to compare against Java strings**.

</details>

## Installation

First, download the **LINE** and **LIME** APKs from one of the sites below.
Refer to the versions listed in Releases.

> [!IMPORTANT]
> Do not use split APKs.
> Do not force-merge them; always use the original APK.

LI**N**E
- [APKMirror](https://www.apkmirror.com/uploads/?appcategory=line)
- [APKPure](https://apkpure.net/jp/line-calls-messages/jp.naver.line.android/versions)
- [APKCombo](https://apkcombo.com/ja/line/jp.naver.line.android/old-versions/)
- [Uptodown](https://line.jp.uptodown.com/android/versions)

LI**M**E
- [Release](https://github.com/andrewliang25/LIME-Reborn/releases/latest)

### Rooted devices (Magisk)

1. Install [**LSPosed for JingMatrix**](https://github.com/JingMatrix/LSPatch/releases)

2. Install both the LI**N**E app and the LI**M**E app
3. To prevent automatic updates from the Google Play Store, specify the LINE app in [**Update Locker**](https://github.com/Xposed-Modules-Repo/ru.mike.updatelocker) or [**Hide My Applist**](https://github.com/Dr-TSNG/Hide-My-Applist).
  For [Aurora Store](https://auroraoss.com), use the blacklist.
4. In LSPosed, go to the LIME module, then check <kbd>Enable module</kbd> and the LINE app.

### Non-rooted devices

> [!WARNING]
> On non-rooted devices, there are the following issues:
> - Chat-history restore using a Google account (Drive) is not possible
>   (possible if you log in with [this method](https://github.com/Chipppppppppp/LIME/issues/50#issuecomment-2174842592))
> - Ringtones/call tones do not play
>   There is a feature that reproduces a simulated ringtone
> - Crashes when a call comes in
> - Purchasing coins is not possible
> - Some LINE Pay features are unusable
> - Cannot be used on Wear OS (smartwatches)

1. Install [**LSPatch**](https://github.com/LSPosed/LSPatch)
  Note: the fork [**NPatch**](https://github.com/HSSkyBoy/NPatch) may have issues.
  Also, if the app crashes when using the **official LSPosed** LSPatch, applying the patch with the fork [**JingMatrix LSPatch**](https://github.com/JingMatrix/LSPatch/) may work correctly.

2. Open the **LSPatch** app and apply the patch via: <kbd>Manage</kbd> > <kbd>＋</kbd> (bottom right) > <kbd>Select APK from storage</kbd> > select the LI**N**E APK you downloaded earlier > <kbd>Integrate</kbd> → <kbd>Embed modules</kbd> > <kbd>Select installed app</kbd> > check LI**M**E and press <kbd>＋</kbd> > <kbd>Start patch</kbd>.

Note: chat restore appears to be possible using [this method](https://github.com/Chipppppppppp/LIME/issues/50#issuecomment-2174842592).

> [!TIP]
> If <kbd>Select directory</kbd> appears, press <kbd>OK</kbd> to launch the file picker, create a folder under any directory, then press <kbd>Use this folder</kbd> > <kbd>Allow</kbd>.

3. If you use [**Shizuku**](https://github.com/RikkaApps/Shizuku), press <kbd>Install</kbd> to continue.
  If you don't, install from another app such as a file explorer.

> [!IMPORTANT]
> If a LINE app installed from the Play Store is already present, uninstall it first, because the signatures will conflict.

## Multi-device login

### 1. Log in as a PC
> [!WARNING]
> This method is currently unavailable.

<details><summary>View method</summary>

This disguises the app as the PC (Windows) version of LINE. This force-logs-out the PC version of LINE, but lets you move the PC-version LINE (which has features the mobile app lacks) onto Android LINE.

Note: if one of the devices is iOS, Letter Sealing may not work and you may be unable to receive messages, so follow [this method](https://github.com/Chipppppppppp/LIME/issues/88#issuecomment-2012001059) to do the Letter Sealing gacha. (You can find the key from <kbd>☰</kbd> > <kbd>Settings</kbd> > <kbd>Encryption key</kbd> in the top right of a chat with someone.)

- Pros: no issues with message sync; LIME only needs to be installed on one device; works even without root
- Cons: cannot log in on 3 or more devices; service icons are not shown on the second device

#### Steps

1. Install LINE and LIME on the other device
2. On the LINE login screen, check "Disguise as PC (DESKTOPWIN)"
3. From <kbd>Settings</kbd> > <kbd>Apps</kbd> > <kbd>LINE</kbd>, tap "Force stop" and "Clear cache" under "Storage & cache" on the LINE app's settings screen
4. Open the LINE app again and tap "Log in as secondary device" to log in
5. After logging in, tap "Back up/restore chats" in LINE's settings and restore chats older than two weeks

</details>

### 2. Spoof the Android ID
This method is only possible if **both devices are rooted**.
As described at <https://jesuscorona.hatenablog.com/entry/2019/02/10/010920>, note that a slight delay occurs in message sync and so on.

<details>

- Pros: can log in on 3 or more devices; all services usable
- Cons: a delay occurs in message sync; root-only

#### Steps

1. Install LINE and LIME
2. On the LINE login screen, check "Multi-device login (spoof Android ID)"
3. From <kbd>Settings</kbd> > <kbd>Apps</kbd> > <kbd>LINE</kbd>, tap "Force stop" and "Clear cache" under "Storage & cache" on the LINE app's settings screen
4. Open the LINE app again and log in
5. After logging in, back up the LINE app using [Swift Backup](https://play.google.com/store/apps/details?id=org.swiftapps.swiftbackup) (details [here](https://blog.hogehoge.com/2022/01/android-swift-backup.html))
6. Move Swift Backup's backup folder to the other device and install the backed-up LINE (details [here](https://blog.hogehoge.com/2022/05/SwiftBackup2.html))
7. Install LIME first, **without opening** the LINE app

</details>

## Reporting issues

If you find a new bug or a fix, please [report it](https://github.com/andrewliang25/LIME-Reborn/issues/new/choose).

> [!NOTE]
> If you understand Japanese, please write in Japanese.

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=andrewliang25/LIME-Reborn&type=Date)](https://star-history.com/#andrewliang25/LIME-Reborn&Date)
