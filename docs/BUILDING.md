# 构建与测试说明

本文面向希望从源码编译或参与开发的人。仅下载安装使用 App 的用户无需进行以下操作。

[返回项目介绍](../README.md)

## 项目信息

- 技术栈：Kotlin、XML Views、ViewBinding。
- 应用包名：`io.github.captainrainbow.notificationhistoryhelper`。
- 当前版本：`0.1.4`。
- App 运行平台：Android 11 及以上。

下文中的 Windows、macOS 和 Linux 指用于编译的电脑系统，不是 App 的运行平台。生成的安装包仍用于 Android。

## 构建环境

准备 JDK 21、Android SDK Platform 36 和 Build Tools 36.0.0。可以通过 Android Studio 打开项目，或配置本机的 `JAVA_HOME` 与 Android SDK 路径后使用命令行。

Windows 下请将项目放在不含中文等非 ASCII 字符的目录中，以免 Android 构建插件拒绝加载。

项目自带 Gradle Wrapper：Windows 使用 `gradlew.bat`，macOS / Linux 使用 `gradlew`。它会按 `gradle/wrapper/gradle-wrapper.properties` 获取指定版本的 Gradle，已下载的版本会复用，因此不需要另行安装任意版本的 Gradle。首次构建可能需要联网下载 Gradle 和项目依赖。

## 本地构建与检查

在项目根目录执行。以下命令运行单元测试和 Lint 检查，并生成 Debug App 与设备测试安装包；不会自动安装到设备或运行设备测试。

Windows PowerShell：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
```

macOS / Linux：

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
```

## Debug 安装包与设备测试

Debug App 安装包位于 `app/build/outputs/apk/debug/app-debug.apk`，仅用于开发测试，不是正式签名发行版。

设备测试需要已启动的 Android 模拟器或已开启 USB 调试、完成调试授权的测试设备。普通用户使用已安装的 App 不需要启用 USB 调试。

测试请使用合成通知或其他非私人数据。提交问题截图或测试日志前，请移除通知正文、联系人等私人信息。

## 自动检查与本地文件

仓库的 GitHub Actions 工作流配置用于运行构建、单元测试、Lint 和 APK 权限检查，不自动上传安装包。工作流见 [android.yml](../.github/workflows/android.yml)。

`local.properties`、构建缓存、签名文件和密码只保存在本机，不应提交。自动检查不能代替上传前的隐私审核。
