# BubbleButtonModule

Xposed 模块，为 Pixel Launcher 最近任务界面添加「消息气泡」功能。

## 功能

- 在多任务界面底部操作栏添加「🫧 消息气泡」按钮
- 在任务卡片菜单（点击 app 左上角图标）中添加「🫧 消息气泡」选项
- 点击后将当前选中的应用变为消息气泡模式
- 按钮样式、动画与原生按钮完全一致
- 按钮过多时自动换行居中显示

## 要求

- Android 8.0+ (API 26+)
- 已安装 LSPosed / KernelSU + Zygisk
- Pixel Launcher (NexusLauncher)

## 安装

1. 下载 [最新 Release](https://github.com/TYOPXN360/BubbleButtonModule/releases) APK
2. 在 LSPosed 中安装并启用
3. 设置作用域为 `com.google.android.apps.nexuslauncher`
4. 强制停止 Pixel Launcher 并重新打开

## 使用

### 方式一：底部操作栏
进入多任务界面 → 点击底部「🫧 消息气泡」按钮

### 方式二：任务卡片菜单
进入多任务界面 → 点击任务卡片左上角 app 图标 → 点击「🫧 消息气泡」

## 技术实现

基于 [libxposed API 102](https://github.com/libxposed/api)，Hook Pixel Launcher 的以下组件：

| Hook 目标 | 方法 | 作用 |
|-----------|------|------|
| `OverviewActionsView` | `onFinishInflate` | 注入底部操作栏按钮 |
| `OverviewActionsView` | `onClick` | 处理按钮点击 |
| `OverviewActionsView` | `updateHiddenFlags` | 控制按钮可见性 |
| `TaskMenuView` | `addMenuOptions` | 注入任务卡片菜单项 |
| `TaskOverlayFactory` | `getEnabledShortcuts` | 菜单快捷方式扩展 |

气泡触发通过 `SystemUiProxy.showAppBubble()` 调用 WMShell 的 Bubble 服务。

## 构建

```bash
export JAVA_HOME=/path/to/jdk-17
export GRADLE_USER_HOME=/path/to/.gradle
./gradlew assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/`

## License

Apache License 2.0
