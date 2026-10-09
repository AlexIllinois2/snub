[English](README.md) | 简体中文 | [日本語](README.ja.md)

# 怠 Snub

[![Android CI status](https://github.com/AlexIllinois2/snub/workflows/Android%20CI/badge.svg)](https://github.com/AlexIllinois2/snub/actions)

怠（Snub）是 [雹 Hail](https://github.com/aistra0528/Hail) 的分支（fork），是一款用于冻结 Android
应用的自由软件。[GitHub Releases](https://github.com/AlexIllinois2/snub/releases)

## 相对上游 Hail 的修改

- **改名**：应用名 *Hail（雹）* → *Snub（怠）*，包名 `com.aistra.hail` → `io.github.AlexIllinois2.snub`。
  API action 变为 `io.github.AlexIllinois2.snub.action.*`，URI scheme 仍为 `hail://`。
- **划掉自动冻结**：前台服务（仅 Root 工作模式）轮询最近任务屏幕，自动冻结所有不在最近任务中的受管应用，
  包括被划掉的应用和从未出现过的应用。轮询间隔、解冻后冻结延时等选项可在设置中配置，开机后自动恢复服务。
- **批量创建桌面图标**：一键为标签页内所有符合条件的已冻结应用请求桌面快捷方式，无需逐个添加。
- **应用列表自动刷新**：应用回到前台后首次进入应用列表页时自动刷新列表，新装应用无需手动刷新即可显示。
- **低电量自动关机**：前台服务（仅 Root 工作模式）监控电池电量，电量降至设定阈值时自动关机。
  关机前会显示带取消按钮的倒计时通知，接入充电器也会取消即将执行的关机。阈值与倒计时时长可在设置中配置，
  开机后自动恢复服务。
- **标签级自动冻结策略**：每个标签可单独启用「后台后自动冻结」和「锁屏后自动冻结」，各自支持以秒为单位的
  延时（0 表示立即冻结）。原全局自动冻结开关、延时选项及快捷设置磁贴「自动冻结」切换动作已移除。
- **应用仅限单个标签**：一个应用只能属于一个标签（原先可属于多个标签）。
- **静默创建桌面快捷方式**：可开启「为新管理的应用自动创建快捷方式（静默）」，也可在首页右上角菜单一键为
  尚无快捷方式的受管应用批量静默创建快捷方式。静默创建通过传统 `INSTALL_SHORTCUT` 广播实现，无需逐个确认，
  但需要桌面（启动器）支持静默创建。
- **CI 发布**：GitHub Actions 在推送标签时自动构建并将 APK 附加到 GitHub Release。

<img src="fastlane/metadata/android/zh-CN/images/phoneScreenshots/1.png" width="32%" /> <img src="fastlane/metadata/android/zh-CN/images/phoneScreenshots/2.png" width="32%" /> <img src="fastlane/metadata/android/zh-CN/images/phoneScreenshots/3.png" width="32%" />

## 冻结

冻结`freeze`是一个营销用语，用于描述使**应用在用户不需要时不可运行**
的行为，以此控制设备使用、减少内存占用和节省电量。用户可在需要时解冻`unfreeze`应用。

在一般情况下，“冻结”是指停用，此外怠也可以通过隐藏和暂停来“冻结”应用。

### 停用

被停用`disable`的应用不会出现在启动器中。在已安装应用列表中会显示已停用`disabled`状态。启用`enable`应用即可恢复。

### 隐藏

被隐藏`hide`的应用不会出现在启动器和已安装应用列表中。取消隐藏`unhide`应用即可恢复。

> 在这种状态下，软件包几乎处于卸载状态，无法使用，但并没有删除数据或实际的软件包文件。

### 暂停 (Android 7.0+)

被暂停`suspend`的应用在启动器中会显示为灰度图标。取消暂停`unsuspend`应用即可恢复。

> 在这种状态下，应用程序的通知将被隐藏，任何启动活动将被停止，不能弹出提示、对话框或播放音频。
> 当用户试图启动一个暂停的应用程序时，系统将向用户显示一个对话框，告知他们在暂停状态下不能使用这个应用程序。

暂停只会阻止用户与应用交互，而**不会**阻止应用在后台运行。

## 工作模式

**冻结的应用需要通过相同工作模式解冻。**

1. 如果您的设备支持无线调试 (Android 11+) 或已 root，推荐选择`Shizuku`。

2. 如果您的设备已 root，可选择`Root`。**此模式速度相对较慢。**

| 授权方式                                                                                           | 强行停止 | 停用 | 隐藏 | 暂停 | 卸载/重新安装（系统应用） |
|------------------------------------------------------------------------------------------------|------|----|----|----|---------------|
| Root                                                                                           | ✓    | ✓  | ✓  | ✓  | ✓             |
| 设备所有者                                                                                          | ✗    | ✗  | ✓  | ✓  | ✗             |
| 特权系统应用                                                                                         | ✓    | ✓  | ✗  | ✗  | ✗             |
| [Shizuku](https://github.com/RikkaApps/Shizuku) (root)/[Sui](https://github.com/RikkaApps/Sui) | ✓    | ✓  | ✓  | ✓  | ✓             |
| [Shizuku](https://github.com/RikkaApps/Shizuku) (adb)                                          | ✓    | ✓  | ✗  | ✓  | ✓             |
| [Dhizuku](https://github.com/iamr0s/Dhizuku)                                                   | ✗    | ✗  | ✓  | ✓  | ✗             |
| [炼妖壶](https://github.com/oasisfeng/island)/[Insular](https://gitlab.com/secure-system/Insular) | ✗    | ✗  | ✓  | ✓  | ✗             |

### 设备所有者

**设置为设备所有者的应用需要移除设备所有者后方可卸载。**

#### 通过 adb 将怠设置为设备所有者

[Android 调试桥 (adb) 指南](https://developer.android.google.cn/studio/command-line/adb)

[下载 Android SDK 平台工具](https://developer.android.google.cn/studio/releases/platform-tools)

通过 adb 发出命令：

```shell
adb shell dpm set-device-owner io.github.AlexIllinois2.snub/.receiver.DeviceAdminReceiver
```

设置成功后会输出以下信息：

```
Success: Device owner set to package io.github.AlexIllinois2.snub
Active admin set to component {io.github.AlexIllinois2.snub/io.github.AlexIllinois2.snub.receiver.DeviceAdminReceiver}
```

如输出其他信息，请使用搜索引擎自行查阅与解决。

#### 移除怠的设备所有者

设置 > 移除设备所有者

### 特权系统应用

需要设置特许权限许可名单：

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

并将怠安装为特权系统应用。

推荐方法是在构建 ROM 时导入怠，`Android.bp`配置示例：

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

## 恢复

### 通过 adb

替换 com.package.name 为目标应用的包名。

```shell
# 启用应用
adb shell pm enable com.package.name
# 取消隐藏应用 (需要 root)
adb shell su -c pm unhide com.package.name
# 取消暂停应用
adb shell pm unsuspend com.package.name
```

### 修改文件

访问`/data/system/users/0/package-restrictions.xml`，此文件存储了应用限制相关信息。您可修改、重命名或直接删除此文件。

- 启用应用：修改`enabled`属性为 2 (DISABLED) 或 3 (DISABLED_USER) 的值为 1 (ENABLED)

- 取消隐藏应用：修改`hidden`属性为 true 的值为 false

- 取消暂停应用：修改`suspended`属性为 true 的值为 false

### 通过恢复模式清除数据 (wipe data)

**希望您能谨慎地选择冻结应用，以免陷入此窘境。**

## API

```shell
adb shell am start -a action -e key value
```

`action`可为：

- `io.github.AlexIllinois2.snub.action.LAUNCH`：解冻并启动目标应用。应用未冻结时会直接启动。`key="package"` `value="com.package.name"`

- `io.github.AlexIllinois2.snub.action.FREEZE`：冻结目标应用。应用需处于首页。`key="package"` `value="com.package.name"`

- `io.github.AlexIllinois2.snub.action.UNFREEZE`：解冻目标应用。`key="package"` `value="com.package.name"`

- `io.github.AlexIllinois2.snub.action.FREEZE_TAG`：冻结目标标签中的全部非白名单应用。`key="tag"` `value="标签名"`

- `io.github.AlexIllinois2.snub.action.UNFREEZE_TAG`：解冻目标标签中的全部应用。`key="tag"` `value="标签名"`

- `io.github.AlexIllinois2.snub.action.FREEZE_ALL`：冻结首页全部应用。无需`extra`。

- `io.github.AlexIllinois2.snub.action.UNFREEZE_ALL`：解冻首页全部应用。无需`extra`。

- `io.github.AlexIllinois2.snub.action.FREEZE_NON_WHITELISTED`：冻结首页全部非白名单应用。无需`extra`。

- `io.github.AlexIllinois2.snub.action.FREEZE_AUTO`：自动冻结首页应用。无需`extra`。

- `io.github.AlexIllinois2.snub.action.LOCK`：锁定屏幕。无需`extra`。

- `io.github.AlexIllinois2.snub.action.LOCK_FREEZE`：冻结首页全部应用并锁定屏幕。无需`extra`。

或使用以下`schema`:

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

## 许可证

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
