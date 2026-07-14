# APK Store Client Demo

Android APK store client demo with local mock data. It demonstrates app list, environment/channel filters, version comparison, mock download progress, SHA-256 verification, mock installation, task list, logs, and reset behavior.

## Build APK

Open the Actions tab and run **Build Client Demo APK** manually. Download the `apk-store-client-demo-debug` artifact after the workflow finishes.

Local build with Android SDK:

```bash
gradle -p client-demo :app:assembleDebug
```
