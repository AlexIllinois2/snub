[English](README.md) | [简体中文](README.zh_CN.md) | 日本語

# 怠 Snub

[![Android CI status](https://github.com/AlexIllinois2/snub/workflows/Android%20CI/badge.svg)](https://github.com/AlexIllinois2/snub/actions)

怠（Snub）は、[雹 Hail](https://github.com/aistra0528/Hail) のフォーク（fork）で、Android
アプリを凍結するための自由ソフトウェアです。[GitHub Releases](https://github.com/AlexIllinois2/snub/releases)

## 上流 Hail からの変更点

- **改名**：アプリ名 *Hail（雹）* → *Snub（怠）*、パッケージ名 `com.aistra.hail` →
  `io.github.AlexIllinois2.snub`。API action は `io.github.AlexIllinois2.snub.action.*` になり、
  URI スキームは引き続き `hail://` です。
- **スワイプ自動凍結**：フォアグラウンドサービス（Root 作業モードのみ）が最近タスク画面をポーリングし、
  最近のタスクに存在しない管理対象アプリを自動的に凍結します。スワイプで消されたアプリや一度も表示されていない
  アプリも含まれます。ポーリング間隔、解凍後の凍結遅延などのオプションは設定で変更でき、端末起動後に
  サービスは自動的に復元されます。
- **一括ホーム画面ショートカット作成**：タグページ内の条件を満たすすべての凍結済みアプリのホーム画面
  ショートカットをワンクリックでリクエストでき、一件ずつ追加する必要はありません。
- **アプリリスト自動更新**：アプリがフォアグラウンドに戻った後、最初にアプリリストページを開いたときに
  リストを自動更新します。新しくインストールしたアプリを手動更新なしで表示できます。
- **低電量自動シャットダウン**：フォアグラウンドサービス（Root 作業モードのみ）がバッテリー残量を監視し、
  設定したしきい値を下回ると端末を自動的にシャットダウンします。シャットダウン前にはキャンセルボタン付きの
  カウントダウン通知が表示され、充電器を接続しても保留中のシャットダウンはキャンセルされます。
  しきい値とカウントダウン秒数は設定で変更でき、端末起動後にサービスは自動的に復元されます。
- **タグごとの自動凍結ポリシー**：各タグで「バックグラウンド後の自動凍結」と「画面ロック後の自動凍結」を
  個別に有効化でき、それぞれ秒単位の遅延を設定できます（0 は即時凍結）。従来のグローバル自動凍結スイッチ、
  遅延オプション、クイック設定タイルの「自動凍結」切り替えアクションは削除されました。
- **アプリは 1 つのタグのみに所属可能**：1 つのアプリは 1 つのタグにのみ所属できます
  （以前は複数のタグに所属できました）。
- **サイレントホーム画面ショートカット作成**：「新しく管理したアプリのショートカットを自動作成（サイレント）」
  を有効にできるほか、ホーム画面右上のメニューから、まだショートカットを持たない管理対象アプリのショートカットを
  一括でサイレント作成できます。サイレント作成は従来の `INSTALL_SHORTCUT` ブロードキャストで実装されており、
  一件ずつの確認は不要ですが、ランチャー（ホームアプリ）がサイレント作成に対応している必要があります。
- **CI リリース**：GitHub Actions がタグのプッシュ時に自動ビルドし、APK を GitHub Release に添付します。

<img src="fastlane/metadata/android/zh-CN/images/phoneScreenshots/1.png" width="32%" /> <img src="fastlane/metadata/android/zh-CN/images/phoneScreenshots/2.png" width="32%" /> <img src="fastlane/metadata/android/zh-CN/images/phoneScreenshots/3.png" width="32%" />

## 凍結

凍結`freeze`は、**アプリが不要なときに即座に停止する**
（オンデマンドリクエスト）動作を指す言葉で、デバイスの使用をより良くし、RAMの使用量を削減し、電力を節約します。ユーザーは、アプリを解凍
`unfreeze`
して元の状態に戻すこともできます。

一般的に、「凍結」は無効化を意味しますが、Snub はアプリを隠したり、一時停止したりすることもできます。

### 無効化

無効化されたアプリは、ランチャーに表示されず、インストール済みアプリのリストには「無効」と表示されます。アプリを有効化
`enable`
して元に戻します。

### 隠す

隠されたアプリは、ランチャーやインストール済みアプリのリストに表示されません。アプリを表示`unhide`して元に戻します。

> この状態では、アプリはほぼアンインストールされた状態になりますが、アプリのデータや実際のパッケージファイルはデバイスから削除されません。

### 一時停止 (Android 7.0+)

一時停止されたアプリは、デバイスのランチャーでアイコンがグレースケールで表示されます。アプリを再開`unsuspend`して元に戻します。

> この状態では、アプリの通知は非表示になり、開始されたアクティビティは停止され、トースト、ダイアログ、オーディオの再生もできません。
> ユーザーが一時停止されたアプリを起動しようとすると、システムは代わりにユーザーに対してこのアプリを使用できないことを通知するダイアログを表示します。

一時停止は、ユーザーがアプリと対話するのを防ぐだけで、アプリがバックグラウンドで実行されるのを防ぐことは**ありません**。

## 作業モード

**Snub で凍結されたアプリは、同じ作業モードで解凍する必要があります。**

1. ワイヤレスデバッグをサポートするデバイス（Android 11+）またはroot化されたデバイスの場合、`Shizuku`を推奨します。

2. root化されたデバイスの場合、`Root`が代替手段です。**速度が遅いです。**

| Privilege                                                                                         | Force Stop | Disable | Hide | Suspend | Uninstall/Reinstall (System Apps) |
|---------------------------------------------------------------------------------------------------|------------|---------|------|---------|-----------------------------------|
| Root                                                                                              | ✓          | ✓       | ✓    | ✓       | ✓                                 |
| デバイス所有者                                                                                           | ✗          | ✗       | ✓    | ✓       | ✗                                 |
| 特権システムアプリ                                                                                         | ✓          | ✓       | ✗    | ✗       | ✗                                 |
| [Shizuku](https://github.com/RikkaApps/Shizuku) (root)/[Sui](https://github.com/RikkaApps/Sui)    | ✓          | ✓       | ✓    | ✓       | ✓                                 |
| [Shizuku](https://github.com/RikkaApps/Shizuku) (adb)                                             | ✓          | ✓       | ✗    | ✓       | ✓                                 |
| [Dhizuku](https://github.com/iamr0s/Dhizuku)                                                      | ✗          | ✗       | ✓    | ✓       | ✗                                 |
| [Island](https://github.com/oasisfeng/island)/[Insular](https://gitlab.com/secure-system/Insular) | ✗          | ✗       | ✓    | ✓       | ✗                                 |

### デバイス所有者

**アンインストールする前にデバイス所有者を削除する必要があります**

#### adbでデバイス所有者を設定する

[Android デバッグブリッジ (adb) ガイド](https://developer.android.com/studio/command-line/adb)

[Android SDK プラットフォームツールのダウンロード](https://developer.android.com/studio/releases/platform-tools)

adbコマンドを発行します：

```shell
adb shell dpm set-device-owner io.github.AlexIllinois2.snub/.receiver.DeviceAdminReceiver
```

デバイス所有者が正常に設定された場合、adbは次のメッセージを出力します：

```
Success: Device owner set to package io.github.AlexIllinois2.snub
Active admin set to component {io.github.AlexIllinois2.snub/io.github.AlexIllinois2.snub.receiver.DeviceAdminReceiver}
```

それ以外の場合は、検索エンジンでメッセージを検索してください。

#### デバイス所有者を削除する

設定 > デバイス所有者を削除

### 特権システムアプリ

次の特権アプリの権限が必要です：

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

このモードを使用するには、Snub を特権システムアプリとしてインストールする必要があります。

推奨される方法は、ROMをビルドする際に Snub をインポートすることです。`Android.bp`の例：

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

## 復元

### adbで

com.package.nameをターゲットアプリのパッケージ名に置き換えます。

```shell
# アプリを有効化
adb shell pm enable com.package.name
# アプリの非表示を解除（rootが必要）
adb shell su -c pm unhide com.package.name
# アプリの一時停止を解除
adb shell pm unsuspend com.package.name
```

### ファイルを変更する

`/data/system/users/0/package-restrictions.xml`にアクセスします。このファイルにはアプリの制限に関する情報が保存されています。これを変更、名前変更、または削除できます。

- アプリを有効化：`enabled`の値を2（DISABLED）または3（DISABLED_USER）から1（ENABLED）に変更します。

- アプリの非表示を解除：`hidden`の値をtrueからfalseに変更します。

- アプリの一時停止を解除：`suspended`の値をtrueからfalseに変更します。

### リカバリーモードでデータを消去する

**私の責任ではありません :(**

## API

```shell
adb shell am start -a action -e key value
```

`action`は次の定数のいずれかです：

- `io.github.AlexIllinois2.snub.action.LAUNCH`
  ：ターゲットアプリを解凍して起動します。解凍されている場合は、直接起動します。`key="package"` `value="com.package.name"`

- `io.github.AlexIllinois2.snub.action.FREEZE`
  ：ターゲットアプリを凍結します。ホームにチェックされている必要があります。`key="package"` `value="com.package.name"`

- `io.github.AlexIllinois2.snub.action.UNFREEZE`：ターゲットアプリを解凍します。`key="package"` `value="com.package.name"`

- `io.github.AlexIllinois2.snub.action.FREEZE_TAG`
  ：ターゲットタグ内のすべての非ホワイトリストアプリを凍結します。`key="tag"` `value="タグ名"`

- `io.github.AlexIllinois2.snub.action.UNFREEZE_TAG`：ターゲットタグ内のすべてのアプリを解凍します。`key="tag"` `value="タグ名"`

- `io.github.AlexIllinois2.snub.action.FREEZE_ALL`：ホームのすべてのアプリを凍結します。`extra`は必要ありません。

- `io.github.AlexIllinois2.snub.action.UNFREEZE_ALL`：ホームのすべてのアプリを解凍します。`extra`は必要ありません。

- `io.github.AlexIllinois2.snub.action.FREEZE_NON_WHITELISTED`：ホームのすべての非ホワイトリストアプリを凍結します。`extra`は必要ありません。

- `io.github.AlexIllinois2.snub.action.FREEZE_AUTO`：ホームのアプリを自動的に凍結します。`extra`は必要ありません。

- `io.github.AlexIllinois2.snub.action.LOCK`：画面をロックします。`extra`は必要ありません。

- `io.github.AlexIllinois2.snub.action.LOCK_FREEZE`：ホームのすべてのアプリを凍結し、画面をロックします。`extra`は必要ありません。

または次の`schema`を使用します：

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

## ライセンス

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
