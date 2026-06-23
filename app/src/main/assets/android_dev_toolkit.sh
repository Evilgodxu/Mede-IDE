#!/data/data/com.termux/files/usr/bin/bash
# Mede-IDE Android 开发工具包
# 用法: aid <命令> [参数]
# 短别名: aid (Android IDE)

# 颜色
R='\033[0m'; B='\033[1m'; RED='\033[31m'; GRN='\033[32m'
YEL='\033[33m'; BLU='\033[34m'; CYN='\033[36m'; GRY='\033[90m'

# 路径
ROOT="/data/data/com.termux/files/home"
IDE_DIR="/sdcard/Download/mede_ide"
PROJECTS_DIR="$IDE_DIR/projects"
KEYSTORE_DIR="$IDE_DIR/keystore"
LOG_DIR="$IDE_DIR/logs"

# 工具函数
hr()   { echo -e "${GRY}─────────────────────────────────────────${R}"; }
ttl()  { echo -e "\n${CYN}${B}▶ $1${R}"; }
ok()   { echo -e "${GRN}✓ $1${R}"; }
err()  { echo -e "${RED}✗ $1${R}"; }
warn() { echo -e "${YEL}! $1${R}"; }
ask()  { echo -ne "${BLU}? $1${R} "; }

# 初始化目录
init_dirs() {
    mkdir -p "$IDE_DIR" "$PROJECTS_DIR" "$KEYSTORE_DIR" "$LOG_DIR"
}

# 显示菜单
show_help() {
    init_dirs
    clear 2>/dev/null
    echo -e "${CYN}${B}"
    echo "  ╔═══════════════════════════════════════════╗"
    echo "  ║       Mede-IDE Android 开发工具包         ║"
    echo "  ║     短别名: aid  (Android IDE)            ║"
    echo "  ╚═══════════════════════════════════════════╝"
    echo -e "${R}"
    hr
    echo -e "${B}📦 项目管理${R}"
    echo "  aid new <名> [包名]    创建 Android 项目"
    echo "  aid list                列出所有项目"
    echo "  aid open <名>           进入项目目录"
    echo "  aid del <名>            删除项目"
    echo "  aid info <名>           查看项目信息"
    hr
    echo -e "${B}🔨 编译构建${R}"
    echo "  aid build [名]          编译 Debug APK"
    echo "  aid release [名]        编译 Release APK"
    echo "  aid install [名]        安装 APK 到手机"
    echo "  aid clean [名]          清理构建文件"
    hr
    echo -e "${B}🛠  工具命令${R}"
    echo "  aid env                 检测开发环境"
    echo "  aid sdk                 管理 Android SDK"
    echo "  aid avd                 管理模拟器"
    echo "  aid device              查看连接的设备"
    echo "  aid log [标签]          查看日志"
    hr
    echo -e "${B}🔧 系统工具${R}"
    echo "  aid pkg <名>            安装系统应用"
    echo "  aid pkg-un <包>         卸载应用"
    echo "  aid pkg-list            列出已安装应用"
    echo "  aid screen              截屏"
    echo "  aid path <文件>         查找文件路径"
    hr
    echo -e "${B}💾 备份签名${R}"
    echo "  aid ks                  生成签名密钥"
    echo "  aid ks-list             列出签名密钥"
    hr
    echo -e "${B}📚 其他${R}"
    echo "  aid setup               一键配置环境"
    echo "  aid alias               设置命令别名"
    echo "  aid ver                 查看版本"
    echo "  aid help                显示此帮助"
    hr
}

# 环境检测
cmd_env() {
    ttl "环境检测"
    local ok_count=0; local total=0
    local tools=("java:Java" "gradle:Gradle" "aapt:AAPT" "apksigner:APK签名" "adb:ADB" "zipalign:对齐工具")
    for tool in "${tools[@]}"; do
        local cmd="${tool%%:*}"; local name="${tool##*:}"
        ((total++))
        if command -v "$cmd" >/dev/null 2>&1; then
            ok "$name: $(command -v $cmd)"
            ((ok_count++))
        else
            warn "$name: 未安装"
        fi
    done
    hr
    echo -e "结果: ${GRN}${ok_count}${R}/${total} 可用"
    if [ $ok_count -lt $total ]; then
        echo -e "${YEL}提示: 运行 aid setup 可自动配置环境${R}"
    fi
}

# 一键配置
cmd_setup() {
    ttl "一键配置开发环境"
    init_dirs
    pkg update -y 2>/dev/null
    for p in openjdk-17 gradle aapt apksigner zipalign android-tools; do
        if ! command -v "${p%-*}" >/dev/null 2>&1; then
            echo "安装 $p ..."
            pkg install -y "$p" 2>/dev/null
        fi
    done
    ok "环境配置完成"
    cmd_alias
}

# 设置别名
cmd_alias() {
    local shell_rc="$HOME/.bashrc"
    [ -f "$HOME/.zshrc" ] && shell_rc="$HOME/.zshrc"
    if ! grep -q "alias aid=" "$shell_rc" 2>/dev/null; then
        echo "alias aid='bash $IDE_DIR/android_dev_toolkit.sh'" >> "$shell_rc"
        ok "已添加别名到 $shell_rc"
    else
        ok "别名已存在"
    fi
}

# 列出项目
cmd_list() {
    init_dirs
    ttl "项目列表 (位于 $PROJECTS_DIR)"
    if [ -z "$(ls -A $PROJECTS_DIR 2>/dev/null)" ]; then
        warn "暂无项目，使用 aid new <名> 创建"
        return
    fi
    printf "${B}%-20s %-30s %s${R}\n" "项目名" "包名" "状态"
    hr
    for proj in "$PROJECTS_DIR"/*/; do
        [ ! -d "$proj" ] && continue
        local name=$(basename "$proj")
        local pkg=$(grep -oP 'applicationId\s*=\s*"\K[^"]+' "$proj/app/build.gradle.kts" 2>/dev/null || \
                    grep -oP 'applicationId\s*=\s*"\K[^"]+' "$proj/app/build.gradle" 2>/dev/null || echo "-")
        printf "%-20s %-30s %s\n" "$name" "$pkg" "就绪"
    done
}

# 创建项目
cmd_new() {
    local name="$1"
    local pkg="${2:-com.example.$name}"
    if [ -z "$name" ]; then
        ask "请输入项目名:"; read name
    fi
    [ -z "$name" ] && { err "项目名不能为空"; return 1; }
    local proj_dir="$PROJECTS_DIR/$name"
    if [ -d "$proj_dir" ]; then
        err "项目已存在: $name"; return 1
    fi
    ttl "创建项目: $name ($pkg)"
    mkdir -p "$proj_dir/app/src/main/java/$(echo $pkg | tr '.' '/')"
    mkdir -p "$proj_dir/app/src/main/res/values" "$proj_dir/app/src/main/res/layout"
    mkdir -p "$proj_dir/gradle/wrapper"

    # settings.gradle.kts
    cat > "$proj_dir/settings.gradle.kts" <<EOF
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = "$name"
include(":app")
EOF

    # build.gradle.kts (root)
    cat > "$proj_dir/build.gradle.kts" <<EOF
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}
EOF

    # app/build.gradle.kts
    cat > "$proj_dir/app/build.gradle.kts" <<EOF
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "$pkg"
    compileSdk = 34
    defaultConfig {
        applicationId = "$pkg"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
EOF

    # AndroidManifest.xml
    cat > "$proj_dir/app/src/main/AndroidManifest.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="$name"
        android:theme="@android:style/Theme.Material.Light">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

    # MainActivity.kt
    cat > "$proj_dir/app/src/main/java/$(echo $pkg | tr '.' '/')/MainActivity.kt" <<EOF
package $pkg
import android.app.Activity
import android.os.Bundle
import android.widget.TextView
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Hello from $name" })
    }
}
EOF

    ok "项目创建成功: $proj_dir"
    echo -e "${YEL}提示: cd $proj_dir && aid build 编译项目${R}"
}

# 删除项目
cmd_del() {
    local name="$1"
    [ -z "$name" ] && { err "请指定项目名"; return 1; }
    local proj_dir="$PROJECTS_DIR/$name"
    [ ! -d "$proj_dir" ] && { err "项目不存在: $name"; return 1; }
    ask "确认删除项目 $name ? (y/N)"; read ans
    [ "$ans" = "y" ] || [ "$ans" = "Y" ] || { warn "已取消"; return; }
    rm -rf "$proj_dir"
    ok "已删除项目: $name"
}

# 进入项目
cmd_open() {
    local name="$1"
    [ -z "$name" ] && { err "请指定项目名"; return 1; }
    local proj_dir="$PROJECTS_DIR/$name"
    [ ! -d "$proj_dir" ] && { err "项目不存在: $name"; return 1; }
    cd "$proj_dir"
    ok "已进入: $proj_dir"
    pwd
}

# 项目信息
cmd_info() {
    local name="$1"
    [ -z "$name" ] && { err "请指定项目名"; return 1; }
    local proj_dir="$PROJECTS_DIR/$name"
    [ ! -d "$proj_dir" ] && { err "项目不存在: $name"; return 1; }
    ttl "项目信息: $name"
    local pkg=$(grep -oP 'applicationId\s*=\s*"\K[^"]+' "$proj_dir/app/build.gradle.kts" 2>/dev/null || echo "-")
    local ver=$(grep -oP 'versionName\s*=\s*"\K[^"]+' "$proj_dir/app/build.gradle.kts" 2>/dev/null || echo "-")
    local size=$(du -sh "$proj_dir" 2>/dev/null | cut -f1)
    echo -e "  ${B}名称:${R} $name"
    echo -e "  ${B}包名:${R} $pkg"
    echo -e "  ${B}版本:${R} $ver"
    echo -e "  ${B}大小:${R} $size"
    echo -e "  ${B}路径:${R} $proj_dir"
}

# 编译项目
cmd_build() {
    local name="${1:-$(basename $(pwd))}"
    local proj_dir="$PROJECTS_DIR/$name"
    if [ ! -d "$proj_dir" ]; then proj_dir="$(pwd)"; fi
    [ ! -f "$proj_dir/app/build.gradle.kts" ] && [ ! -f "$proj_dir/app/build.gradle" ] && \
        { err "未找到 build.gradle: $proj_dir"; return 1; }
    ttl "编译 Debug: $name"
    cd "$proj_dir"
    gradle assembleDebug 2>&1 | tail -20
    local apk="$proj_dir/app/build/outputs/apk/debug/app-debug.apk"
    [ -f "$apk" ] && ok "编译成功: $apk" || err "编译失败"
}

# 编译 Release
cmd_release() {
    local name="${1:-$(basename $(pwd))}"
    local proj_dir="$PROJECTS_DIR/$name"
    [ ! -d "$proj_dir" ] && proj_dir="$(pwd)"
    ttl "编译 Release: $name"
    cd "$proj_dir"
    gradle assembleRelease 2>&1 | tail -20
    local apk="$proj_dir/app/build/outputs/apk/release/app-release.apk"
    [ -f "$apk" ] && ok "编译成功: $apk" || err "编译失败"
}

# 安装 APK
cmd_install() {
    local name="${1:-$(basename $(pwd))}"
    local proj_dir="$PROJECTS_DIR/$name"
    [ ! -d "$proj_dir" ] && proj_dir="$(pwd)"
    local apk="$proj_dir/app/build/outputs/apk/debug/app-debug.apk"
    [ -f "$proj_dir/app/build/outputs/apk/release/app-release.apk" ] && \
        apk="$proj_dir/app/build/outputs/apk/release/app-release.apk"
    [ ! -f "$apk" ] && { err "未找到 APK，请先编译"; return 1; }
    ttl "安装: $apk"
    adb install -r "$apk"
}

# 清理
cmd_clean() {
    local name="${1:-$(basename $(pwd))}"
    local proj_dir="$PROJECTS_DIR/$name"
    [ ! -d "$proj_dir" ] && proj_dir="$(pwd)"
    cd "$proj_dir"
    gradle clean 2>&1 | tail -5
    ok "已清理"
}

# Android SDK 管理
cmd_sdk() {
    local action="${1:-list}"
    case "$action" in
        list) sdkmanager --list_installed 2>/dev/null || warn "sdkmanager 未安装" ;;
        install) sdkmanager "${@:2}" ;;
        *) echo "用法: aid sdk [list|install <包>]" ;;
    esac
}

# 模拟器
cmd_avd() {
    local action="${1:-list}"
    case "$action" in
        list) avdmanager list avd 2>/dev/null || warn "avdmanager 未安装" ;;
        create) avdmanager create avd -n "${2:-test}" -k "${3:-system-images;android-30;google_apis;x86_64}" ;;
        start) emulator -avd "${2:-test}" ;;
        *) echo "用法: aid avd [list|create|start]" ;;
    esac
}

# 设备列表
cmd_device() {
    ttl "已连接设备"
    adb devices -l 2>/dev/null || warn "adb 未安装"
}

# 日志
cmd_log() {
    local tag="${1:-*}"
    ttl "日志 (Ctrl+C 退出)"
    adb logcat -s "$tag" 2>/dev/null || warn "adb 未安装"
}

# 安装系统应用
cmd_pkg() {
    local name="$1"
    [ -z "$name" ] && { echo "用法: aid pkg <包名>"; return 1; }
    adb install "$name" 2>/dev/null && ok "已安装" || err "安装失败"
}

# 卸载应用
cmd_pkg_un() {
    local pkg="$1"
    [ -z "$pkg" ] && { echo "用法: aid pkg-un <包名>"; return 1; }
    ask "确认卸载 $pkg ? (y/N)"; read ans
    [ "$ans" = "y" ] || [ "$ans" = "Y" ] || return
    adb uninstall "$pkg" && ok "已卸载" || err "卸载失败"
}

# 已安装应用
cmd_pkg_list() {
    ttl "已安装应用"
    adb shell pm list packages -3 2>/dev/null | sed 's/package://'
}

# 截屏
cmd_screen() {
    local file="${1:-screen_$(date +%Y%m%d_%H%M%S).png}"
    adb exec-out screencap -p > "$file" && ok "已保存: $file" || err "截屏失败"
}

# 查找文件
cmd_path() {
    local name="$1"
    [ -z "$name" ] && { echo "用法: aid path <文件名>"; return 1; }
    ttl "查找: $name"
    find "$PROJECTS_DIR" -name "$name" 2>/dev/null
}

# 签名密钥
cmd_ks() {
    local name="${1:-mykey}"
    local ks_file="$KEYSTORE_DIR/${name}.jks"
    if [ -f "$ks_file" ]; then
        warn "密钥已存在: $ks_file"; return 1
    fi
    ask "密钥密码 (默认 123456):"; read -s pass
    [ -z "$pass" ] && pass="123456"
    keytool -genkey -v -keystore "$ks_file" -alias "$name" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$pass" -keypass "$pass" \
        -dname "CN=Mede,O=Android,C=CN"
    ok "密钥已生成: $ks_file"
}

# 列出密钥
cmd_ks_list() {
    ttl "签名密钥"
    ls -lh "$KEYSTORE_DIR"/*.jks 2>/dev/null || warn "暂无密钥"
}

# 版本
cmd_ver() {
    echo -e "${CYN}Mede-IDE Toolkit v1.0${R}"
    echo -e "${GRY}支持 Android 原生开发${R}"
}

# 主入口
main() {
    init_dirs
    local cmd="${1:-help}"
    shift 2>/dev/null
    case "$cmd" in
        h|help|-h|--help|"") show_help ;;
        env) cmd_env ;;
        setup) cmd_setup ;;
        alias) cmd_alias ;;
        list|ls) cmd_list ;;
        new|create) cmd_new "$@" ;;
        del|rm) cmd_del "$@" ;;
        open|cd) cmd_open "$@" ;;
        info) cmd_info "$@" ;;
        build) cmd_build "$@" ;;
        release) cmd_release "$@" ;;
        install) cmd_install "$@" ;;
        clean) cmd_clean "$@" ;;
        sdk) cmd_sdk "$@" ;;
        avd|emulator) cmd_avd "$@" ;;
        device|dev) cmd_device ;;
        log|logcat) cmd_log "$@" ;;
        pkg) cmd_pkg "$@" ;;
        pkg-un|pkg-rm) cmd_pkg_un "$@" ;;
        pkg-list) cmd_pkg_list ;;
        screen|screenshot) cmd_screen "$@" ;;
        path|find) cmd_path "$@" ;;
        ks|keystore) cmd_ks "$@" ;;
        ks-list) cmd_ks_list ;;
        ver|version) cmd_ver ;;
        *) err "未知命令: $cmd"; echo "运行 aid help 查看帮助" ;;
    esac
}

main "$@"
