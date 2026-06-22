#!/data/data/com.termux/files/usr/bin/bash
# Android 开发环境自动化管理脚本
# 支持：环境检测、项目生成、编译、混淆保护、项目切换

TERMUX_HOME="$HOME"
ANDROID_SDK="$TERMUX_HOME/android-sdk"
PROJECTS_BASE="/storage/emulated/0/Termux/Android"
PROJECTS_LINK="$TERMUX_HOME/projects"
GRADLE_PROPS="$TERMUX_HOME/.gradle/gradle.properties"
AAPT2_PATH="/data/data/com.termux/files/usr/bin/aapt2"
GRADLE_CMD="gradle"

RED='\033[91m'
GREEN='\033[92m'
YELLOW='\033[93m'
CYAN='\033[96m'
BOLD='\033[1m'
RESET='\033[0m'

info() { echo -e "${CYAN}[*] $1${RESET}"; }
ok() { echo -e "${GREEN}[+] $1${RESET}"; }
warn() { echo -e "${YELLOW}[!] $1${RESET}"; }
err() { echo -e "${RED}[-] $1${RESET}"; }
title() { echo -e "${BOLD}\n========================================\n  $1\n========================================${RESET}"; }

run() {
    eval "$1"
    return $?
}

run_quiet() {
    eval "$1" > /dev/null 2>&1
    return $?
}

run_output() {
    eval "$1" 2>/dev/null
}

ask() {
    local prompt="$1"
    local default="$2"
    local suffix=""
    [ -n "$default" ] && suffix=" [$default]"
    echo -ne "${YELLOW}  -> $prompt$suffix: ${RESET}"
    read -r val
    echo "${val:-$default}"
}

confirm() {
    echo -ne "${YELLOW}  -> $1 (y/n): ${RESET}"
    read -r val
    [ "$val" = "y" ] || [ "$val" = "Y" ]
}

write_file() {
    local path="$1"
    local content="$2"
    mkdir -p "$(dirname "$path")"
    echo "$content" > "$path"
}

append_file() {
    local path="$1"
    local content="$2"
    echo "$content" >> "$path"
}

check_env() {
    title "环境检测"
    local all_ok=1

    local checks=(
        "Java (openjdk-17)" "java -version 2>&1 | grep -q '17\\.'" "pkg install -y openjdk-17"
        "Gradle" "which gradle > /dev/null 2>&1" "pkg install -y gradle"
        "wget" "which wget > /dev/null 2>&1" "pkg install -y wget"
        "unzip" "which unzip > /dev/null 2>&1" "pkg install -y unzip"
        "aapt2 (ARM)" "test -f $AAPT2_PATH" "pkg install -y aapt2"
    )

    local missing_sdk=()

    for ((i=0; i<${#checks[@]}; i+=3)); do
        local name="${checks[i]}"
        local check_cmd="${checks[i+1]}"
        local fix_cmd="${checks[i+2]}"

        if eval "$check_cmd"; then
            ok "$name"
        else
            err "$name"
            if [ -n "$fix_cmd" ]; then
                if confirm "是否安装 $name？"; then
                    info "正在安装 $name..."
                    run_quiet "$fix_cmd"
                    if eval "$check_cmd"; then
                        ok "$name 安装完成并验证通过"
                    else
                        err "$name 安装后仍未通过检测，请手动检查"
                        all_ok=0
                    fi
                else
                    all_ok=0
                fi
            else
                missing_sdk+=("$name")
                all_ok=0
            fi
        fi
    done

    if [ -d "$ANDROID_SDK/platforms/android-34" ]; then
        ok "Android SDK"
    else
        err "Android SDK"
        missing_sdk+=("Android SDK")
        all_ok=0
    fi

    if [ -d "$ANDROID_SDK/build-tools/34.0.0" ]; then
        ok "SDK build-tools 34"
    else
        err "SDK build-tools 34"
        missing_sdk+=("SDK build-tools 34")
        all_ok=0
    fi

    if [ -d "$ANDROID_SDK/ndk/25.1.8937393" ]; then
        ok "NDK 25"
    else
        err "NDK 25"
        missing_sdk+=("NDK 25")
        all_ok=0
    fi

    if [ ${#missing_sdk[@]} -gt 0 ]; then
        warn "以下 SDK 组件需要手动安装："
        for item in "${missing_sdk[@]}"; do
            echo "    - $item"
        done
        echo -e "${CYAN}  运行：${RESET}"
        echo '  sdkmanager --sdk_root=$HOME/android-sdk "platforms;android-34" "build-tools;34.0.0" "ndk;25.1.8937393"'
    fi

    check_gradle_props

    if [ $all_ok -eq 1 ]; then
        ok "环境检测全部通过！"
    else
        warn "部分组件缺失，请按提示处理后重试"
    fi

    return $all_ok
}

check_gradle_props() {
    mkdir -p "$(dirname "$GRADLE_PROPS")"

    local required=(
        "org.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=256m"
        "org.gradle.daemon=false"
        "org.gradle.parallel=false"
        "android.enableLint=false"
        "android.aapt2FromMavenOverride=$AAPT2_PATH"
    )

    local changed=0
    local existing=()

    if [ -f "$GRADLE_PROPS" ]; then
        while IFS= read -r line; do
            if echo "$line" | grep -qE '^[^#].*='; then
                existing+=("$line")
            fi
        done < "$GRADLE_PROPS"
    fi

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

switch_project() {
    title "切换项目"
    local projects=($(list_projects))
    if [ ${#projects[@]} -eq 0 ]; then
        warn "没有找到任何项目"
        return 1
    fi

    echo -e "${CYAN}  已有项目：${RESET}"
    for i in "${!projects[@]}"; do
        echo "    $((i+1)). ${projects[i]}"
    done

    local choice=$(ask "选择项目编号")
    local idx=$((choice-1))
    if [ $idx -ge 0 ] && [ $idx -lt ${#projects[@]} ]; then
        local project_name="${projects[idx]}"
        local project_path="$PROJECTS_BASE/$project_name"
        ok "已切换到项目：$project_name"
        echo "$project_name|$project_path"
        return 0
    else
        err "编号无效"
        return 1
    fi
}

create_project() {
    title "生成项目模板"

    local templates=(
        "1:java:Java Only"
        "2:java_kotlin:Java + Kotlin"
        "3:java_kotlin_lua:Java + Kotlin + Lua"
        "4:lua:Lua Only"
    )

    echo -e "${CYAN}  选择模板：${RESET}"
    for t in "${templates[@]}"; do
        IFS=':' read -r num key name <<< "$t"
        echo "    $num. $name"
    done

    local t_choice=$(ask "模板编号" "1")
    local template_key=""
    local template_name=""
    for t in "${templates[@]}"; do
        IFS=':' read -r num key name <<< "$t"
        if [ "$num" = "$t_choice" ]; then
            template_key="$key"
            template_name="$name"
            break
        fi
    done

    if [ -z "$template_key" ]; then
        err "无效选项"
        return 1
    fi

    info "模板：$template_name"

    echo -e "\n${CYAN}  选择 App 目标 Java 版本：${RESET}"
    echo "    1. Java 17（推荐，新语法）"
    echo "    2. Java 8（兼容写法，老设备/老库友好）"
    local j_choice=$(ask "版本编号" "1")
    local java_target="17"
    [ "$j_choice" = "2" ] && java_target="8"
    info "App 字节码目标：Java $java_target"
    warn "注：Termux 官方源无 openjdk-8，Gradle 运行环境本身固定为 JDK17，此选项只影响 App 代码按哪种语法标准编译"

    local app_name=$(ask "应用名称" "MyApp")
    local package_name=$(ask "包名" "com.example.myapp")
    local project_path="$PROJECTS_BASE/$app_name"
    local link_path="$PROJECTS_LINK/$app_name"

    if [ -d "$project_path" ]; then
        if ! confirm "项目 $app_name 已存在，覆盖？"; then
            return 1
        fi
        rm -rf "$project_path"
    fi

    info "正在生成项目：$app_name（$package_name）..."

    local package_path="${package_name//./\/}"

    local manifest='<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="'$app_name'"
        android:theme="@style/AppTheme">
        <activity android:name=".MainActivity"
                  android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>'

    local styles='<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="android:Theme.Material.Light"/>
</resources>'

    local settings="rootProject.name = '$app_name'
include ':app'"

    local root_gradle=''
    if echo "$template_key" | grep -qE 'kotlin|lua'; then
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
        maven { url "https://maven.aliyun.com/repository/gradle-plugin" }
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
        maven { url "https://maven.aliyun.com/repository/gradle-plugin" }
        google()
        mavenCentral()
    }
}'
    fi

    local plugins='apply plugin: "com.android.application"
'
    local deps=''
    if echo "$template_key" | grep -qE 'kotlin|lua'; then
        plugins+='apply plugin: "org.jetbrains.kotlin.android"
'
        deps='    implementation "org.jetbrains.kotlin:kotlin-stdlib:1.9.22"
'
    fi
    if echo "$template_key" | grep -q 'lua'; then
        deps+='    implementation "org.luaj:luaj-jse:3.0.1"
'
    fi

    local kotlin_opts=''
    if echo "$template_key" | grep -qE 'kotlin|lua'; then
        local kotlin_jvm="1.8"
        [ "$java_target" = "17" ] && kotlin_jvm="17"
        kotlin_opts="
    kotlinOptions {
        jvmTarget = '$kotlin_jvm'
    }
"
    fi

    local app_gradle="$plugins"
    app_gradle+='android {
    compileSdk 34
    namespace "'$package_name'"

    defaultConfig {
        applicationId "'$package_name'"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_'$java_target'
        targetCompatibility JavaVersion.VERSION_'$java_target'
    }'
    app_gradle+="$kotlin_opts"
    app_gradle+='    aaptOptions {
        cruncherEnabled false
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }
}

dependencies {'
    app_gradle+="$deps"
    app_gradle+='}

tasks.whenTaskAdded { task ->
    if (task.name == "assembleDebug") {
        task.doLast {
            copy {
                from "build/outputs/apk/debug/app-debug.apk"
                into "/storage/emulated/0/"
                rename { "'$app_name'-debug.apk" }
            }
        }
    }
    if (task.name == "assembleRelease") {
        task.doLast {
            copy {
                from "build/outputs/apk/release/app-release.apk"
                into "/storage/emulated/0/"
                rename { "'$app_name'-release.apk" }
            }
        }
    }
}'

    local main_activity=''
    if [ "$template_key" = "java" ]; then
        main_activity="package $package_name;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText(\"$app_name\");
        tv.setTextSize(24);
        setContentView(tv);
    }
}"
    elif [ "$template_key" = "java_kotlin" ]; then
        main_activity="package $package_name;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String msg = KotlinHelper.INSTANCE.getGreeting(\"$app_name\");
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextSize(24);
        setContentView(tv);
    }
}"
    elif [ "$template_key" = "java_kotlin_lua" ]; then
        main_activity="package $package_name;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String luaResult = runLua();
        String kotlinMsg = KotlinHelper.INSTANCE.getGreeting(\"$app_name\");
        TextView tv = new TextView(this);
        tv.setText(kotlinMsg + \"\\n\" + luaResult);
        tv.setTextSize(20);
        setContentView(tv);
    }

    private String runLua() {
        try {
            org.luaj.vm2.Globals globals = JsePlatform.standardGlobals();
            LuaValue chunk = globals.load(\"return 'Lua: Hello from LuaJ!'\");
            return chunk.call().tojstring();
        } catch (Exception e) {
            return \"Lua 错误: \" + e.getMessage();
        }
    }
}"
    else
        main_activity="package $package_name;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String result = runLua();
        TextView tv = new TextView(this);
        tv.setText(result);
        tv.setTextSize(20);
        setContentView(tv);
    }

    private String runLua() {
        try {
            org.luaj.vm2.Globals globals = JsePlatform.standardGlobals();
            LuaValue chunk = globals.load(
                \"local msg = '$app_name - Lua Only\\\\n'\\n\" +
                \"msg = msg .. 'LuaJ version: ' .. _VERSION\\n\" +
                \"return msg\"
            );
            return chunk.call().tojstring();
        } catch (Exception e) {
            return \"Lua 错误: \" + e.getMessage();
        }
    }
}"
    fi

    local kotlin_helper=''
    if echo "$template_key" | grep -q 'kotlin'; then
        kotlin_helper="package $package_name

object KotlinHelper {
    fun getGreeting(appName: String): String {
        return \"\$appName - Kotlin + Java\"
    }
}"
    fi

    local proguard="# ProGuard 规则 - $app_name
-keep class $package_name.MainActivity { *; }
-keep class org.luaj.** { *; }
-dontwarn org.luaj.**
-keep class kotlin.** { *; }
-dontwarn kotlin.**
-optimizations \!code/simplification/arithmetic,\!code/simplification/cast,\!field/*,\!class/merging/*
-optimizationpasses 5
-allowaccessmodification"

    local wrapper_props="distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://mirrors.aliyun.com/gradle/distributions/gradle-8.7-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists"

    mkdir -p "$project_path/app/src/main/java/$package_path"
    mkdir -p "$project_path/app/src/main/res/values"
    mkdir -p "$project_path/gradle/wrapper"

    write_file "$project_path/settings.gradle" "$settings"
    write_file "$project_path/build.gradle" "$root_gradle"
    write_file "$project_path/app/build.gradle" "$app_gradle"
    write_file "$project_path/app/proguard-rules.pro" "$proguard"
    write_file "$project_path/gradle/wrapper/gradle-wrapper.properties" "$wrapper_props"
    write_file "$project_path/app/src/main/AndroidManifest.xml" "$manifest"
    write_file "$project_path/app/src/main/res/values/styles.xml" "$styles"
    write_file "$project_path/app/src/main/java/$package_path/MainActivity.java" "$main_activity"

    if [ -n "$kotlin_helper" ]; then
        write_file "$project_path/app/src/main/java/$package_path/KotlinHelper.kt" "$kotlin_helper"
    fi

    local jar_path="$project_path/gradle/wrapper/gradle-wrapper.jar"
    if [ ! -f "$jar_path" ]; then
        info "下载 gradle-wrapper.jar..."
        wget -q "https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar" -O "$jar_path"
    fi

    mkdir -p "$PROJECTS_LINK"
    [ -L "$link_path" ] && rm "$link_path"
    ln -s "$project_path" "$link_path"

    ok "项目已生成：$project_path"
    ok "软链接：$link_path"

    return 0
}

setup_protection() {
    local project_path="$1"
    local app_name="$2"
    local package_name="$3"

    title "配置混淆保护"
    info "启用 R8 全模式 + 字符串加密 + 反调试..."

    local app_gradle_path="$project_path/app/build.gradle"
    if [ -f "$app_gradle_path" ]; then
        sed -i 's/minifyEnabled false/minifyEnabled true\n            shrinkResources true\n            proguardFiles getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"/g' "$app_gradle_path"
        ok "build.gradle 混淆配置已更新"
    fi

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

# 类名/方法名完全随机化
-obfuscationdictionary /dev/urandom
-classobfuscationdictionary /dev/urandom
-packageobfuscationdictionary /dev/urandom

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
    # 替换包名变量
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
    # 替换包名变量
    anti_debug=$(echo "$anti_debug" | sed "s/\$package_name/$package_name/g")

    write_file "$guard_dir/AntiDebug.java" "$anti_debug"
    ok "反调试类已生成：AntiDebug.java"

    ok "混淆保护配置完成！release 版本编译时生效"
    info "编译 release：gradle assembleRelease --no-daemon"
}

rename_project() {
    local project_path="$1"
    local old_app_name="$2"
    local old_package="$3"

    title "修改项目信息（改名 / 改包）"
    warn "当前应用名：$old_app_name　当前包名：$old_package"

    local new_app_name=$(ask "新应用名称（留空则不改）" "")
    local new_package=$(ask "新包名（留空则不改）" "")

    [ -z "$new_app_name" ] && new_app_name="$old_app_name"
    [ -z "$new_package" ] && new_package="$old_package"

    [ "$new_app_name" = "$old_app_name" ] && [ "$new_package" = "$old_package" ] && {
        info "未做任何修改"
        echo "$project_path|$old_app_name|$old_package"
        return 0
    }

    local old_pkg_path="${old_package//./\/}"
    local new_pkg_path="${new_package//./\/}"
    local base_dir=$(dirname "$project_path")
    local new_project_path="$base_dir/$new_app_name"

    if [ "$new_app_name" != "$old_app_name" ]; then
        if [ -d "$new_project_path" ]; then
            err "目标目录已存在：$new_project_path，取消操作"
            echo "$project_path|$old_app_name|$old_package"
            return 1
        fi
        mv "$project_path" "$new_project_path"
        ok "项目目录已重命名：$old_app_name -> $new_app_name"
    else
        new_project_path="$project_path"
    fi

    local java_root="$new_project_path/app/src/main/java"
    local old_src_dir="$java_root/$old_pkg_path"
    local new_src_dir="$java_root/$new_pkg_path"

    if [ "$new_package" != "$old_package" ] && [ -d "$old_src_dir" ]; then
        mkdir -p "$(dirname "$new_src_dir")"
        mv "$old_src_dir" "$new_src_dir"
        ok "源码包目录已迁移：$old_pkg_path -> $new_pkg_path"

        local cleanup_dir=$(dirname "$old_src_dir")
        while [ "$cleanup_dir" != "$java_root" ] && [ -d "$cleanup_dir" ] && [ -z "$(ls -A "$cleanup_dir")" ]; do
            rmdir "$cleanup_dir"
            cleanup_dir=$(dirname "$cleanup_dir")
        done
    fi

    local target_src_dir="$new_src_dir"
    [ ! -d "$new_src_dir" ] && target_src_dir="$java_root/$old_pkg_path"

    if [ -d "$target_src_dir" ]; then
        for fname in "$target_src_dir"/*.java "$target_src_dir"/*.kt; do
            [ -f "$fname" ] || continue
            sed -i "s/package $old_package;/package $new_package;/g" "$fname"
            sed -i "s/package $old_package$/package $new_package/g" "$fname"
        done
        ok "源码文件 package 声明已更新"
    fi

    local gradle_path="$new_project_path/app/build.gradle"
    if [ -f "$gradle_path" ]; then
        sed -i "s/namespace '$old_package'/namespace '$new_package'/g" "$gradle_path"
        sed -i "s/applicationId '$old_package'/applicationId '$new_package'/g" "$gradle_path"
        sed -i "s/'$old_app_name-debug.apk'/'$new_app_name-debug.apk'/g" "$gradle_path"
        sed -i "s/'$old_app_name-release.apk'/'$new_app_name-release.apk'/g" "$gradle_path"
        ok "app/build.gradle 已更新"
    fi

    local settings_path="$new_project_path/settings.gradle"
    if [ -f "$settings_path" ]; then
        write_file "$settings_path" "rootProject.name = '$new_app_name'\ninclude ':app'\n"
        ok "settings.gradle 已更新"
    fi

    local manifest_path="$new_project_path/app/src/main/AndroidManifest.xml"
    if [ -f "$manifest_path" ]; then
        sed -i "s/android:label=\"$old_app_name\"/android:label=\"$new_app_name\"/g" "$manifest_path"
        ok "AndroidManifest.xml 已更新"
    fi

    local proguard_path="$new_project_path/app/proguard-rules.pro"
    if [ -f "$proguard_path" ]; then
        sed -i "s/$old_package/$new_package/g" "$proguard_path"
        ok "proguard-rules.pro 已更新"
    fi

    local old_link="$PROJECTS_LINK/$old_app_name"
    local new_link="$PROJECTS_LINK/$new_app_name"
    [ -L "$old_link" ] && rm "$old_link"
    [ -L "$new_link" ] && rm "$new_link"
    ln -s "$new_project_path" "$new_link"
    ok "软链接已更新：$new_link"

    ok "项目信息修改完成！位置仍在：$new_project_path"
    echo "$new_project_path|$new_app_name|$new_package"
    return 0
}

quick_setup() {
    title "一键配置"
    check_gradle_props

    local sdk_cmake="$ANDROID_SDK/cmake/3.22.1/bin/cmake"
    local termux_cmake="/data/data/com.termux/files/usr/bin/cmake"
    if [ -f "$sdk_cmake" ] && [ ! -L "$sdk_cmake" ]; then
        local bak="$sdk_cmake.bak"
        [ ! -f "$bak" ] && mv "$sdk_cmake" "$bak"
        ln -s "$termux_cmake" "$sdk_cmake"
        ok "cmake 软链接已修复"
    fi

    local sdk_ninja="$ANDROID_SDK/cmake/3.22.1/bin/ninja"
    local termux_ninja="/data/data/com.termux/files/usr/bin/ninja"
    if [ -f "$sdk_ninja" ] && [ ! -L "$sdk_ninja" ]; then
        local bak="$sdk_ninja.bak"
        [ ! -f "$bak" ] && mv "$sdk_ninja" "$bak"
        ln -s "$termux_ninja" "$sdk_ninja"
        ok "ninja 软链接已修复"
    fi

    ok "一键配置完成"
}

build_project() {
    local project_path="$1"
    local build_type="${2:-debug}"

    title "编译项目（$build_type）"
    cd "$project_path" || return 1
    local cmd="$GRADLE_CMD assemble${build_type^} --no-daemon"
    info "执行：$cmd"
    $cmd
}

main() {
    local current_project=""
    local current_path=""

    while true; do
        title "开发工具"
        if [ -n "$current_project" ]; then
            echo -e "${GREEN}  当前项目：$current_project${RESET}"
        else
            echo -e "${YELLOW}  当前项目：未选择${RESET}"
        fi

        echo "
  1. 环境检测
  2. 生成项目模板
  3. 切换项目
  4. 一键配置
  5. 混淆保护配置
  6. 编译 Debug
  7. 编译 Release
  8. 修改项目信息（改名/改包）
  0. 退出
"
        local choice=$(ask "选择功能" "0")

        case "$choice" in
            1) check_env ;;
            2)
                create_project
                local projects=($(list_projects))
                [ ${#projects[@]} -gt 0 ] && {
                    current_project="${projects[-1]}"
                    current_path="$PROJECTS_BASE/$current_project"
                }
                ;;
            3)
                local result=$(switch_project)
                if [ $? -eq 0 ]; then
                    IFS='|' read -r current_project current_path <<< "$result"
                fi
                ;;
            4) quick_setup ;;
            5)
                if [ -z "$current_project" ]; then
                    warn "请先选择项目（选项3）"
                else
                    local pkg=$(ask "包名" "com.example.app")
                    setup_protection "$current_path" "$current_project" "$pkg"
                fi
                ;;
            6)
                if [ -z "$current_project" ]; then
                    warn "请先选择项目（选项3）"
                else
                    build_project "$current_path" "debug"
                fi
                ;;
            7)
                if [ -z "$current_project" ]; then
                    warn "请先选择项目（选项3）"
                else
                    build_project "$current_path" "release"
                fi
                ;;
            8)
                if [ -z "$current_project" ]; then
                    warn "请先选择项目（选项3）"
                else
                    local old_pkg=$(ask "当前包名（用于定位源码目录）" "com.example.app")
                    local result=$(rename_project "$current_path" "$current_project" "$old_pkg")
                    if [ $? -eq 0 ]; then
                        IFS='|' read -r current_path current_project _ <<< "$result"
                    fi
                fi
                ;;
            0)
                echo -e "${CYAN}\n  再见！\n${RESET}"
                exit 0
                ;;
            *) warn "无效选项" ;;
        esac

        echo -ne "${YELLOW}\n  按 Enter 继续...${RESET}"
        read -r
    done
}

if [ "$0" = "$BASH_SOURCE" ]; then
    main
fi
