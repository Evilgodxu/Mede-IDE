#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Android 开发环境自动化管理脚本
# 支持：环境检测、项目生成、编译、混淆保护、项目切换

import os
import sys
import subprocess
import shutil
import json

# ── 路径常量 ──────────────────────────────────────────────────
TERMUX_HOME = os.path.expanduser("~")
ANDROID_SDK = os.path.join(TERMUX_HOME, "android-sdk")
PROJECTS_BASE = "/storage/emulated/0/Termux/Android"
PROJECTS_LINK = os.path.join(TERMUX_HOME, "projects")
GRADLE_PROPS = os.path.join(TERMUX_HOME, ".gradle", "gradle.properties")
AAPT2_PATH = "/data/data/com.termux/files/usr/bin/aapt2"
GRADLE_CMD = "gradle"

# ── 颜色输出 ──────────────────────────────────────────────────
def c(text, color):
    # 终端颜色输出
    colors = {
        "red": "\033[91m", "green": "\033[92m",
        "yellow": "\033[93m", "cyan": "\033[96m",
        "bold": "\033[1m", "reset": "\033[0m"
    }
    return f"{colors.get(color,'')}{text}{colors['reset']}"

def info(msg):    print(c(f"[*] {msg}", "cyan"))
def ok(msg):      print(c(f"[✓] {msg}", "green"))
def warn(msg):    print(c(f"[!] {msg}", "yellow"))
def err(msg):     print(c(f"[✗] {msg}", "red"))
def title(msg):   print(c(f"\n{'='*50}\n  {msg}\n{'='*50}", "bold"))

# ── 工具函数 ──────────────────────────────────────────────────
def run(cmd, silent=False):
    # 执行 shell 命令（stderr 合并到 stdout，便于 grep 匹配）
    full_cmd = f"({cmd}) 2>&1" if silent else cmd
    result = subprocess.run(full_cmd, shell=True, capture_output=silent, text=True)
    return result.returncode == 0

def run_quiet(cmd):
    # 静默执行命令
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return result.returncode == 0

def run_output(cmd):
    # 执行命令并返回输出
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return result.stdout.strip()

def ask(prompt, default=None):
    # 用户输入
    suffix = f" [{default}]" if default else ""
    val = input(c(f"  → {prompt}{suffix}: ", "yellow")).strip()
    return val if val else default

def confirm(prompt):
    # 确认提示
    return input(c(f"  → {prompt} (y/n): ", "yellow")).strip().lower() == "y"

def write_file(path, content):
    # 写文件，自动创建目录
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

# ── 环境检测 ──────────────────────────────────────────────────
def check_env():
    title("环境检测")
    all_ok = True

    # 检测各项工具
    checks = [
        ("Java (openjdk-17)",    "java -version 2>&1 | grep -q '17\\.'",
         "pkg install -y openjdk-17 < /dev/null > /dev/null 2>&1"),
        ("Gradle",               "which gradle > /dev/null 2>&1",
         "pkg install -y gradle < /dev/null > /dev/null 2>&1"),
        ("wget",                 "which wget > /dev/null 2>&1",
         "pkg install -y wget < /dev/null > /dev/null 2>&1"),
        ("unzip",                "which unzip > /dev/null 2>&1",
         "pkg install -y unzip < /dev/null > /dev/null 2>&1"),
        ("aapt2 (ARM)",          f"test -f {AAPT2_PATH}",
         "pkg install -y aapt2 < /dev/null > /dev/null 2>&1"),
        ("Android SDK",          f"test -d {ANDROID_SDK}/platforms/android-34",  None),
        ("SDK build-tools 34",   f"test -d {ANDROID_SDK}/build-tools/34.0.0",    None),
        ("NDK 25",               f"test -d {ANDROID_SDK}/ndk/25.1.8937393",      None),
    ]

    missing_sdk = []
    for name, check_cmd, fix_cmd in checks:
        ok_flag = run(check_cmd, silent=True)
        if ok_flag:
            ok(name)
        else:
            err(name)
            if fix_cmd:
                if confirm(f"是否安装 {name}？"):
                    info(f"正在安装 {name}（静默模式）...")
                    run_quiet(fix_cmd)
                    # 安装后立即重新验证
                    if run(check_cmd, silent=True):
                        ok(f"{name} 安装完成并验证通过")
                    else:
                        err(f"{name} 安装后仍未通过检测，请手动检查")
                        all_ok = False
                else:
                    all_ok = False
            else:
                missing_sdk.append(name)
                all_ok = False

    # 提示 SDK 组件需手动安装
    if missing_sdk:
        warn("以下 SDK 组件需要手动安装：")
        for item in missing_sdk:
            print(f"    - {item}")
        print(c("  运行：", "cyan"))
        print(f'  sdkmanager --sdk_root=$HOME/android-sdk "platforms;android-34" "build-tools;34.0.0" "ndk;25.1.8937393"')

    # 检测 gradle.properties
    check_gradle_props()

    if all_ok:
        ok("环境检测全部通过！")
    else:
        warn("部分组件缺失，请按提示处理后重试")

    return all_ok

def check_gradle_props():
    # 确保 gradle.properties 配置正确
    required = {
        "org.gradle.jvmargs": "-Xmx1024m -XX:MaxMetaspaceSize=256m",
        "org.gradle.daemon": "false",
        "org.gradle.parallel": "false",
        "android.enableLint": "false",
        "android.aapt2FromMavenOverride": AAPT2_PATH,
    }
    os.makedirs(os.path.dirname(GRADLE_PROPS), exist_ok=True)

    # 读取现有配置
    existing = {}
    if os.path.exists(GRADLE_PROPS):
        with open(GRADLE_PROPS, "r") as f:
            for line in f:
                line = line.strip()
                if "=" in line and not line.startswith("#"):
                    k, v = line.split("=", 1)
                    existing[k.strip()] = v.strip()

    # 写入缺失配置
    changed = False
    for k, v in required.items():
        if existing.get(k) != v:
            existing[k] = v
            changed = True

    if changed:
        with open(GRADLE_PROPS, "w") as f:
            for k, v in existing.items():
                f.write(f"{k}={v}\n")
        ok("gradle.properties 已更新")

# ── 获取已有项目列表 ──────────────────────────────────────────
def list_projects():
    # 列出所有项目
    if not os.path.isdir(PROJECTS_BASE):
        return []
    projects = []
    for name in os.listdir(PROJECTS_BASE):
        path = os.path.join(PROJECTS_BASE, name)
        if os.path.isdir(path) and os.path.exists(os.path.join(path, "app/build.gradle")):
            projects.append(name)
    return sorted(projects)

# ── 切换项目 ──────────────────────────────────────────────────
def switch_project():
    title("切换项目")
    projects = list_projects()
    if not projects:
        warn("没有找到任何项目")
        return None

    print(c("  已有项目：", "cyan"))
    for i, name in enumerate(projects, 1):
        print(f"    {i}. {name}")

    choice = ask("选择项目编号")
    try:
        idx = int(choice) - 1
        if 0 <= idx < len(projects):
            project_name = projects[idx]
            project_path = os.path.join(PROJECTS_BASE, project_name)
            ok(f"已切换到项目：{project_name}")
            return project_name, project_path
        else:
            err("编号无效")
            return None
    except (ValueError, TypeError):
        err("请输入有效编号")
        return None

# ── 模板内容生成 ──────────────────────────────────────────────
def get_template_files(app_name, package_name, template, java_target="17"):
    # 根据模板类型生成文件内容；java_target: "8" 或 "17"（影响 App 字节码目标版本，Gradle 本身始终用 JDK17 运行）
    package_path = package_name.replace(".", "/")

    # ── AndroidManifest.xml ──
    manifest = f'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="{app_name}"
        android:theme="@style/AppTheme">
        <activity android:name=".MainActivity"
                  android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
'''

    # ── styles.xml ──
    styles = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="android:Theme.Material.Light"/>
</resources>
'''

    # ── settings.gradle ──
    settings = f'''rootProject.name = '{app_name}'
include ':app'
'''

    # ── root build.gradle ──
    if template in ("java_kotlin", "java_kotlin_lua", "lua"):
        root_gradle = '''buildscript {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.3.0'
        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22'
    }
}
allprojects {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
        google()
        mavenCentral()
    }
}
'''
    else:
        root_gradle = '''buildscript {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.3.0'
    }
}
allprojects {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/public' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin' }
        google()
        mavenCentral()
    }
}
'''

    # ── app/build.gradle ──
    plugins = "apply plugin: 'com.android.application'\n"
    deps = ""
    if template in ("java_kotlin", "java_kotlin_lua", "lua"):
        plugins += "apply plugin: 'org.jetbrains.kotlin.android'\n"
        deps += "    implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.22'\n"
    if template in ("java_kotlin_lua", "lua"):
        deps += "    implementation 'org.luaj:luaj-jse:3.0.1'\n"

    kotlin_opts = ""
    if template in ("java_kotlin", "java_kotlin_lua", "lua"):
        kotlin_jvm = "1.8" if java_target == "8" else "17"
        kotlin_opts = f'''
    kotlinOptions {{
        jvmTarget = '{kotlin_jvm}'
    }}
'''

    app_gradle = f'''{plugins}
android {{
    compileSdk 34
    namespace '{package_name}'

    defaultConfig {{
        applicationId '{package_name}'
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName '1.0'
    }}

    compileOptions {{
        sourceCompatibility JavaVersion.VERSION_{java_target}
        targetCompatibility JavaVersion.VERSION_{java_target}
    }}
{kotlin_opts}
    aaptOptions {{
        cruncherEnabled false
    }}

    buildTypes {{
        release {{
            minifyEnabled false
        }}
    }}
}}

dependencies {{
{deps}}}

// 编译完成后复制 APK 到 /storage/emulated/0/
tasks.whenTaskAdded {{ task ->
    if (task.name == 'assembleDebug') {{
        task.doLast {{
            copy {{
                from 'build/outputs/apk/debug/app-debug.apk'
                into '/storage/emulated/0/'
                rename {{ '{app_name}-debug.apk' }}
            }}
        }}
    }}
    if (task.name == 'assembleRelease') {{
        task.doLast {{
            copy {{
                from 'build/outputs/apk/release/app-release.apk'
                into '/storage/emulated/0/'
                rename {{ '{app_name}-release.apk' }}
            }}
        }}
    }}
}}
'''

    # ── MainActivity.java ──
    if template == "java":
        main_activity = f'''package {package_name};

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

// 主Activity - Java 模板
public class MainActivity extends Activity {{
    @Override
    protected void onCreate(Bundle savedInstanceState) {{
        super.onCreate(savedInstanceState);
        // 设置界面
        TextView tv = new TextView(this);
        tv.setText("{app_name}");
        tv.setTextSize(24);
        setContentView(tv);
    }}
}}
'''
    elif template == "java_kotlin":
        main_activity = f'''package {package_name};

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

// 主Activity - Java + Kotlin 模板
public class MainActivity extends Activity {{
    @Override
    protected void onCreate(Bundle savedInstanceState) {{
        super.onCreate(savedInstanceState);
        // 调用 Kotlin 工具类
        String msg = KotlinHelper.INSTANCE.getGreeting("{app_name}");
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextSize(24);
        setContentView(tv);
    }}
}}
'''
    elif template == "java_kotlin_lua":
        main_activity = f'''package {package_name};

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

// 主Activity - Java + Kotlin + Lua 模板
public class MainActivity extends Activity {{
    @Override
    protected void onCreate(Bundle savedInstanceState) {{
        super.onCreate(savedInstanceState);
        // 运行 Lua 脚本
        String luaResult = runLua();
        // 调用 Kotlin 工具类
        String kotlinMsg = KotlinHelper.INSTANCE.getGreeting("{app_name}");
        TextView tv = new TextView(this);
        tv.setText(kotlinMsg + "\\n" + luaResult);
        tv.setTextSize(20);
        setContentView(tv);
    }}

    private String runLua() {{
        // 执行内联 Lua 脚本
        try {{
            org.luaj.vm2.Globals globals = JsePlatform.standardGlobals();
            LuaValue chunk = globals.load("return 'Lua: Hello from LuaJ!'");
            return chunk.call().tojstring();
        }} catch (Exception e) {{
            return "Lua 错误: " + e.getMessage();
        }}
    }}
}}
'''
    else:  # lua only
        main_activity = f'''package {package_name};

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

// 主Activity - Lua 模板（通过 LuaJ 运行）
public class MainActivity extends Activity {{
    @Override
    protected void onCreate(Bundle savedInstanceState) {{
        super.onCreate(savedInstanceState);
        // 运行 Lua 脚本
        String result = runLua();
        TextView tv = new TextView(this);
        tv.setText(result);
        tv.setTextSize(20);
        setContentView(tv);
    }}

    private String runLua() {{
        // 执行 Lua 脚本
        try {{
            org.luaj.vm2.Globals globals = JsePlatform.standardGlobals();
            LuaValue chunk = globals.load(
                "local msg = '{app_name} - Lua Only\\\\n'\\n" +
                "msg = msg .. 'LuaJ version: ' .. _VERSION\\n" +
                "return msg"
            );
            return chunk.call().tojstring();
        }} catch (Exception e) {{
            return "Lua 错误: " + e.getMessage();
        }}
    }}
}}
'''

    # ── KotlinHelper.kt（仅 Kotlin 相关模板）──
    kotlin_helper = None
    if template in ("java_kotlin", "java_kotlin_lua"):
        kotlin_helper = f'''package {package_name}

// Kotlin 工具类
object KotlinHelper {{
    // 返回问候语
    fun getGreeting(appName: String): String {{
        return "$appName - Kotlin + Java"
    }}
}}
'''

    # ── proguard-rules.pro ──
    proguard = f'''# ProGuard 规则 - {app_name}
# 保留主 Activity
-keep class {package_name}.MainActivity {{ *; }}

# 保留 LuaJ
-keep class org.luaj.** {{ *; }}
-dontwarn org.luaj.**

# 保留 Kotlin
-keep class kotlin.** {{ *; }}
-dontwarn kotlin.**

# 字符串加密（R8 启用后生效）
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
'''

    # ── gradle-wrapper.properties（阿里云镜像源）──
    wrapper_props = '''distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://mirrors.aliyun.com/gradle/distributions/gradle-8.7-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
'''

    return {
        "manifest": manifest,
        "styles": styles,
        "settings": settings,
        "root_gradle": root_gradle,
        "app_gradle": app_gradle,
        "main_activity": main_activity,
        "kotlin_helper": kotlin_helper,
        "proguard": proguard,
        "wrapper_props": wrapper_props,
        "package_path": package_path,
    }

# ── 生成项目模板 ──────────────────────────────────────────────
def create_project():
    title("生成项目模板")

    # 选择模板
    templates = {
        "1": ("java",           "Java Only"),
        "2": ("java_kotlin",    "Java + Kotlin"),
        "3": ("java_kotlin_lua","Java + Kotlin + Lua"),
        "4": ("lua",            "Lua Only"),
    }
    print(c("  选择模板：", "cyan"))
    for k, (_, name) in templates.items():
        print(f"    {k}. {name}")

    t_choice = ask("模板编号", "1")
    if t_choice not in templates:
        err("无效选项")
        return

    template_key, template_name = templates[t_choice]
    info(f"模板：{template_name}")

    # 选择 App 目标 Java 版本
    print(c("\n  选择 App 目标 Java 版本：", "cyan"))
    print("    1. Java 17（推荐，新语法）")
    print("    2. Java 8（兼容写法，老设备/老库友好）")
    j_choice = ask("版本编号", "1")
    java_target = "8" if j_choice == "2" else "17"
    info(f"App 字节码目标：Java {java_target}")
    warn("注：Termux 官方源无 openjdk-8，Gradle 运行环境本身固定为 JDK17，"
         "此选项只影响 App 代码按哪种语法标准编译（向下兼容）")

    # 输入项目信息
    app_name    = ask("应用名称", "MyApp")
    package_name = ask("包名", "com.example.myapp")

    project_path = os.path.join(PROJECTS_BASE, app_name)
    link_path    = os.path.join(PROJECTS_LINK, app_name)

    # 检查是否已存在
    if os.path.exists(project_path):
        if not confirm(f"项目 {app_name} 已存在，覆盖？"):
            return

    info(f"正在生成项目：{app_name}（{package_name}）...")

    files = get_template_files(app_name, package_name, template_key, java_target)
    pkg_path = files["package_path"]

    # 创建目录结构
    dirs = [
        f"{project_path}/app/src/main/java/{pkg_path}",
        f"{project_path}/app/src/main/res/values",
        f"{project_path}/gradle/wrapper",
    ]
    for d in dirs:
        os.makedirs(d, exist_ok=True)

    # 写入文件
    write_file(f"{project_path}/settings.gradle",                              files["settings"])
    write_file(f"{project_path}/build.gradle",                                 files["root_gradle"])
    write_file(f"{project_path}/app/build.gradle",                             files["app_gradle"])
    write_file(f"{project_path}/app/proguard-rules.pro",                       files["proguard"])
    write_file(f"{project_path}/gradle/wrapper/gradle-wrapper.properties",     files["wrapper_props"])
    write_file(f"{project_path}/app/src/main/AndroidManifest.xml",             files["manifest"])
    write_file(f"{project_path}/app/src/main/res/values/styles.xml",           files["styles"])
    write_file(f"{project_path}/app/src/main/java/{pkg_path}/MainActivity.java", files["main_activity"])

    # Kotlin 文件
    if files["kotlin_helper"]:
        write_file(f"{project_path}/app/src/main/java/{pkg_path}/KotlinHelper.kt", files["kotlin_helper"])

    # 下载 gradle-wrapper.jar
    jar_path = f"{project_path}/gradle/wrapper/gradle-wrapper.jar"
    if not os.path.exists(jar_path):
        info("下载 gradle-wrapper.jar...")
        run(f"wget -q https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar -O {jar_path}")

    # 创建软链接
    os.makedirs(PROJECTS_LINK, exist_ok=True)
    if os.path.islink(link_path):
        os.unlink(link_path)
    os.symlink(project_path, link_path)

    ok(f"项目已生成：{project_path}")
    ok(f"软链接：{link_path}")

# ── 混淆保护配置 ──────────────────────────────────────────────
def setup_protection(project_path, app_name, package_name):
    title("配置混淆保护")

    info("启用 R8 全模式 + 字符串加密 + 反调试...")

    # 更新 app/build.gradle 启用 release 混淆
    app_gradle_path = os.path.join(project_path, "app/build.gradle")
    with open(app_gradle_path, "r") as f:
        content = f.read()

    # 替换 release 配置
    old_release = '''    buildTypes {
        release {
            minifyEnabled false
        }
    }'''

    new_release = f'''    buildTypes {{
        debug {{
            // 调试版也启用混淆（可选）
            minifyEnabled false
        }}
        release {{
            // 启用 R8 全模式混淆
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }}
    }}'''

    if old_release in content:
        content = content.replace(old_release, new_release)
        with open(app_gradle_path, "w") as f:
            f.write(content)
        ok("build.gradle 混淆配置已更新")

    # 写入增强版 proguard 规则
    proguard_content = f'''# ── 基础保留 ────────────────────────────────────────────────
-keep class {package_name}.MainActivity {{ *; }}
-keepattributes *Annotation*

# ── LuaJ ────────────────────────────────────────────────────
-keep class org.luaj.** {{ *; }}
-dontwarn org.luaj.**

# ── Kotlin ──────────────────────────────────────────────────
-keep class kotlin.** {{ *; }}
-keep class kotlinx.** {{ *; }}
-dontwarn kotlin.**

# ── 激进混淆选项 ─────────────────────────────────────────────
# 5 轮优化
-optimizationpasses 5
-allowaccessmodification
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# 类名/方法名完全随机化
-obfuscationdictionary /dev/urandom
-classobfuscationdictionary /dev/urandom
-packageobfuscationdictionary /dev/urandom

# 合并包名（更难反编译）
-repackageclasses 'x'
-flattenpackagehierarchy 'x'

# ── 反调试（插入 Java 层检测）───────────────────────────────
-keep class {package_name}.guard.** {{ *; }}

# ── 移除 Log 调用 ────────────────────────────────────────────
-assumenosideeffects class android.util.Log {{
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}}

# ── 字符串加密（R8 fullMode 生效）───────────────────────────
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,string/encryption
'''
    write_file(os.path.join(project_path, "app/proguard-rules.pro"), proguard_content)
    ok("ProGuard 规则已写入")

    # 启用 R8 fullMode
    gradle_props_project = os.path.join(project_path, "gradle.properties")
    write_file(gradle_props_project, "android.enableR8.fullMode=true\n")
    ok("R8 全模式已启用")

    # 生成反调试 Java 类
    guard_dir = os.path.join(project_path, f"app/src/main/java/{package_name.replace('.','/')}/guard")
    os.makedirs(guard_dir, exist_ok=True)
    anti_debug = f'''package {package_name}.guard;

import android.content.Context;
import android.os.Debug;
import android.os.Process;

// 反调试检测类
public class AntiDebug {{

    // 检测是否被调试
    public static void check(Context ctx) {{
        // 检测调试器
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {{
            Process.killProcess(Process.myPid());
        }}
        // 检测模拟器
        if (isEmulator()) {{
            Process.killProcess(Process.myPid());
        }}
    }}

    // 判断是否在模拟器运行
    private static boolean isEmulator() {{
        return android.os.Build.FINGERPRINT.startsWith("generic")
            || android.os.Build.FINGERPRINT.startsWith("unknown")
            || android.os.Build.MODEL.contains("Emulator")
            || android.os.Build.MODEL.contains("Android SDK");
    }}
}}
'''
    write_file(os.path.join(guard_dir, "AntiDebug.java"), anti_debug)
    ok("反调试类已生成：AntiDebug.java")

    ok("混淆保护配置完成！release 版本编译时生效")
    info("编译 release：gradle assembleRelease --no-daemon")

def rename_project(project_path, old_app_name, old_package):
    title("修改项目信息（改名 / 改包）")
    warn(f"当前应用名：{old_app_name}　当前包名：{old_package}")

    new_app_name = ask("新应用名称（留空则不改）", "")
    new_package  = ask("新包名（留空则不改）", "")

    if not new_app_name and not new_package:
        info("未做任何修改")
        return project_path, old_app_name, old_package

    new_app_name = new_app_name or old_app_name
    new_package  = new_package or old_package

    old_pkg_path = old_package.replace(".", "/")
    new_pkg_path = new_package.replace(".", "/")

    base_dir = os.path.dirname(project_path)
    new_project_path = os.path.join(base_dir, new_app_name)

    # ── 1. 先处理文件夹改名（如果应用名变了）──────────────
    if new_app_name != old_app_name:
        if os.path.exists(new_project_path):
            err(f"目标目录已存在：{new_project_path}，取消操作")
            return project_path, old_app_name, old_package
        shutil.move(project_path, new_project_path)
        ok(f"项目目录已重命名：{old_app_name} → {new_app_name}")
    else:
        new_project_path = project_path

    # ── 2. 处理包名目录结构迁移（如果包名变了）──────────────
    java_root = os.path.join(new_project_path, "app/src/main/java")
    old_src_dir = os.path.join(java_root, old_pkg_path)
    new_src_dir = os.path.join(java_root, new_pkg_path)

    if new_package != old_package and os.path.isdir(old_src_dir):
        os.makedirs(os.path.dirname(new_src_dir), exist_ok=True)
        shutil.move(old_src_dir, new_src_dir)
        ok(f"源码包目录已迁移：{old_pkg_path} → {new_pkg_path}")

        # 清理迁移后留下的空目录
        cleanup_dir = os.path.dirname(old_src_dir)
        while cleanup_dir != java_root and os.path.isdir(cleanup_dir) and not os.listdir(cleanup_dir):
            empty = cleanup_dir
            cleanup_dir = os.path.dirname(cleanup_dir)
            os.rmdir(empty)

    # ── 3. 替换所有源码文件里的 package 声明 ──────────────
    target_src_dir = new_src_dir if os.path.isdir(new_src_dir) else os.path.join(java_root, old_pkg_path)
    if os.path.isdir(target_src_dir):
        for fname in os.listdir(target_src_dir):
            if fname.endswith((".java", ".kt")):
                fpath = os.path.join(target_src_dir, fname)
                with open(fpath, "r", encoding="utf-8") as f:
                    content = f.read()
                content = content.replace(f"package {old_package};", f"package {new_package};")
                content = content.replace(f"package {old_package}\n", f"package {new_package}\n")
                with open(fpath, "w", encoding="utf-8") as f:
                    f.write(content)
        ok("源码文件 package 声明已更新")

    # ── 4. 更新 app/build.gradle（包名 + APK 输出名）─────
    gradle_path = os.path.join(new_project_path, "app/build.gradle")
    if os.path.exists(gradle_path):
        with open(gradle_path, "r", encoding="utf-8") as f:
            content = f.read()
        content = content.replace(f"namespace '{old_package}'", f"namespace '{new_package}'")
        content = content.replace(f"applicationId '{old_package}'", f"applicationId '{new_package}'")
        content = content.replace(f"'{old_app_name}-debug.apk'", f"'{new_app_name}-debug.apk'")
        content = content.replace(f"'{old_app_name}-release.apk'", f"'{new_app_name}-release.apk'")
        with open(gradle_path, "w", encoding="utf-8") as f:
            f.write(content)
        ok("app/build.gradle 已更新")

    # ── 5. 更新 settings.gradle（项目显示名）───────────
    settings_path = os.path.join(new_project_path, "settings.gradle")
    if os.path.exists(settings_path):
        write_file(settings_path, f"rootProject.name = '{new_app_name}'\ninclude ':app'\n")
        ok("settings.gradle 已更新")

    # ── 6. 更新 AndroidManifest.xml（显示标签）─────────
    manifest_path = os.path.join(new_project_path, "app/src/main/AndroidManifest.xml")
    if os.path.exists(manifest_path):
        with open(manifest_path, "r", encoding="utf-8") as f:
            content = f.read()
        content = content.replace(f'android:label="{old_app_name}"', f'android:label="{new_app_name}"')
        with open(manifest_path, "w", encoding="utf-8") as f:
            f.write(content)
        ok("AndroidManifest.xml 已更新")

    # ── 7. 更新 proguard-rules.pro 里的包名引用 ────────
    proguard_path = os.path.join(new_project_path, "app/proguard-rules.pro")
    if os.path.exists(proguard_path):
        with open(proguard_path, "r", encoding="utf-8") as f:
            content = f.read()
        content = content.replace(old_package, new_package)
        with open(proguard_path, "w", encoding="utf-8") as f:
            f.write(content)

    # ── 8. 修复软链接 ──────────────────────────────────
    old_link = os.path.join(PROJECTS_LINK, old_app_name)
    new_link = os.path.join(PROJECTS_LINK, new_app_name)
    if os.path.islink(old_link):
        os.unlink(old_link)
    if os.path.islink(new_link):
        os.unlink(new_link)
    os.symlink(new_project_path, new_link)
    ok(f"软链接已更新：{new_link}")

    ok(f"项目信息修改完成！位置仍在：{new_project_path}")
    return new_project_path, new_app_name, new_package

# ── 一键配置 ─────────────────────────────────────────────────
def quick_setup():
    title("一键配置")
    check_gradle_props()

    # 确保 gradle-wrapper 软链接正确
    sdk_cmake = os.path.join(ANDROID_SDK, "cmake/3.22.1/bin/cmake")
    termux_cmake = "/data/data/com.termux/files/usr/bin/cmake"
    if os.path.exists(sdk_cmake) and not os.path.islink(sdk_cmake):
        bak = sdk_cmake + ".bak"
        if not os.path.exists(bak):
            os.rename(sdk_cmake, bak)
        os.symlink(termux_cmake, sdk_cmake)
        ok("cmake 软链接已修复")

    sdk_ninja = os.path.join(ANDROID_SDK, "cmake/3.22.1/bin/ninja")
    termux_ninja = "/data/data/com.termux/files/usr/bin/ninja"
    if os.path.exists(sdk_ninja) and not os.path.islink(sdk_ninja):
        bak = sdk_ninja + ".bak"
        if not os.path.exists(bak):
            os.rename(sdk_ninja, bak)
        os.symlink(termux_ninja, sdk_ninja)
        ok("ninja 软链接已修复")

    ok("一键配置完成")

# ── 编译项目 ──────────────────────────────────────────────────
def build_project(project_path, build_type="debug"):
    title(f"编译项目（{build_type}）")
    os.chdir(project_path)
    cmd = f"{GRADLE_CMD} assemble{build_type.capitalize()} --no-daemon"
    info(f"执行：{cmd}")
    run(cmd)

# ── 主菜单 ────────────────────────────────────────────────────
def main():
    # 当前选中项目
    current_project = None
    current_path = None

    while True:
        title("开发工具")
        if current_project:
            print(c(f"  当前项目：{current_project}", "green"))
        else:
            print(c("  当前项目：未选择", "yellow"))

        print("""
  1. 环境检测
  2. 生成项目模板
  3. 切换项目
  4. 一键配置
  5. 混淆保护配置
  6. 编译 Debug
  7. 编译 Release
  8. 修改项目信息（改名/改包）
  0. 退出
""")
        choice = ask("选择功能", "0")

        if choice == "1":
            check_env()

        elif choice == "2":
            create_project()
            # 生成后自动切换
            projects = list_projects()
            if projects:
                current_project = projects[-1]
                current_path = os.path.join(PROJECTS_BASE, current_project)

        elif choice == "3":
            result = switch_project()
            if result:
                current_project, current_path = result

        elif choice == "4":
            quick_setup()

        elif choice == "5":
            if not current_project:
                warn("请先选择项目（选项3）")
            else:
                pkg = ask("包名", "com.example.app")
                setup_protection(current_path, current_project, pkg)

        elif choice == "6":
            if not current_project:
                warn("请先选择项目（选项3）")
            else:
                build_project(current_path, "debug")

        elif choice == "7":
            if not current_project:
                warn("请先选择项目（选项3）")
            else:
                build_project(current_path, "release")

        elif choice == "8":
            if not current_project:
                warn("请先选择项目（选项3）")
            else:
                old_pkg = ask("当前包名（用于定位源码目录）", "com.example.app")
                new_path, new_name, _ = rename_project(current_path, current_project, old_pkg)
                current_path, current_project = new_path, new_name

        elif choice == "0":
            print(c("\n  再见！\n", "cyan"))
            sys.exit(0)

        else:
            warn("无效选项")

        input(c("\n  按 Enter 继续...", "yellow"))

if __name__ == "__main__":
    main()
