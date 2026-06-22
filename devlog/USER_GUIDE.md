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

**配置外部应用权限（必需）：**
```bash
mkdir -p ~/.termux
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings
```

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
4. 命令输出会在 2 秒后显示在终端窗口中

### 工作原理
- 使用 `am startservice` 调用 Termux 的 `RunCommandService`
- 命令输出写入公共存储 `/sdcard/mede_terminal/`
- 应用读取输出文件并显示结果

### 开发工具按钮
- Termux 已安装时，顶部栏显示「Build」图标
- 点击可启动内置的 Android 开发工具脚本
- 脚本以命令行模式运行，显示功能菜单

---

## 开发工具

### 内置脚本功能
应用内置 `android_dev_toolkit.sh`，提供以下功能：

1. **环境检测** - 检测 Java、Gradle、wget 等工具是否安装
2. **列出项目** - 列出所有已创建的 Android 项目
3. **项目生成** - 创建新的 Android 项目模板（Java、Kotlin、Lua）
4. **编译构建** - 执行 Gradle 构建命令（Debug/Release）
5. **混淆保护** - 代码混淆和签名配置
6. **一键配置** - 配置 gradle.properties

### 脚本路径
脚本会自动复制到以下路径（按优先级）：
- `/sdcard/Download/mede_ide/android_dev_toolkit.sh`
- `/sdcard/mede_ide/android_dev_toolkit.sh`

### 使用方式

#### 启动方式
1. 点击终端顶部栏的「Build」图标按钮
2. 脚本会显示功能菜单
3. 在终端输入命令调用对应功能

#### 命令列表
```bash
# 显示菜单
bash android_dev_toolkit.sh menu

# 环境检测
bash android_dev_toolkit.sh check_env

# 列出项目
bash android_dev_toolkit.sh list_projects

# 创建项目
bash android_dev_toolkit.sh create_project <应用名称> <包名> <模板类型>

# 编译 Debug
bash android_dev_toolkit.sh build_debug <项目名称>

# 编译 Release
bash android_dev_toolkit.sh build_release <项目名称>

# 一键配置
bash android_dev_toolkit.sh quick_setup

# 混淆保护配置
bash android_dev_toolkit.sh setup_protection <项目路径> <项目名称> <包名>
```

#### 模板类型
- `java` - 纯 Java 项目
- `kotlin` - Java + Kotlin 混合项目
- `lua` - Java + Kotlin + Lua 项目

#### 示例
```bash
# 创建 Java 项目
bash android_dev_toolkit.sh create_project MyApp com.example.myapp java

# 创建 Kotlin 项目
bash android_dev_toolkit.sh create_project MyApp com.example.myapp kotlin

# 编译项目
bash android_dev_toolkit.sh build_debug MyApp
```

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
- 未配置 `allow-external-apps=true`
- Termux 服务未启动

**解决方案：**
1. 在 Termux 中执行配置命令：
   ```bash
   mkdir -p ~/.termux
   echo 'allow-external-apps=true' >> ~/.termux/termux.properties
   termux-reload-settings
   ```
2. 重启 Termux 和 Mede IDE

### 问题 3：开发工具菜单显示后，输入数字无反应

**原因：**
脚本已改为命令行模式，不再支持交互式选择

**解决方案：**
需要在终端输入完整的命令，例如：
- 执行环境检测：`bash android_dev_toolkit.sh check_env`
- 创建项目：`bash android_dev_toolkit.sh create_project MyApp com.example.myapp java`
- 编译项目：`bash android_dev_toolkit.sh build_debug MyApp`

### 问题 4：显示「命令执行失败」

**原因：**
- 命令本身执行出错
- 开发工具未安装

**解决方案：**
1. 查看错误信息中的具体内容
2. 在 Termux 中手动执行相同命令验证
3. 确认已安装必要的工具（如 `pkg install openjdk-17 gradle`）

### 问题 5：开发工具按钮点击无反应

**原因：**
- 脚本复制失败
- Termux 未配置外部应用权限

**解决方案：**
1. 确认 Termux 已安装并配置 `allow-external-apps=true`
2. 检查终端输出中的错误信息
3. 手动执行：`bash /sdcard/Download/mede_ide/android_dev_toolkit.sh`

### 问题 6：Termux 检测状态不更新

**原因：**
状态缓存未刷新

**解决方案：**
点击终端右上角的刷新按钮手动刷新

### 问题 7：脚本显示「需要 bash 环境」或「语法错误」

**原因：**
脚本之前使用了 bash 特有语法

**解决方案：**
更新到最新版本的应用即可，脚本已修复为兼容所有 shell 环境

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

### 2. 命令执行延迟
命令输出会在 2 秒后显示，这是为了等待 Termux 完成执行并写入输出文件

### 3. 长时间命令
执行时间超过 60 秒的命令可能被系统终止

### 4. Termux 版本要求
需要 Termux v0.118+ 版本

### 5. SELinux 限制
部分设备（如华为、小米）的 SELinux 策略可能阻止访问 Termux 目录

### 6. 脚本为命令行模式
开发工具脚本不支持交互式菜单选择，需要输入完整命令

---

## 配置检查清单

使用终端前，请确认以下配置已完成：

| 检查项 | 命令/操作 | 预期结果 |
|--------|-----------|----------|
| Termux 已安装 | 打开 Termux | 正常启动，显示 shell |
| 外部应用权限已配置 | `cat ~/.termux/termux.properties` | 包含 `allow-external-apps=true` |
| 开发工具已安装 | `java -version` | 显示版本号 |
| Termux 正常运行 | 打开 Termux 执行 `echo test` | 输出 `test` |

### 快速配置脚本

在 Termux 中一次性完成所有配置：
```bash
# 配置外部应用权限
mkdir -p ~/.termux
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings

# 安装开发工具
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

#### 终端功能
- 使用 `am startservice` 调用 Termux `RunCommandService`
- 命令输出写入公共存储 `/sdcard/mede_terminal/`
- 需要配置 `allow-external-apps=true` 才能使用

#### 开发工具
- 脚本改为命令行模式，不再支持交互式选择
- 美化界面，添加彩色边框和分隔线
- 使用 ✓✗ 符号显示检测结果
- 支持直接调用功能：`check_env`、`create_project`、`build_debug` 等

#### 其他
- 修复 bash 脚本兼容性问题（替换 `[[ ]]` 语法为标准 sh）
- 删除代码中的 emoji 图标
- 简化用户使用指南