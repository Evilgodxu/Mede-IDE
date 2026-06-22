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

#### 2. 开发工具安装（可选）
如果需要使用开发工具脚本，在 Termux 中执行：
```bash
pkg install openjdk-17 gradle wget unzip aapt2
```

#### 3. 其他常用工具（可选）
```bash
pkg install git nodejs clang make vim
```

---

## 终端使用

### 基本操作
1. 点击侧边栏的「终端」图标打开终端面板
2. 在底部输入框输入命令
3. 点击发送按钮或按回车执行
4. 命令输出会实时显示在终端窗口中

### 工作原理
- 如果 Termux 已安装，应用会直接调用 Termux 的 bash shell 执行命令
- 如果 Termux 未安装，会使用系统 shell（功能有限）
- 输出实时读取，不需要等待或配置额外权限

### 开发工具按钮
- Termux 已安装时，顶部栏显示「Build」图标
- 点击可启动内置的 Android 开发工具脚本

---

## 开发工具

### 内置脚本功能
应用内置 `android_dev_toolkit.sh`，提供以下功能：

1. **环境检测** - 检测 Android SDK、Java、Gradle 等环境
2. **项目生成** - 创建新的 Android 项目模板（Java、Kotlin、Lua）
3. **编译构建** - 执行 Gradle 构建命令
4. **混淆保护** - 代码混淆和签名配置
5. **项目切换** - 快速切换工作项目

### 脚本路径
脚本会自动复制到以下路径（按优先级）：
- `/sdcard/Download/mede_ide/android_dev_toolkit.sh`
- `/sdcard/mede_ide/android_dev_toolkit.sh`

### 自定义脚本
用户可以将自己的 shell 脚本放入项目的 `app/src/main/assets/` 目录：
1. 将 `.sh` 文件放入 assets 目录
2. 重新编译应用
3. 在终端中执行：`bash <脚本路径>`

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

### 问题 2：命令执行无输出

**原因：**
- Termux bash 路径不存在
- 命令本身没有输出

**解决方案：**
1. 确认 Termux 已正确安装
2. 在 Termux 中执行 `ls /data/data/com.termux/files/usr/bin/bash` 确认 bash 存在
3. 尝试执行有输出的命令如 `ls` 或 `echo test`

### 问题 3：显示「命令执行失败」

**原因：**
- 命令本身执行出错
- Termux 环境变量未正确设置

**解决方案：**
1. 查看错误信息中的具体内容
2. 在 Termux 中手动执行相同命令验证
3. 确认已安装必要的工具（如 `pkg install openjdk-17 gradle`）

### 问题 4：开发工具按钮点击无反应

**原因：**
- 脚本复制失败
- Termux bash 不存在
- 脚本兼容性问题

**解决方案：**
1. 确认 Termux 已安装
2. 检查终端输出中的错误信息
3. 手动执行：`bash /sdcard/Download/mede_ide/android_dev_toolkit.sh`

### 问题 5：Termux 检测状态不更新

**原因：**
状态缓存未刷新

**解决方案：**
点击终端右上角的刷新按钮手动刷新

### 问题 6：脚本显示「需要 bash 环境」或「语法错误」

**原因：**
脚本之前使用了 bash 特有语法，已修复为标准 sh 语法

**解决方案：**
更新到最新版本的应用即可，脚本已兼容所有 shell 环境

---

## 已知限制

### 1. 交互式命令不支持
以下类型的命令无法在 Mede IDE 终端中正常使用：
- `vim`、`nano` 等编辑器（需要交互式界面）
- `top`、`htop` 等监控工具
- `ssh` 远程连接
- `gdb` 调试器

**替代方案：**
这些命令请在 Termux 应用中直接执行

### 2. 长时间命令
执行时间超过 60 秒的命令可能被系统终止

### 3. Termux 版本要求
需要 Termux v0.118+ 版本

### 4. SELinux 限制
部分设备（如华为、小米）的 SELinux 策略可能阻止访问 Termux 目录

---

## 配置检查清单

使用终端前，请确认以下配置已完成：

| 检查项 | 命令/操作 | 预期结果 |
|--------|-----------|----------|
| Termux 已安装 | 打开 Termux | 正常启动，显示 shell |
| Termux bash 存在 | `ls /data/data/com.termux/files/usr/bin/bash` | 文件存在 |
| 开发工具已安装 | `java -version` | 显示版本号 |
| Termux 正常运行 | 打开 Termux 执行 `echo test` | 输出 `test` |

### 快速配置脚本

在 Termux 中一次性安装所有开发工具：
```bash
pkg install openjdk-17 gradle wget unzip aapt2 -y

# 验证安装
echo "=== 验证安装 ==="
java -version
gradle --version
which aapt2

echo "=== 配置完成 ==="
```

---

## 获取帮助

如果遇到未列出的问题：
1. 查看 GitHub Issues：https://github.com/Evilgodxu/Mede-IDE/issues
2. 提交新 Issue，附上：
   - 设备型号和 Android 版本
   - Termux 版本（在 Termux 中执行 `pkg show-termux`）
   - 错误截图或日志
   - 已尝试的解决方案

---

## 更新日志

### 2026-06-22
- 实现内置终端，直接调用 Termux bash 执行命令
- 不再需要配置 `allow-external-apps=true`
- 替换 Python 脚本为 bash 脚本，不需要 Python 环境
- 修复 bash 脚本兼容性问题（替换 `[[ ]]` 语法为标准 sh）
- 修复脚本入口检查（移除 `$BASH_SOURCE` 依赖）
- 删除代码中的 emoji 图标
- 简化用户使用指南