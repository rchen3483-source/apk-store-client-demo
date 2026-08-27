# APK 商店客户端 Demo

原生 Android Java MVP 客户端，直接调用研发环境 `admin-service` 的 APK 商店接口。

## 当前功能

- 从 `/admin/v1/apk-store/apps` 加载产品（`appCode`）。
- 根据产品从后端加载环境（`envCode`）。
- 加载最新版本和最近 5 个历史版本。
- 按版本号或构建号调用服务端搜索历史版本。
- 通过版本 `releaseId` 获取下载地址，显示真实下载进度。
- 下载完成后显示安装按钮，并使用 Android `FileProvider` 打开系统安装确认页。
- 根据设备已安装包的真实 `versionCode` 显示未安装、可更新、已最新等状态。

## 研发环境地址

默认地址配置在 `app/build.gradle` 的 `buildConfigField`：

```text
http://dev-transsaas.iflytranslate.com/admin
```

如研发环境域名调整，只需修改该字段后重新构建。

## 构建

工程使用 Android Gradle Plugin 8.5.2、compileSdk 35、minSdk 26，并依赖 AndroidX Core 1.13.1。

```bash
gradle -p client-demo :app:assembleDebug
```

APK 输出路径：`client-demo/app/build/outputs/apk/debug/app-debug.apk`。

本工作区未检测到 Java/Gradle/Android SDK，无法在本地完成 APK 编译；GitHub Actions 或 Android Studio 可直接构建。

