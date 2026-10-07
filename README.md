# 爱丽丝计时器

作者：**bilibili w蝉蝉** · 当前版本 **0.1.3** · Android **8.0+**

《蔚蓝档案》咖啡厅摸头的本地提醒工具。Kotlin + Jetpack Compose；不自动点击、不读取游戏内部数据、不使用无障碍、不联网。

## 功能

- 自动检测进入、离开游戏，离开后开始倒计时，默认 3 小时。
- 计时期间重进游戏保留原截止时间；提醒后下一次进入并离开游戏开始新一轮。
- 首页「从现在开始」按当前间隔开始或重新计时。
- 「修改剩余时间」按小时、分钟输入，最少 1 分钟，不超过当前设置的提醒间隔，分钟为 0–59。
- 30 分钟、1/2/3 小时预设与自定义间隔；预设修改用于下一个周期。
- 爱丽丝、桃、绿、柚子随机角色提醒，台词增删改、启用/禁用，头像可自行替换。
- 国服、日服、国际服预设及已安装应用选择。
- 首次启动引导使用情况访问、通知、精确提醒、后台无限制和自启动。
- 常驻监测通知可通过系统「后台监测」分类单独隐藏，角色提醒使用独立分类。

## 构建

安装 Android Studio，打开仓库根目录。需要 JDK 17+、Android SDK Platform 36 和 Build Tools 36.0.0。项目使用 Gradle 8.13、AGP 8.13.2、Kotlin 2.2.10。

SDK 路径由 Android Studio 在本机生成 `local.properties`，或通过 `ANDROID_HOME` 指定。`local.properties` 不提交到 GitHub。

Windows：

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:assembleRelease :app:lintDebug
```

macOS / Linux：

```sh
chmod +x gradlew
./gradlew :core:test :app:assembleDebug :app:assembleRelease :app:lintDebug
```

中文 Windows 路径可使用脚本，它临时建立盘符别名，结束后自动移除：

```powershell
.\scripts\build.ps1 -JavaHome '<JDK目录>' -AndroidSdk '<Android SDK目录>'
```

产物：

- `app/build/outputs/apk/debug/app-debug.apk`：本机构建的调试包。
- `app/build/outputs/apk/release/app-release-unsigned.apk`：发布构建，需要使用自己的密钥签名。
- 构建脚本将未签名发布包复制为 `dist/AliceTimer-0.1.3-unsigned.apk`，不会替你使用开发者密钥签名。

本地交付目录 `dist/AliceTimer-0.1.3.apk` 是已验证的最终安装包，可作为 GitHub Release 附件；`dist/` 和 APK 默认不进入源码 Git 提交。本仓库不提供签名私钥。

## 计时与后台

本地保存开始时间、截止时间和计时阶段，用 AlarmManager 的精确唤醒闹钟发送提醒，WorkManager 负责补偿与恢复；界面不是计时依据。同一次开机使用单调时间校正系统时间修改，重启、应用更新和权限变化时恢复截止时间。

前台服务每 2 秒读取 UsageEvents，仅判断选定游戏的前后台状态。1.5 秒缓冲用于排除游戏内 Activity 切换。使用情况访问需要用户授权；自启动和后台无限制通过系统页面设置。

关闭「后台监测」通知分类不会关闭服务；关闭整个应用通知则会影响角色提醒。系统「强行停止」会终止后台工作，需要重新打开应用。没有精确提醒权限时，提醒可能延后。

## 目录与资源

```text
app/         Android 应用：ui、monitor、timer、notification、data
core/        纯 Kotlin 计时逻辑、随机台词、配置迁移和单元测试
mockgame/    三个版本包名的测试空壳，不包含真实游戏
gradle/      Gradle Wrapper
scripts/     Windows 构建及官方 Android 虚拟机测试脚本
docs/        测试与交付检查记录
LICENSES/    第三方许可证
```

角色头像目录：`app/src/main/assets/characters/{aris,momoi,midori,yuzu}/`。可放入 `avatar.png`、`avatar.webp` 或 `avatar.jpg`，也可从应用内导入；缺省使用角色文字占位。

启动器图标原图为 `app/src/main/res/drawable-nodpi/ic_launcher_art.jpg`，前景 XML 保留自适应图标留白。台词与人设参考见 [CHARACTER_DIALOGUES.md](CHARACTER_DIALOGUES.md)。

## 测试

0.1.3 的 26 项单元测试通过；最终签名包在官方 Android 15 虚拟机中验证手动计时、剩余时间限制、截止时间恢复，以及常驻通知隐藏后的自动监测和锁屏提醒。详情见 [docs/TESTING.md](docs/TESTING.md)。开源副本重新构建的结果见 [docs/BUILD_VERIFICATION.md](docs/BUILD_VERIFICATION.md)。

测试脚本使用项目目录内自行安装的 `.toolchain/android-sdk` 和可 `adb root` 的 AOSP 测试镜像；工具链、镜像、缓存和测试设备数据不随源码分发。脚本只接受官方虚拟机，使用测试空壳，拒绝覆盖真实游戏。`scripts/emulator-timer-controls-test.ps1` 会清理虚拟机里本应用的测试配置。

## 开源许可

项目代码采用 [MIT License](LICENSE)，允许自由修改和分发，须保留原作者版权声明及许可证。图片、角色资料和第三方组件的许可说明见 [NOTICE.md](NOTICE.md)。
