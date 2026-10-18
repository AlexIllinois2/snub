# 项目长期备忘（snub / Hail）

## 构建环境（本机）
- JDK：`C:\Users\45574\.workbuddy\binaries\jdk\jdk-21.0.5+11`（项目 toolchain 要求 21）
- Android SDK：`C:\Users\45574\.workbuddy\binaries\android-sdk`（含 platform 36 / build-tools 36.0.0 / platform-tools）；已通过项目根 `local.properties` 的 `sdk.dir` 配置（该文件被 git 忽略）
- 构建命令：`JAVA_HOME=<上述JDK> ./gradlew assembleDebug|assembleRelease`；wrapper 为 Gradle 9.4.1（腾讯云镜像）
- 系统无 JAVA_HOME/ANDROID_HOME，无 Android Studio；Gradle 首次运行会自动下载发行版
- Git Bash 调用 adb 时 `/sdcard/...` 路径会被 MSYS 转换，需写成 `//sdcard/...`

## 签名
- 本机无 `signing.properties`（仅有 .sample），release 按项目脚本回退 debug 签名（CN=Android Debug）；如需正式签名需用户提供 keystore 并写入 signing.properties

## MuMu 模拟器验证
- 安装位置：`D:\乐游模拟器\MuMuPlayer`（MuMu 6.5.9 / Android 15，进程 MuMuNxMain/MuMuNxDevice）
- 连接：`adb connect 127.0.0.1:7555`（屏幕 1080x1920）
- 底部导航点击坐标：设置 (675,1850)、关于 (945,1850)
- 首次启动会弹通知权限对话框，"不允许"按钮坐标 (540,1235)，需先关闭再导航

## 界面结构备忘
- 导航：res/navigation/mobile_navigation.xml（nav_home/nav_apps/nav_settings/nav_about），MainActivity + BottomNavigationView（menu/nav_main.xml）
- 启动 Activity：`io.github.AlexIllinois2.snub.ui.main.MainActivity`；debug 包 applicationId 为 `io.github.AlexIllinois2.snub.debug`
