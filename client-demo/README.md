# APK 商店客户端 Demo

这是一个原生 Android Java Demo 工程，用本地 mock 数据演示内部 APK 商店客户端能力。

## 功能

- 模拟企业登录。
- 展示多应用、多环境、多渠道 APK 列表。
- 按环境、渠道、更新状态筛选。
- 展示包名、版本号、构建号、APK 大小、SHA-256、更新说明。
- 用 mock 本机安装版本计算更新状态。
- 模拟下载进度、SHA-256 校验、安装成功和结果上报。
- 展示下载任务、操作日志、设置和 mock 数据重置。

## 不包含

- 不连接真实服务端。
- 不真实下载 APK。
- 不调起系统安装器。
- 不读取真实手机已安装 App。

## 构建方式

当前机器未检测到 Java、Gradle 和 Android SDK，因此这里先交付可构建源码。

在有 Android Studio 的机器上：

1. 打开 `client-demo/`。
2. 等待 Gradle Sync 完成。
3. 选择 `app` 模块。
4. 执行 `Build > Build Bundle(s) / APK(s) > Build APK(s)`。
5. APK 一般生成在 `client-demo/app/build/outputs/apk/debug/app-debug.apk`。

命令行环境具备 Android SDK 时，也可以执行：

```bash
./gradlew :app:assembleDebug
```

Windows 下如没有 Gradle Wrapper，可用 Android Studio 先完成同步，或在具备 Gradle 的环境执行：

```bash
gradle :app:assembleDebug
```

