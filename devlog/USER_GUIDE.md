# Mede IDE 用户使用指南

## 目录
1. [环境配置](#环境配置)
2. [终端使用](#终端使用)
3. [开发工具](#开发工具)
4. [常见问题与解决方案](#常见问题与解决方案)
5. [已知限制](#已知限制)
6. [技术原理](#技术原理)
7. [配置检查清单](#配置检查清单)

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

**重要说明：**
- 此配置允许外部应用（如 Mede IDE）调用 Termux 的 RunCommandService
- 不配置此项，Termux 会拒绝所有外部应用的命令请求
- 配置后需要执行 `termux-reload-settings` 才能生效

#### 3. 应用存储权限配置
**必须授予存储权限，否则无法读取命令输出！**

**配置步骤：**
1. 打开系统设置
2. 进入「应用」→「Mede IDE」
3. 点击「权限」
4. 开启以下权限：
   - **存储**（Android 10 及以下）
   - **文件和媒体**（Android 11+）
   - **管理所有文件**（Android 11+，部分设备需要）

**验证权限：**
在终端执行 `ls /sdcard/`，如果能列出文件则权限正常

#### 4. Python 环境（可选，用于开发工具脚本）
```bash
pkg install python
```

验证：`python3 --version`

#### 5. 其他常用工具（可选）
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
- 存储权限未授予

**解决方案：**
1. 确保已安装 Python：`pkg install python`
2. 检查应用存储权限是否已授予
3. 查看终端输出中的错误信息

### 问题 5：命令执行超时（30秒无响应）

**原因：**
- 命令本身执行时间过长
- Termux RunCommandService 未正确响应
- 存储权限未授予，无法写入输出文件

**解决方案：**
1. 对于长时间命令，建议直接在 Termux 中执行
2. 检查 Termux 是否正常运行（打开 Termux 应用确认）
3. 确认已授予存储权限
4. 重启 Termux：在 Termux 中执行 `exit` 后重新打开

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

### 问题 9：显示「Termux 未响应」或「配置提示」

**原因：**
- 未配置 `allow-external-apps=true`
- 存储权限未授予
- Termux 未正常运行

**解决方案：**
按顺序检查：
1. 在 Termux 中执行配置命令（见问题 2）
2. 在系统设置中授予存储权限
3. 打开 Termux 应用确认其正常运行
4. 点击终端刷新按钮重新检测

### 问题 10：命令输出显示「执行失败」

**原因：**
- Termux RunCommandService 调用失败
- SELinux 策略阻止
- am 命令执行权限不足

**解决方案：**
1. 确认 Termux 配置正确
2. 确认存储权限已授予
3. 尝试在 Termux 中手动执行相同命令
4. 如果是 SELinux 问题，需要在 Termux 中执行命令

### 问题 11：公共存储目录无法访问

**原因：**
- Android 11+ 需要特殊权限
- 部分设备限制公共存储访问

**解决方案：**
1. Android 11+ 需要授予「管理所有文件」权限
2. 在系统设置中找到「Mede IDE」→「权限」→「所有文件访问权限」
3. 开启该权限

### 问题 12：Termux 配置文件不存在

**原因：**
首次使用 Termux，配置目录未创建

**解决方案：**
```bash
mkdir -p ~/.termux
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings
```

---

## 已知限制

### 1. 交互式命令不支持
以下类型的命令无法在 Mede IDE 终端中正常使用：
- `vim`、`nano` 等编辑器（需要交互式界面）
- `top`、`htop` 等监控工具
- `ssh` 远程连接
- `python` 交互式 REPL
- `gdb` 调试器

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

### 7. 公共存储依赖
命令输出依赖公共存储目录 `/sdcard/mede_terminal/`，如果该目录无法访问则终端无法工作

### 8. 多命令不支持
不支持在同一行执行多个命令（如 `ls; pwd`），请分开执行

### 9. 管道和重定向限制
部分复杂的管道命令可能无法正确执行，建议在 Termux 中执行

---

## 技术原理

### 终端执行流程

```
用户输入命令
    ↓
Mede IDE 调用 am startservice
    ↓
Termux RunCommandService 接收命令
    ↓
命令在 Termux shell 中执行
    ↓
输出写入 /sdcard/mede_terminal/output_xxx.txt
    ↓
Mede IDE 读取输出文件
    ↓
显示结果给用户
```

### 为什么需要公共存储目录

Android 安全机制规定：
- 每个应用的私有目录（如 `/data/data/com.termux/`）只能被该应用访问
- Termux 无法写入 Mede IDE 的私有目录
- Mede IDE 无法读取 Termux 的私有目录
- 公共存储目录 `/sdcard/` 是双方都可以访问的区域

### 为什么需要 allow-external-apps=true

Termux 默认禁止外部应用调用其服务，这是安全保护措施。配置此项后：
- Termux 会接受来自其他应用的 Intent 调用
- RunCommandService 可以执行外部应用发送的命令
- 配置文件位于 `~/.termux/termux.properties`

---

## 配置检查清单

使用终端前，请确认以下配置已完成：

| 检查项 | 命令/操作 | 预期结果 |
|--------|-----------|----------|
| Termux 已安装 | 打开 Termux | 正常启动，显示 shell |
| 外部应用权限 | `cat ~/.termux/termux.properties` | 包含 `allow-external-apps=true` |
| Python 已安装 | `python3 --version` | 显示版本号（如 Python 3.11.x） |
| 存储权限 | 系统设置 → 应用 → Mede IDE → 权限 | 存储/文件权限已开启 |
| 公共存储可访问 | `ls /sdcard/` | 能列出文件 |
| Termux 正常运行 | 打开 Termux 执行 `echo test` | 输出 `test` |

### 快速配置脚本

在 Termux 中一次性执行所有配置：
```bash
# 创建配置目录
mkdir -p ~/.termux

# 启用外部应用权限
echo 'allow-external-apps=true' >> ~/.termux/termux.properties

# 重载配置
termux-reload-settings

# 安装 Python
pkg install python -y

# 验证配置
echo "=== 配置验证 ==="
cat ~/.termux/termux.properties
python3 --version
ls /sdcard/

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
   - 是否已配置 `allow-external-apps=true`
   - 是否已授予存储权限

---

## 更新日志

### 2026-06-22
- 内置开发工具脚本
- 修复 Termux 检测问题
- 修复终端命令执行问题
- 改用公共存储目录 `/sdcard/mede_terminal/` 作为输出位置
- 添加外部应用权限配置提示
- 添加存储权限配置说明
- 删除代码中的 emoji 图标
- 完善用户使用指南