# 0.1.3 测试记录

最终安装包版本：0.1.3 / versionCode 4。

最终安装包 SHA-256：`880b883918d038aececa1091cd8b529f7876205c22c87ac982493abcdb8f5056`。

## 已执行的检查

- 26 项纯 Kotlin 单元测试通过：15 项计时与随机逻辑、5 项配置迁移、6 项手动计时与剩余时间修改。
- debug、release 和三个空壳 flavor 编译通过。
- Lint 0 错误、11 条依赖版本、KTX、备份配置和图标资源建议。
- 最终 APK v2、v3 签名验证通过，发布清单没有 debug 测试入口。

## 官方虚拟机实际运行

Google 官方 Android Emulator 36.2.12，Android 15 / API 35，AOSP x86_64，720 × 1280。直接安装最终签名 APK，使用自写国服测试空壳，未下载安装真实游戏。

|检查|结果|
|---|---|
|从现在开始|立即开始完整 3 小时；再次点击更新开始与截止时间|
|编辑剩余时间|输入 1 小时 30 分钟，保存后立即更新截止时间|
|范围检查|拒绝 0 分钟、分钟字段 60、超过当前间隔；允许等于当前间隔|
|新设置的上限|间隔改为 2 小时后，编辑不允许 3 小时，允许 2 小时|
|保存与恢复|重新打开应用后保留修改过的截止时间|
|隐藏常驻通知|系统监测通知分类关闭，应用返回后显示已隐藏|
|隐藏后的服务|MonitorService 仍为前台服务，角色通知分类独立开启|
|手动修改后提醒|从 2 分钟缩短至 1 分钟，重进游戏不重置；锁屏时收到角色提醒|
|下一轮自动计时|提醒后再进入并离开游戏，自动开始 1 分钟；锁屏时再次提醒|
|通知中心|角色提醒可见，计时器的常驻监测通知不可见|
|运行日志|没有 AndroidRuntime 错误|

## 复现

基础逻辑：

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:assembleRelease :mockgame:assembleDebug :app:lintDebug
```

设备脚本需先自行准备 `.toolchain/android-sdk` 中的 platform-tools、emulator 和 `system-images/android-35/default/x86_64`；启动脚本使用 `AliceTimerTest` AVD、独立 ADB 端口 5042、虚拟设备端口 5580。

```powershell
.\scripts\start-emulator.ps1
# 将自己的签名发布包放到 dist/AliceTimer-0.1.3.apk，使用可 root 的测试镜像。
.\.toolchain\android-sdk\platform-tools\adb.exe -P 5042 -s emulator-5580 root
.\scripts\emulator-timer-controls-test.ps1
.\scripts\stop-emulator.ps1
```

测试会清理虚拟机中计时器的配置，恢复最终提醒间隔为 3 小时。调试脚本仅适用于 debug APK；发布包新功能脚本不依赖 debug 接收器。未提供 SDK、虚拟设备、签名密钥、原始系统转储或测试缓存，源码仓库只保留可复现的测试和摘要。
