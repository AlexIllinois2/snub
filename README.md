English | [简体中文](README.zh_CN.md) | [日本語](README.ja.md)

# Snub

[![Android CI status](https://github.com/AlexIllinois2/snub/workflows/Android%20CI/badge.svg)](https://github.com/AlexIllinois2/snub/actions)

Snub is a fork of [Hail](https://github.com/aistra0528/Hail), a free-as-in-freedom software to
freeze Android apps. [GitHub Releases](https://github.com/AlexIllinois2/snub/releases)

## Changes over upstream Hail

- **Renamed**: app name *Hail* → *Snub*, package name `com.aistra.hail` → `io.github.AlexIllinois2.snub`.
  API actions become `io.github.AlexIllinois2.snub.action.*`, while the URI scheme remains `hail://`.
- **Auto freeze on swipe**: a foreground service (Root working mode only) polls the recents screen and
  automatically freezes managed apps that are absent from recents, including apps swiped away or never seen.
  Polling interval, freeze delay after unfreeze and other options are configurable in Settings, and the
  service is restored on boot.
- **Batch home screen shortcuts**: create launcher shortcuts for all eligible frozen apps on a tag page
  in one go, instead of adding them one by one.
- **Auto-refresh apps list**: the apps page automatically refreshes on the first entry after the app
  returns to the foreground, so newly installed apps show up without a manual refresh.
- **Low battery auto shutdown**: a foreground service (Root working mode only) monitors the battery level and
  shuts the device down once it drops to the configured threshold. A countdown notification with a cancel button
  is shown before shutdown, and plugging in the charger also cancels the pending shutdown. Threshold and countdown
  length are configurable in Settings, and the service is restored on boot.
- **Per-tag auto-freeze policies**: each tag can individually enable *freeze after going to background*
  and *freeze after screen locked*, each with its own timeout in seconds (0 = freeze immediately).
  The global auto-freeze switch, delay and the quick settings tile "Auto freeze" toggle action were removed.
- **Single tag per app**: an app can only belong to one tag (previously multiple tags).
- **Silent home screen shortcuts**: optionally create home screen shortcuts silently (no confirmation
  dialogs) for newly managed apps, or batch-create silent shortcuts for managed apps without one from
  the home menu. Silent creation relies on the legacy `INSTALL_SHORTCUT` broadcast, which requires
  launcher support. Since Android 8.0 the system silently drops this broadcast from apps targeting
  API 26+, and modern launchers gate it behind per-app permissions; under the Root (or Shizuku with
  root) working mode, Snub instead delivers the broadcast as root via an `app_process` helper,
  bypassing both restrictions on launchers that still support the legacy broadcast.
- **CI releases**: GitHub Actions automatically builds and attaches an APK to a GitHub Release on tag push.

<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="32%" /> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="32%" /> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="32%" />

## Freeze

Freeze is a word that describes the action of **blocking (immediately stopping) apps when they are not needed/in-use (
on-demand request)** which in turn helps the device to cut down on the usage of RAM and save power. Users can also
unfreeze them to revert to their original state.

In general, "freeze" means disable, but also Snub can "freeze" apps by hiding and suspending them.

### Disable

Disabled apps will not be shown in the launcher and will be shown as "Disabled" in the installed apps list. Enable them
to revert the action.

### Hide

Hidden apps will not be shown in the launcher and in the installed apps list. Unhide them to revert the action.

> While in this state, which is almost like an uninstalled state, the package will be unavailable, however, the
> application data and the actual package file will not be removed from the device.

### Suspend (Android 7.0+)

Suspended apps will have their icons shown in grayscale within the device's launcher. Unsuspend them to revert the
action.

> While in this state, the application's notifications will be hidden, any of its started activities will be stopped and
> it will not be able to show toasts, dialogs or even play audio. When the user tries to launch a suspended app, the
> system will, instead, show a dialog to the user informing them that they cannot use this app while it is suspended.

Suspend only prevents the user from interacting with the app, it does **NOT** prevent the app from running in the
background.

## Working mode

**Any app that has been frozen on Snub will need to be unfrozen by the same working mode.**

1. For devices supporting wireless debugging (Android 11+) or rooted devices, `Shizuku` is recommended.

2. For rooted devices, `Root` is an alternative. **It is slower.**

| Privilege                                                                                         | Force Stop | Disable | Hide | Suspend | Uninstall/Reinstall (System Apps) |
|---------------------------------------------------------------------------------------------------|------------|---------|------|---------|-----------------------------------|
| Root                                                                                              | ✓          | ✓       | ✓    | ✓       | ✓                                 |
| Device Owner                                                                                      | ✗          | ✗       | ✓    | ✓       | ✗                                 |
| Privileged System App                                                                             | ✓          | ✓       | ✗    | ✗       | ✗                                 |
| [Shizuku](https://github.com/RikkaApps/Shizuku) (root)/[Sui](https://github.com/RikkaApps/Sui)    | ✓          | ✓       | ✓    | ✓       | ✓                                 |
| [Shizuku](https://github.com/RikkaApps/Shizuku) (adb)                                             | ✓          | ✓       | ✗    | ✓       | ✓                                 |
| [Dhizuku](https://github.com/iamr0s/Dhizuku)                                                      | ✗          | ✗       | ✓    | ✓       | ✗                                 |
| [Island](https://github.com/oasisfeng/island)/[Insular](https://gitlab.com/secure-system/Insular) | ✗          | ✗       | ✓    | ✓       | ✗                                 |

### Device Owner

**You must remove Snub as a device owner before you can uninstall it**

#### Set device owner by adb

[Android Debug Bridge (adb) Guide](https://developer.android.com/studio/command-line/adb)

[Download Android SDK Platform-Tools](https://developer.android.com/studio/releases/platform-tools)

Issue adb command:

```shell
adb shell dpm set-device-owner io.github.AlexIllinois2.snub/.receiver.DeviceAdminReceiver
```

In response, adb prints this message if device owner has been successfully set:

```
Success: Device owner set to package io.github.AlexIllinois2.snub. Active admin set to component {io.github.AlexIllinois2.snub/io.github.AlexIllinois2.snub.receiver.DeviceAdminReceiver}
```

Search the message by search engine otherwise.

#### Remove device owner

Settings > Remove Device Owner

### Privileged System App

The following privapp-permissions is required:

```xml
<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="io.github.AlexIllinois2.snub">
        <permission name="android.permission.PACKAGE_USAGE_STATS"/>
        <permission name="android.permission.FORCE_STOP_PACKAGES"/>
        <permission name="android.permission.CHANGE_COMPONENT_ENABLED_STATE"/>
        <permission name="android.permission.MANAGE_APP_OPS_MODES"/>
    </privapp-permissions>
</permissions>
```

To use this mode, you should install Snub as a privileged system app.

The recommended approach is to import Snub when building your ROM, here's an example for `Android.bp`:

```bp
android_app_import {
    name: "Snub",
    apk: "Snub.apk",
    privileged: true,

    dex_preopt: {
        enabled: false,
    },
    presigned: true,
    preprocessed: true,

    required: ["privapp-permissions_io.github.AlexIllinois2.snub.xml"]
}

prebuilt_etc {
    name: "privapp-permissions_io.github.AlexIllinois2.snub.xml",
    src: "privapp-permissions.xml",
    sub_dir: "permissions",
}
```

## Revert

### By adb

Replace com.package.name to the package name of target app.

```shell
# Enable app
adb shell pm enable com.package.name
# Unhide app (root required)
adb shell su -c pm unhide com.package.name
# Unsuspend app
adb shell pm unsuspend com.package.name
```

### Modify file

Access `/data/system/users/0/package-restrictions.xml`, this file stores the restrictions about apps. You can modify,
rename or just delete it.

- Enable app: Modify the value of `enabled` from 2 (DISABLED) or 3 (DISABLED_USER) to 1 (ENABLED)

- Unhide app: Modify the value of `hidden` from true to false

- Unsuspend app: Modify the value of `suspended` from true to false

### Wipe data by recovery

**None of my business :(**

## API

```shell
adb shell am start -a action -e key value
```

`action` can be one of the following constants:

- `io.github.AlexIllinois2.snub.action.LAUNCH`: Unfreeze and launch target app. If it is unfrozen, it will launch directly.
  `key="package"` `value="com.package.name"`

- `io.github.AlexIllinois2.snub.action.FREEZE`: Freeze target app. It must be checked at Home. `key="package"`
  `value="com.package.name"`

- `io.github.AlexIllinois2.snub.action.UNFREEZE`: Unfreeze target app. `key="package"` `value="com.package.name"`

- `io.github.AlexIllinois2.snub.action.FREEZE_TAG`: Freeze all non-whitelisted apps in the target tag. `key="tag"` `value="Tag name"`

- `io.github.AlexIllinois2.snub.action.UNFREEZE_TAG`: Unfreeze all apps in the target tag. `key="tag"` `value="Tag name"`

- `io.github.AlexIllinois2.snub.action.FREEZE_ALL`: Freeze all apps at Home. `extra` is not necessary.

- `io.github.AlexIllinois2.snub.action.UNFREEZE_ALL`: Unfreeze all apps at Home. `extra` is not necessary.

- `io.github.AlexIllinois2.snub.action.FREEZE_NON_WHITELISTED`: Freeze all non-whitelisted apps at Home. `extra` is not necessary.

- `io.github.AlexIllinois2.snub.action.FREEZE_AUTO`: Auto freeze apps at Home. `extra` is not necessary.

- `io.github.AlexIllinois2.snub.action.LOCK`: Lock screen. `extra` is not necessary.

- `io.github.AlexIllinois2.snub.action.LOCK_FREEZE`: Freeze all apps at Home and lock screen. `extra` is not necessary.

or use following `schema`:

- `hail://launch?package=xxx`

- `hail://freeze?package=xxx`

- `hail://unfreeze?package=xxx`

- `hail://freeze_tag?tag=xxx`

- `hail://unfreeze_tag?tag=xxx`

- `hail://freeze_all`

- `hail://unfreeze_all`

- `hail://freeze_non_whitelisted`

- `hail://freeze_auto`

- `hail://lock`

- `hail://lock_freeze`

## License

    Hail - Freeze Android apps
    Copyright (C) 2021-2026 Aistra
    Copyright (C) 2022-2026 Hail contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
