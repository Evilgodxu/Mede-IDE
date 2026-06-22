#!/data/data/com.termux/files/usr/bin/bash

BLACK='\033[30m'
RED='\033[31m'
GREEN='\033[32m'
YELLOW='\033[33m'
BLUE='\033[34m'
MAGENTA='\033[35m'
CYAN='\033[36m'
WHITE='\033[37m'
BOLD='\033[1m'
UNDERLINE='\033[4m'
RESET='\033[0m'
CLEAR='\033[2J\033[H'

BG_BLACK='\033[40m'
BG_RED='\033[41m'
BG_GREEN='\033[42m'
BG_YELLOW='\033[43m'
BG_BLUE='\033[44m'
BG_MAGENTA='\033[45m'
BG_CYAN='\033[46m'
BG_WHITE='\033[47m'

print_line() {
    local char="$1"
    local color="$2"
    local len=60
    echo -e "${color}$(printf "%${len}s" | tr ' ' "$char")${RESET}"
}

print_title() {
    print_line '=' "$CYAN"
    echo -e "${CYAN}${BOLD}                      $1${RESET}"
    print_line '=' "$CYAN"
}

print_subtitle() {
    echo -e "\n${BLUE}${BOLD}  --- $1 ---${RESET}"
}

print_box() {
    local content="$1"
    local color="$2"
    print_line '-' "$color"
    echo -e "${color}${content}${RESET}"
    print_line '-' "$color"
}

info() { echo -e "${CYAN}${BOLD}[*]${RESET} ${CYAN}$1${RESET}"; }
ok() { echo -e "${GREEN}${BOLD}[+]${RESET} ${GREEN}$1${RESET}"; }
warn() { echo -e "${YELLOW}${BOLD}[!]${RESET} ${YELLOW}$1${RESET}"; }
err() { echo -e "${RED}${BOLD}[-]${RESET} ${RED}$1${RESET}"; }

TERMUX_HOME="$HOME"
ANDROID_SDK="$TERMUX_HOME/android-sdk"
PROJECTS_BASE="/storage/emulated/0/Termux/Android"
PROJECTS_LINK="$TERMUX_HOME/projects"
GRADLE_PROPS="$TERMUX_HOME/.gradle/gradle.properties"
AAPT2_PATH="/data/data/com.termux/files/usr/bin/aapt2"
GRADLE_CMD="gradle"

check_env() {
    print_title "环境检测"
    
    echo -e "\n${WHITE}${BOLD}  正在检测开发环境...${RESET}\n"
    
    local checks=(
        "Java (openjdk-17)" "java -version 2>&1 | grep -q '17\\.'" "已安装" "需安装"
        "Gradle" "which gradle > /dev/null 2>&1" "已安装" "需安装"
        "wget" "which wget > /dev/null 2>&1" "已安装" "需安装"
        "unzip" "which unzip > /dev/null 2>&1" "已安装" "需安装"
        "aapt2" "test -f $AAPT2_PATH" "已安装" "需安装"
        "git" "which git > /dev/null 2>&1" "已安装" "需安装"
    )
    
    local total=${#checks[@]}
    local count=$((total / 4))
    local pass=0
    
    for ((i=0; i<total; i+=4)); do
        local name="${checks[i]}"
        local check_cmd="${checks[i+1]}"
        local ok_msg="${checks[i+2]}"
        local err_msg="${checks[i+3]}"
        
        printf "  %-25s" "$name"
        if eval "$check_cmd"; then
            echo -e "${GREEN}${BOLD}[OK] $ok_msg${RESET}"
            pass=$((pass + 1))
        else
            echo -e "${RED}${BOLD}[FAIL] $err_msg${RESET}"
        fi
    done
    
    echo -e "\n  ${WHITE}${BOLD}检测完成: ${GREEN}${BOLD}$pass/${count}${RESET} ${WHITE}项通过${RESET}"
    
    if [ $pass -ne $count ]; then
        warn "部分工具未安装，请执行: pkg install openjdk-17 gradle wget unzip aapt2 git"
    else
        ok "开发环境已就绪！"
    fi
}

list_projects() {
    if [ ! -d "$PROJECTS_BASE" ]; then
        return
    fi
    local projects=()
    for name in "$PROJECTS_BASE"/*; do
        [ -d "$name" ] || continue
        [ -f "$name/app/build.gradle" ] || continue
        projects+=("$(basename "$name")")
    done
    printf "%s\n" "${projects[@]}" | sort
}

create_project() {
    print_title "生成项目模板"
    
    local app_name="${1:-MyApp}"
    local package_name="${2:-com.example.myapp}"
    local template="${3:-java}"
    
    echo -e "\n${WHITE}${BOLD}  项目信息:${RESET}"
    echo -e "    应用名称: ${CYAN}$app_name${RESET}"
    echo -e "    包名: ${CYAN}$package_name${RESET}"
    echo -e "    模板类型: ${CYAN}$template${RESET}"
    
    local project_path="$PROJECTS_BASE/$app_name"
    local package_path="${package_name//./\/}"
    
    if [ -d "$project_path" ]; then
        err "项目已存在: $project_path"
        return 1
    fi
    
    info "正在创建项目目录..."
    mkdir -p "$project_path/app/src/main/java/$package_path"
    mkdir -p "$project_path/app/src/main/res/values"
    mkdir -p "$project_path/app/src/main/res/drawable"
    mkdir -p "$project_path/app/src/main/AndroidManifest.xml"
    mkdir -p "$project_path/gradle/wrapper"
    
    info "正在生成项目文件..."
    
    local settings="rootProject.name = '$app_name'
include ':app'"
    
    local root_gradle=''
    if echo "$template" | grep -qE 'kotlin|lua'; then
        root_gradle='buildscript {
    repositories {
        maven { url "https://maven.aliyun.com/repository/google" }
        maven { url "https://maven.aliyun.com/repository/public" }
        maven { url "https://maven.aliyun.com/repository/gradle-plugin" }
        google()
        mavenCentral()
    }
    dependencies {
        classpath "com.android.tools.build:gradle:8.3.0"
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22"
    }
}
allprojects {
    repositories {
        maven { url "https://maven.aliyun.com/repository/google" }
        maven { url "https://maven.aliyun.com/repository/public" }
        maven { url "https://maven.aliyun.com/repository/gradle-plugin" }
        google()
        mavenCentral()
    }
}'
    else
        root_gradle='buildscript {
    repositories {
        maven { url "https://maven.aliyun.com/repository/google" }
        maven { url "https://maven.aliyun.com/repository/public" }
        google()
        mavenCentral()
    }
    dependencies {
        classpath "com.android.tools.build:gradle:8.3.0"
    }
}
allprojects {
    repositories {
        maven { url "https://maven.aliyun.com/repository/google" }
        maven { url "https://maven.aliyun.com/repository/public" }
        google()
        mavenCentral()
    }
}'
    fi
    
    local plugins='apply plugin: "com.android.application"
'
    local deps=''
    if echo "$template" | grep -qE 'kotlin|lua'; then
        plugins+='apply plugin: "org.jetbrains.kotlin.android"
'
        deps='    implementation "org.jetbrains.kotlin:kotlin-stdlib:1.9.22"
'
    fi
    if echo "$template" | grep -q 'lua'; then
        deps+='    implementation "org.luaj:luaj-jse:3.0.1"
'
    fi
    
    local kotlin_opts=''
    if echo "$template" | grep -qE 'kotlin|lua'; then
        kotlin_opts="
    kotlinOptions {
        jvmTarget = '1.8'
    }
"
    fi
    
    local app_gradle="$plugins"
    app_gradle+='android {
    compileSdk 34
    namespace "'$package_name'"

    defaultConfig {
        applicationId "'$package_name'"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
        }
    }
'$kotlin_opts'
}

dependencies {
    implementation "androidx.appcompat:appcompat:1.6.1"
    implementation "com.google.android.material:material:1.11.0"
    implementation "androidx.constraintlayout:constraintlayout:2.1.4"
'$deps'}
'
    
    local manifest='<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.'$app_name'">
        <activity
            android:name=".'$package_path'.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
'
    
    local main_activity="package $package_name;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        TextView textView = findViewById(R.id.textView);
        textView.setText(\"$app_name - Android Development\");
    }
}
"
    
    local activity_main='<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".'$package_path'.MainActivity">

    <TextView
        android:id="@+id/textView"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="24sp"
        android:textStyle="bold"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
'
    
    local strings='<resources>
    <string name="app_name">'$app_name'</string>
</resources>
'
    
    local themes='<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.'$app_name'" parent="Theme.MaterialComponents.DayNight.DarkActionBar">
        <item name="colorPrimary">@color/purple_500</item>
        <item name="colorPrimaryVariant">@color/purple_700</item>
        <item name="colorOnPrimary">@color/white</item>
        <item name="colorSecondary">@color/teal_200</item>
        <item name="colorSecondaryVariant">@color/teal_700</item>
        <item name="colorOnSecondary">@color/black</item>
        <item name="android:statusBarColor">?attr/colorPrimaryVariant</item>
    </style>
</resources>
'
    
    local colors='<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple_500">#7B1FA2</color>
    <color name="purple_700">#5E1B89</color>
    <color name="teal_200">#80CBC4</color>
    <color name="teal_700">#00897B</color>
    <color name="white">#FFFFFF</color>
    <color name="black">#000000</color>
</resources>
'
    
    local wrapper_props="distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://mirrors.aliyun.com/gradle/distributions/gradle-8.7-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists"
    
    write_file "$project_path/settings.gradle" "$settings"
    write_file "$project_path/build.gradle" "$root_gradle"
    write_file "$project_path/app/build.gradle" "$app_gradle"
    write_file "$project_path/app/src/main/AndroidManifest.xml" "$manifest"
    write_file "$project_path/app/src/main/java/$package_path/MainActivity.java" "$main_activity"
    write_file "$project_path/app/src/main/res/layout/activity_main.xml" "$activity_main"
    write_file "$project_path/app/src/main/res/values/strings.xml" "$strings"
    write_file "$project_path/app/src/main/res/values/themes.xml" "$themes"
    write_file "$project_path/app/src/main/res/values/colors.xml" "$colors"
    write_file "$project_path/gradle/wrapper/gradle-wrapper.properties" "$wrapper_props"
    
    cp /data/data/com.termux/files/usr/share/java/gradle/wrapper/gradle-wrapper.jar "$project_path/gradle/wrapper/" 2>/dev/null || true
    
    ok "项目创建完成！"
    echo -e "\n${WHITE}${BOLD}  项目位置: ${CYAN}$project_path${RESET}"
    echo -e "  包名: ${CYAN}$package_name${RESET}"
    echo -e "  应用名称: ${CYAN}$app_name${RESET}"
    
    mkdir -p "$PROJECTS_LINK"
    ln -s "$project_path" "$PROJECTS_LINK/$app_name" 2>/dev/null || true
    
    return 0
}

switch_project() {
    print_title "切换项目"
    
    local projects=($(list_projects))
    if [ ${#projects[@]} -eq 0 ]; then
        err "没有找到任何项目"
        return 1
    fi
    
    echo -e "\n${WHITE}${BOLD}  已有项目:${RESET}\n"
    
    for i in "${!projects[@]}"; do
        echo -e "    ${CYAN}${BOLD}$((i+1)).${RESET} ${WHITE}${projects[i]}${RESET}"
    done
    
    return 0
}

build_project() {
    local project_name="$1"
    local build_type="${2:-debug}"
    
    print_title "编译项目（$build_type）"
    
    local project_path="$PROJECTS_BASE/$project_name"
    
    if [ ! -d "$project_path" ]; then
        err "项目不存在: $project_path"
        return 1
    fi
    
    info "项目位置: $project_path"
    info "编译类型: $build_type"
    
    cd "$project_path" || return 1
    
    info "正在执行 Gradle 构建..."
    local cmd="$GRADLE_CMD assemble${build_type^} --no-daemon"
    
    echo -e "\n${WHITE}${BOLD}  执行命令: ${CYAN}$cmd${RESET}\n"
    
    $cmd
    
    local exit_code=$?
    
    if [ $exit_code -eq 0 ]; then
        ok "编译成功！"
        
        local apk_path="$project_path/app/build/outputs/apk/$build_type/app-$build_type.apk"
        if [ -f "$apk_path" ]; then
            echo -e "\n${WHITE}${BOLD}  APK 位置: ${GREEN}$apk_path${RESET}"
            
            local target_apk="/storage/emulated/0/${project_name}-${build_type}.apk"
            cp "$apk_path" "$target_apk" 2>/dev/null
            if [ $? -eq 0 ]; then
                ok "APK 已复制到: $target_apk"
            fi
        fi
    else
        err "编译失败（退出码: $exit_code）"
        return 1
    fi
    
    return 0
}

quick_setup() {
    print_title "一键配置"
    
    info "正在检查 gradle.properties..."
    local required=(
        "android.useAndroidX=true"
        "android.enableJetifier=true"
        "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8"
        "android.enableR8.fullMode=true"
    )
    
    if [ -f "$GRADLE_PROPS" ]; then
        local changed=0
        local existing=()
        while IFS= read -r line; do
            if echo "$line" | grep -qE '^[^#].*='; then
                existing+=("$line")
            fi
        done < "$GRADLE_PROPS"
        
        for req in "${required[@]}"; do
            local key="${req%%=*}"
            local found=0
            for line in "${existing[@]}"; do
                if echo "$line" | grep -q "^$key="; then
                    found=1
                    if [ "$line" != "$req" ]; then
                        changed=1
                    fi
                    break
                fi
            done
            if [ $found -eq 0 ]; then
                existing+=("$req")
                changed=1
            fi
        done
        
        if [ $changed -eq 1 ]; then
            > "$GRADLE_PROPS"
            for line in "${existing[@]}"; do
                echo "$line" >> "$GRADLE_PROPS"
            done
            ok "gradle.properties 已更新"
        else
            ok "gradle.properties 配置正确"
        fi
    else
        mkdir -p "$(dirname "$GRADLE_PROPS")"
        printf "%s\n" "${required[@]}" > "$GRADLE_PROPS"
        ok "gradle.properties 已创建"
    fi
    
    ok "一键配置完成！"
}

setup_protection() {
    local project_path="$1"
    local project_name="$2"
    local package_name="${3:-com.example.app}"
    
    print_title "混淆保护配置"
    
    if [ ! -d "$project_path" ]; then
        err "项目不存在: $project_path"
        return 1
    fi
    
    info "正在配置混淆规则..."
    
    local proguard_content=$(cat << 'PROGUARD_EOF'
# 基础保留
-keep class $package_name.MainActivity { *; }
-keepattributes *Annotation*

# LuaJ
-keep class org.luaj.** { *; }
-dontwarn org.luaj.**

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**

# 激进混淆选项
-optimizationpasses 5
-allowaccessmodification
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# 合并包名
-repackageclasses "x"
-flattenpackagehierarchy "x"

# 反调试
-keep class $package_name.guard.** { *; }

# 移除 Log 调用
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# 字符串加密
-optimizations \!code/simplification/arithmetic,\!code/simplification/cast,\!field/*,\!class/merging/*,string/encryption
PROGUARD_EOF
)
    proguard_content=$(echo "$proguard_content" | sed "s/\$package_name/$package_name/g")
    
    write_file "$project_path/app/proguard-rules.pro" "$proguard_content"
    ok "ProGuard 规则已写入"
    
    local gradle_props_project="$project_path/gradle.properties"
    write_file "$gradle_props_project" "android.enableR8.fullMode=true\n"
    ok "R8 全模式已启用"
    
    local guard_dir="$project_path/app/src/main/java/${package_name//./\/}/guard"
    mkdir -p "$guard_dir"
    
    local anti_debug=$(cat << 'JAVA_EOF'
package $package_name.guard;

import android.content.Context;
import android.os.Debug;
import android.os.Process;

public class AntiDebug {
    public static void check(Context ctx) {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            Process.killProcess(Process.myPid());
        }
        if (isEmulator()) {
            Process.killProcess(Process.myPid());
        }
    }

    private static boolean isEmulator() {
        return android.os.Build.FINGERPRINT.startsWith("generic")
            || android.os.Build.FINGERPRINT.startsWith("unknown")
            || android.os.Build.MODEL.contains("Emulator")
            || android.os.Build.MODEL.contains("Android SDK");
    }
}
JAVA_EOF
)
    anti_debug=$(echo "$anti_debug" | sed "s/\$package_name/$package_name/g")
    
    write_file "$guard_dir/AntiDebug.java" "$anti_debug"
    ok "反调试代码已添加"
    
    ok "混淆保护配置完成！"
}

show_usage() {
    print_title "Android 开发工具"
    
    echo -e "\n${WHITE}${BOLD}  使用方法:${RESET}\n"
    echo -e "    ${CYAN}${BOLD}bash /sdcard/Download/mede_ide/android_dev_toolkit.sh <命令> [参数]${RESET}\n"
    
    echo -e "${WHITE}${BOLD}  可用命令:${RESET}\n"
    
    echo -e "    ${GREEN}${BOLD}menu${RESET}              显示交互式菜单"
    echo -e "    ${GREEN}${BOLD}check_env${RESET}         检测开发环境"
    echo -e "    ${GREEN}${BOLD}list_projects${RESET}     列出所有项目"
    echo -e "    ${GREEN}${BOLD}create_project${RESET}    创建项目"
    echo -e "    ${GREEN}${BOLD}build_debug${RESET}       编译 Debug 版本"
    echo -e "    ${GREEN}${BOLD}build_release${RESET}     编译 Release 版本"
    echo -e "    ${GREEN}${BOLD}quick_setup${RESET}       一键配置环境"
    echo -e "    ${GREEN}${BOLD}setup_protection${RESET}  配置混淆保护"
    
    echo -e "\n${WHITE}${BOLD}  示例:${RESET}\n"
    echo -e "    ${CYAN}bash /sdcard/Download/mede_ide/android_dev_toolkit.sh check_env${RESET}"
    echo -e "    ${CYAN}bash /sdcard/Download/mede_ide/android_dev_toolkit.sh create_project MyApp com.example.myapp java${RESET}"
    echo -e "    ${CYAN}bash /sdcard/Download/mede_ide/android_dev_toolkit.sh build_debug MyApp${RESET}"
    
    print_line '=' "$CYAN"
}

show_menu() {
    print_title "Android 开发工具"
    
    echo -e "\n${WHITE}${BOLD}  可用命令:${RESET}\n"
    
    echo -e "    ${GREEN}${BOLD}check_env${RESET}       环境检测"
    echo -e "    ${GREEN}${BOLD}list_projects${RESET}   列出项目"
    echo -e "    ${GREEN}${BOLD}create_project${RESET}  创建项目"
    echo -e "    ${GREEN}${BOLD}build_debug${RESET}     编译 Debug"
    echo -e "    ${GREEN}${BOLD}build_release${RESET}   编译 Release"
    echo -e "    ${GREEN}${BOLD}quick_setup${RESET}     一键配置"
    echo -e "    ${GREEN}${BOLD}setup_protection${RESET} 混淆保护"
    
    echo -e "\n${YELLOW}${BOLD}  使用方式:${RESET}"
    echo -e "    ${WHITE}bash /sdcard/Download/mede_ide/android_dev_toolkit.sh <命令> [参数]${RESET}\n"
    
    echo -e "${YELLOW}${BOLD}  示例:${RESET}"
    echo -e "    ${WHITE}bash /sdcard/Download/mede_ide/android_dev_toolkit.sh check_env${RESET}"
    echo -e "    ${WHITE}bash /sdcard/Download/mede_ide/android_dev_toolkit.sh create_project MyApp com.example.myapp java${RESET}"
    echo -e "    ${WHITE}bash /sdcard/Download/mede_ide/android_dev_toolkit.sh build_debug MyApp${RESET}\n"
}

main() {
    if [ $# -eq 0 ]; then
        show_menu
        return 0
    fi
    
    case "$1" in
        menu)
            show_menu
            ;;
        check_env)
            check_env
            ;;
        list_projects)
            list_projects
            ;;
        create_project)
            create_project "$2" "$3" "$4"
            ;;
        switch_project)
            switch_project
            ;;
        quick_setup)
            quick_setup
            ;;
        build_debug)
            build_project "$2" "debug"
            ;;
        build_release)
            build_project "$2" "release"
            ;;
        setup_protection)
            setup_protection "$2" "$3" "$4"
            ;;
        help|--help|-h)
            show_usage
            ;;
        *)
            err "未知命令: $1"
            show_usage
            return 1
            ;;
    esac
    
    return 0
}

main "$@"