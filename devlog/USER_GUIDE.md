# Mede IDE 用户使用指南

## 目录
1. [环境配置](#环境配置)
2. [终端使用](#终端使用)
3. [开发工具](#开发工具)
4. [常见问题与解决方案](#常见问题与解决方案)
5. [已知限制](#已知限制)

---

## 环境配置

### 必需组件

#### 1. Termux 安装
Mede IDE 的终端功能依赖 Termux 提供真实的 shell 环境。

**安装步骤：**
- 从 F-Droid 下载：https://f-droid.org/packages/com.termux/
- **注意：不要从 Google Play 安装**，Google Play 版本已停止更新且功能受限

**验证安装：**
打开 Termux，执行 `echo $HOME`，应显示 `/data/data/com.termux/files/home`

#### 2. Termux 外部应用权限配置
**必须配置此项，否则终端无法执行命令！**

在 Termux 中执行以下命令：
```bash
mkdir -p ~/.termux
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings
```

**验证配置：**
执行 `cat ~/.termux/termux.properties`，应看到 `allow-external-apps=true`

#### 3. Python 环境（可选，用于开发工具脚本）
```bash
pkg install python
```

验证：`python3 --version`

#### 4. 其他常用工具（可选）
```bash
pkg install git nodejs clang make vim
```

---

## 终端使用

### 基本操作
1. 点击侧边栏的「终端」图标打开终端面板
2. 在底部输入框输入命令
3. 点击发送按钮或按回车执行

### 会话管理
- 支持多会话，点击顶部「+」按钮创建新会话
- 点击会话标签切换会话
- 长按会话标签可关闭会话

### 当前路径
- 显示在输入框左侧
- 使用 `cd <路径>` 切换目录

### 开发工具按钮
- Termux 已安装时，顶部栏显示「Build」图标
- 点击可启动内置的 Android 开发工具脚本

---

## 开发工具

### 内置脚本功能
应用内置 `android_dev_toolkit.py`，提供以下功能：

1. **环境检测** - 检测 Android SDK、Java、Python 等环境
2. **项目生成** - 创建新的 Android 项目模板
3. **编译构建** - 执行 Gradle 构建命令
4. **混淆保护** - 代码混淆和签名配置
5. **项目切换** - 快速切换工作项目

### 自定义脚本
用户可以将自己的 Python 脚本放入项目的 `app/src/main/assets/` 目录：
1. 将 `.py` 文件放入 assets 目录
2. 重新编译应用
3. 在终端中执行：`python3 <脚本路径>`

---

## 常见问题与解决方案

### 问题 1：终端显示「需要 Termux」但已安装

**原因：**
- 安装了 Google Play 版本的 Termux（已废弃）
- Termux 包名不是 `com.termux`

**解决方案：**
1. 卸载当前 Termux
2. 从 F-Droid 安装：https://f-droid.org/packages/com.termux/
3. 点击终端右上角的刷新按钮重新检测

### 问题 2：命令执行无输出或报错

**原因：**
未配置 Termux 外部应用权限

**解决方案：**
在 Termux 中执行：
```bash
mkdir -p ~/.termux
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings
```

### 问题 3：显示「python3: inaccessible or not found」

**原因：**
未在 Termux 中安装 Python

**解决方案：**
在 Termux 中执行：
```bash
pkg install python
```

### 问题 4：开发工具按钮点击无反应

**原因：**
- Python 未安装
- 脚本复制失败

**解决方案：**
1. 确保已安装 Python：`pkg install python`
2. 检查应用存储权限是否已授予
3. 查看终端输出中的错误信息

### 问题 5：命令执行超时（30秒无响应）

**原因：**
- 命令本身执行时间过长
- Termux RunCommandService 未正确响应

**解决方案：**
1. 对于长时间命令，建议直接在 Termux 中执行
2. 检查 Termux 是否正常运行（打开 Termux 应用确认）
3. 重启 Termux：在 Termux 中执行 `exit` 后重新打开

### 问题 6：代码片段模板关闭按钮无效

**原因：**
已修复，如果仍有问题请重新安装最新版本

**解决方案：**
确保使用最新版本 APK

### 问题 7：Termux 检测状态不更新

**原因：**
状态缓存未刷新

**解决方案：**
点击终端右上角的刷新按钮手动刷新

### 问题 8：输出文件路径错误

**原因：**
应用缓存目录权限问题

**解决方案：**
1. 确保应用有存储权限
2. 在系统设置中授予「Mede IDE」存储权限

---

## 已知限制

### 1. 交互式命令不支持
以下类型的命令无法在 Mede IDE 终端中正常使用：
- `vim`、`nano` 等编辑器（需要交互式界面）
- `top`、`htop` 等监控工具
- `ssh` 远程连接
- `python` 交互式 REPL

**替代方案：**
这些命令请在 Termux 应用中直接执行

### 2. 命令执行延迟
由于使用 Intent + 文件方式获取输出，每次命令执行会有 1-2 秒延迟

### 3. 输出截断
超长输出（超过 100KB）可能被截断

### 4. 后台执行限制
Android 8.0+ 对后台服务有限制，长时间命令可能被系统终止

### 5. Termux 版本要求
需要 Termux v0.118+ 版本，旧版本可能不支持 RunCommandService

### 6. SELinux 限制
部分设备（如华为、小米）的 SELinux 策略可能阻止跨应用调用

**解决方案：**
- 在 Termux 中手动执行命令
- 或在开发者选项中关闭 SELinux（需要 root）

---

## 配置检查清单

使用终端前，请确认以下配置已完成：

| 检查项 | 命令 | 预期结果 |
|--------|------|----------|
| Termux 已安装 | 打开 Termux | 正常启动 |
| 外部应用权限 | `cat ~/.termux/termux.properties` | 包含 `allow-external-apps=true` |
| Python 已安装 | `python3 --version` | 显示版本号 |
| 存储权限 | 系统设置 → 应用 → Mede IDE → 权限 | 存储权限已开启 |

---

## 获取帮助

如果遇到未列出的问题：
1. 查看 GitHub Issues：https://github.com/Evilgodxu/Mede-IDE/issues
2. 提交新 Issue，附上：
   - 设备型号和 Android 版本
   - Termux 版本
   - 错误截图或日志
   - 已尝试的解决方案

---

## 更新日志

### 2026-06-22
- 内置开发工具脚本
- 修复 Termux 检测问题
- 修复终端命令执行问题
- 添加外部应用权限配置提示
- 删除代码中的 emoji 图标