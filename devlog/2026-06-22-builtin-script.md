# 2026-06-22 更新日志：内置 Python 脚本与使用说明

## 新增功能

### 1. 内置开发工具脚本
- 将 `android_dev_toolkit.py` 内置到 `app/src/main/assets/` 目录，随应用打包
- 终端面板顶部栏新增 **开发工具按钮**（Build 图标）
- 点击按钮后自动将脚本复制到外部存储，并在 Termux 中执行
- 支持的功能：环境检测、项目生成、编译、混淆保护、项目切换

### 2. Termux 检测优化
- 新增多包名检测，支持以下 Termux 变体：
  - `com.termux`（主版本）
  - `com.termux.stable`（稳定版）
  - `com.termux.beta`（测试版）
  - `com.termux.debug`（调试版）
- 添加 `QUERY_ALL_PACKAGES` 权限（Android 11+）
- 添加应用安装/卸载广播监听，自动更新检测状态
- 顶部栏新增手动刷新按钮

### 3. 自定义 Python 脚本支持
- 用户可以将自定义 Python 脚本放入 `assets/` 目录
- 通过 `python3 "<脚本路径>"` 命令调用

## 使用说明

### 启动内置开发工具
1. 打开应用终端面板（侧边栏 → 终端）
2. 确保已安装 Termux（顶部会显示绿色 Termux 标识）
3. 点击顶部栏右侧的 **开发工具按钮**（锤子图标）
4. 终端会自动执行 `android_dev_toolkit.py`

### 自定义 Python 脚本
1. 将 `.py` 脚本放入项目 `app/src/main/assets/` 目录
2. 重新编译应用
3. 在终端中执行：`python3 "/storage/emulated/0/Android/data/com.medeide.jh/files/tools/<脚本名>.py"`

### 环境要求
- **必须安装 Termux**：[F-Droid 下载](https://f-droid.org/packages/com.termux/)
- **必须安装 Python 3.8+**：在 Termux 中执行 `pkg install python`
- **需要授予存储权限**：首次启动开发工具时会自动复制脚本到外部存储

### 脚本路径
- 应用内部：`/data/app/.../base/assets/`
- 外部存储：`/storage/emulated/0/Android/data/com.medeide.jh/files/tools/`

## 修复的问题

- 修复 Termux 已安装但仍提示"未安装"的问题
- 修复代码片段模板按钮被挤压的布局问题
- 修复代码片段模板关闭按钮无法返回主界面的问题
- 修复最近文件和书签无法正常使用的问题
- 删除代码中的 emoji 图标，全部使用纯文字

## 技术改进

- 使用 `mutableStateOf` 替代 `remember`，支持状态动态更新
- 使用 `DisposableEffect` 管理广播接收器生命周期
- 终端命令执行改用 `Runtime.exec` 多线程读取输出
- 添加 `Looper.getMainLooper()` 确保 UI 更新在主线程

## 构建信息

- 构建时间: 2026-06-22
- 构建状态: 成功
- APK 位置: `app/build/outputs/apk/debug/app-debug.apk`
- Git 提交: `0abfa8d`
- 作者: `tuooiwc <tuooiwc@foxmail.com>`
