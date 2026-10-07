# 开源副本构建检查

检查日期：2026-10-07。版本：0.1.3（versionCode 4）。

从本交付目录的干净源码重新运行 `:core:test :app:assembleRelease :app:lintDebug`，使用 JDK 21、Android SDK Platform 36、Build Tools 36.0.0。SDK 和 Gradle 下载缓存由外部工具链提供，不包含在交付目录中。

- 构建成功，78 个任务全部执行。
- 单元测试 26 项通过：周期逻辑 15 项、配置迁移 5 项、手动计时 6 项；无失败或错误。
- Lint：0 个错误，11 个警告。
- 应用与测试模块的 42 个源码、资源文件与最终开发版本一致。
- Gradle Wrapper、模块配置和构建脚本已保留；构建脚本支持自行指定 JDK 和 SDK。
- 本机配置、SDK、虚拟机、构建缓存和签名私钥未包含在交付目录中。

重新构建产生的未签名 APK 和中间目录已清理。随本地交付提供的 `dist/AliceTimer-0.1.3.apk` 是先前完成虚拟机验证的最终签名包，与开发目录中的文件完全一致；它不是这次重新构建的未签名产物。

最终签名包 SHA-256：

```text
880b883918d038aececa1091cd8b529f7876205c22c87ac982493abcdb8f5056
```

虚拟机功能测试详情见 [TESTING.md](TESTING.md)。`dist/` 已被 Git 忽略，安装包可另行作为 GitHub Release 附件。
