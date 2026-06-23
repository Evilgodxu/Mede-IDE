package com.medeide.jh.screens.home.aitools.tools

import android.util.Log
import com.medeide.jh.data.storage.FileManager
import com.medeide.jh.data.utils.FileLogger

class ProjectTools(
    private val fileManager: FileManager?,
) {
    fun initGradleProject(
        projectName: String,
        template: String = "android",
    ): String {
        Log.d("ProjectTools", "initGradleProject: projectName=$projectName template=$template")
        FileLogger.d("ProjectTools", "initGradleProject: $projectName")

        val fm = fileManager ?: return err("No project folder is open.")
        val projectDir = "${fm.projectDirPath}/$projectName"

        if (fm.exists(projectDir)) {
            return err("Directory already exists: $projectDir")
        }

        return try {
            when (template.lowercase()) {
                "android" -> createAndroidProject(projectName, fm)
                "kotlin" -> createKotlinProject(projectName, fm)
                "java" -> createJavaProject(projectName, fm)
                "gradle" -> createGradleProject(projectName, fm)
                else -> err("Unknown template: $template. Available: android, kotlin, java, gradle")
            }
        } catch (e: Exception) {
            Log.e("ProjectTools", "initGradleProject failed: ${e.message}", e)
            FileLogger.e("ProjectTools", "initGradleProject failed: ${e.message}", e)
            err("Failed to initialize project: ${e.message}")
        }
    }

    private fun createAndroidProject(projectName: String, fm: FileManager): String {
        fm.createDirectory("$projectName/app/src/main/java/com/example/$projectName")
        fm.createDirectory("$projectName/app/src/main/res/layout")
        fm.createDirectory("$projectName/app/src/main/res/drawable")
        fm.createDirectory("$projectName/app/src/test/java/com/example/$projectName")

        fm.writeFile("$projectName/build.gradle", """
            plugins {
                id 'com.android.application' apply false
                id 'org.jetbrains.kotlin.android' apply false
            }
        """.trimIndent())

        fm.writeFile("$projectName/settings.gradle", """
            rootProject.name = '$projectName'
            include ':app'
        """.trimIndent())

        fm.writeFile("$projectName/app/build.gradle", """
            plugins {
                id 'com.android.application'
                id 'org.jetbrains.kotlin.android'
            }

            android {
                namespace 'com.example.$projectName'
                compileSdk 34

                defaultConfig {
                    applicationId 'com.example.$projectName'
                    minSdk 24
                    targetSdk 34
                    versionCode 1
                    versionName '1.0'
                }

                buildTypes {
                    release {
                        minifyEnabled false
                        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
                    }
                }
                compileOptions {
                    sourceCompatibility JavaVersion.VERSION_1_8
                    targetCompatibility JavaVersion.VERSION_1_8
                }
                kotlinOptions {
                    jvmTarget = '1.8'
                }
            }

            dependencies {
                implementation 'androidx.core:core-ktx:1.12.0'
                implementation 'androidx.appcompat:appcompat:1.6.1'
                implementation 'com.google.android.material:material:1.11.0'
                implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
                testImplementation 'junit:junit:4.13.2'
                androidTestImplementation 'androidx.test.ext:junit:1.1.5'
                androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
            }
        """.trimIndent())

        fm.writeFile("$projectName/app/src/main/AndroidManifest.xml", """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools">

                <application
                    android:allowBackup="true"
                    android:dataExtractionRules="@xml/data_extraction_rules"
                    android:fullBackupContent="@xml/backup_rules"
                    android:icon="@mipmap/ic_launcher"
                    android:label="@string/app_name"
                    android:roundIcon="@mipmap/ic_launcher_round"
                    android:supportsRtl="true"
                    android:theme="@style/Theme.$projectName"
                    tools:targetApi="31">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>

            </manifest>
        """.trimIndent())

        fm.writeFile("$projectName/app/src/main/java/com/example/$projectName/MainActivity.kt", """
            package com.example.$projectName

            import androidx.appcompat.app.AppCompatActivity
            import android.os.Bundle

            class MainActivity : AppCompatActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(R.layout.activity_main)
                }
            }
        """.trimIndent())

        fm.writeFile("$projectName/app/src/main/res/layout/activity_main.xml", """
            <?xml version="1.0" encoding="utf-8"?>
            <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                xmlns:tools="http://schemas.android.com/tools"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                tools:context=".MainActivity">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Hello World!"
                    app:layout_constraintBottom_toBottomOf="parent"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintTop_toTopOf="parent" />

            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent())

        return ok("Android project '$projectName' created successfully.")
    }

    private fun createKotlinProject(projectName: String, fm: FileManager): String {
        fm.createDirectory("$projectName/src/main/kotlin/com/example")
        fm.createDirectory("$projectName/src/test/kotlin/com/example")

        fm.writeFile("$projectName/build.gradle.kts", """
            plugins {
                application
                kotlin("jvm") version "1.9.0"
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation(kotlin("stdlib"))
                testImplementation("junit:junit:4.13.2")
            }

            application {
                mainClass.set("com.example.MainKt")
            }
        """.trimIndent())

        fm.writeFile("$projectName/settings.gradle.kts", """
            rootProject.name = "$projectName"
        """.trimIndent())

        fm.writeFile("$projectName/src/main/kotlin/com/example/Main.kt", """
            package com.example

            fun main() {
                println("Hello, $projectName!")
            }
        """.trimIndent())

        fm.writeFile("$projectName/src/test/kotlin/com/example/MainTest.kt", """
            package com.example

            import org.junit.Test
            import org.junit.Assert.*

            class MainTest {
                @Test
                fun testSomething() {
                    assertTrue(true)
                }
            }
        """.trimIndent())

        return ok("Kotlin project '$projectName' created successfully.")
    }

    private fun createJavaProject(projectName: String, fm: FileManager): String {
        fm.createDirectory("$projectName/src/main/java/com/example")
        fm.createDirectory("$projectName/src/test/java/com/example")

        fm.writeFile("$projectName/build.gradle", """
            plugins {
                id 'application'
                id 'java'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation 'junit:junit:4.13.2'
            }

            application {
                mainClass = 'com.example.Main'
            }
        """.trimIndent())

        fm.writeFile("$projectName/settings.gradle", """
            rootProject.name = '$projectName'
        """.trimIndent())

        fm.writeFile("$projectName/src/main/java/com/example/Main.java", """
            package com.example;

            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello, $projectName!");
                }
            }
        """.trimIndent())

        fm.writeFile("$projectName/src/test/java/com/example/MainTest.java", """
            package com.example;

            import org.junit.Test;
            import static org.junit.Assert.*;

            public class MainTest {
                @Test
                public void testSomething() {
                    assertTrue(true);
                }
            }
        """.trimIndent())

        return ok("Java project '$projectName' created successfully.")
    }

    private fun createGradleProject(projectName: String, fm: FileManager): String {
        fm.createDirectory("$projectName/src/main/java/com/example")
        fm.createDirectory("$projectName/src/test/java/com/example")

        fm.writeFile("$projectName/build.gradle", """
            plugins {
                id 'java'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation 'junit:junit:4.13.2'
            }
        """.trimIndent())

        fm.writeFile("$projectName/settings.gradle", """
            rootProject.name = '$projectName'
        """.trimIndent())

        fm.writeFile("$projectName/src/main/java/com/example/Main.java", """
            package com.example;

            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello, $projectName!");
                }
            }
        """.trimIndent())

        return ok("Gradle project '$projectName' created successfully.")
    }

    fun initGitRepository(
        workingDir: String = "",
    ): String {
        Log.d("ProjectTools", "initGitRepository: workingDir=$workingDir")
        FileLogger.d("ProjectTools", "initGitRepository")

        val fm = fileManager ?: return err("No project folder is open.")
        val cwd = if (workingDir.isBlank()) {
            fm.projectDirPath.ifEmpty { fm.storageRootPath }
        } else {
            if (workingDir.startsWith("/")) workingDir else "${fm.projectDirPath}/$workingDir"
        }

        return try {
            val process = ProcessBuilder()
                .command("/system/bin/sh", "-c", "cd $cwd && git init")
                .redirectErrorStream(true)
                .start()

            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }

            process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            ok("Git repository initialized in $cwd\n${output.toString().trimEnd()}")
        } catch (e: Exception) {
            Log.e("ProjectTools", "initGitRepository failed: ${e.message}", e)
            FileLogger.e("ProjectTools", "initGitRepository failed: ${e.message}", e)
            err("Failed to initialize Git repository: ${e.message}")
        }
    }

    fun createGitIgnore(
        projectType: String = "android",
        targetPath: String = "",
    ): String {
        Log.d("ProjectTools", "createGitIgnore: projectType=$projectType")
        FileLogger.d("ProjectTools", "createGitIgnore: $projectType")

        val fm = fileManager ?: return err("No project folder is open.")
        val path = if (targetPath.isBlank()) ".gitignore" else targetPath
        val fullPath = resolvePathOrAbsolute(path, fm)

        val content = when (projectType.lowercase()) {
            "android" -> """
                *.iml
                .gradle
                /local.properties
                /.idea
                /build
                /app/build
                *.apk
                *.ap_
                *.aab
                *.dex
                *.class
                /captures
                /outputs
                /lint-results
                /reports
                /test-results
                /proguard
                *.log
            """.trimIndent()
            "kotlin", "java" -> """
                .gradle
                /build
                *.class
                *.jar
                *.log
                /.idea
                *.iml
            """.trimIndent()
            "flutter" -> """
                .dart_tool/
                .flutter-plugins
                .flutter-plugins-dependencies
                .packages
                .idea/
                android/.gradle/
                android/captures/
                android/gradlew
                android/gradlew.bat
                android/local.properties
                build/
                ios/Flutter/.last_build_id
                ios/Pods/
                pubspec.lock
                *.log
            """.trimIndent()
            "node" -> """
                node_modules/
                .npm
                *.log
                dist/
                build/
                .env
                .idea/
            """.trimIndent()
            else -> """
                .gradle
                /build
                *.class
                *.log
                /.idea
            """.trimIndent()
        }

        return try {
            fm.writeFile(fullPath, content)
            ok(".gitignore created for $projectType project at $fullPath")
        } catch (e: Exception) {
            Log.e("ProjectTools", "createGitIgnore failed: ${e.message}", e)
            FileLogger.e("ProjectTools", "createGitIgnore failed: ${e.message}", e)
            err("Failed to create .gitignore: ${e.message}")
        }
    }

    fun analyzeProjectStructure(): String {
        Log.d("ProjectTools", "analyzeProjectStructure")
        FileLogger.d("ProjectTools", "analyzeProjectStructure")

        val fm = fileManager ?: return err("No project folder is open.")

        return try {
            val projectDir = fm.projectDirPath.ifEmpty { fm.storageRootPath }
            val root = java.io.File(projectDir)

            if (!root.exists()) return err("Project directory not found: $projectDir")

            val buildFiles = mutableListOf<String>()
            val sourceDirs = mutableListOf<String>()
            val configFiles = mutableListOf<String>()
            val projectType = detectProjectType(root)

            root.walk().maxDepth(2).forEach { file ->
                when {
                    file.name == "build.gradle" || file.name == "build.gradle.kts" -> buildFiles.add(file.relativeTo(root).path)
                    file.name == "settings.gradle" || file.name == "settings.gradle.kts" -> configFiles.add(file.relativeTo(root).path)
                    file.name == "pom.xml" -> buildFiles.add(file.relativeTo(root).path)
                    file.name == "package.json" -> buildFiles.add(file.relativeTo(root).path)
                    file.name == "pubspec.yaml" -> buildFiles.add(file.relativeTo(root).path)
                    file.name == "CMakeLists.txt" -> buildFiles.add(file.relativeTo(root).path)
                    file.isDirectory && file.name == "src" -> sourceDirs.add(file.relativeTo(root).path)
                    file.isDirectory && file.name == "app" && file.parentFile == root -> sourceDirs.add(file.relativeTo(root).path)
                }
            }

            return buildString {
                appendLine(ok("Project Analysis:"))
                appendLine("  Type: $projectType")
                appendLine("  Root: $projectDir")
                appendLine()
                appendLine("  Build Files:")
                if (buildFiles.isEmpty()) appendLine("    None")
                else buildFiles.forEach { appendLine("    $it") }
                appendLine()
                appendLine("  Source Directories:")
                if (sourceDirs.isEmpty()) appendLine("    None")
                else sourceDirs.forEach { appendLine("    $it") }
                appendLine()
                appendLine("  Configuration Files:")
                if (configFiles.isEmpty()) appendLine("    None")
                else configFiles.forEach { appendLine("    $it") }
            }.trimEnd()

        } catch (e: Exception) {
            Log.e("ProjectTools", "analyzeProjectStructure failed: ${e.message}", e)
            FileLogger.e("ProjectTools", "analyzeProjectStructure failed: ${e.message}", e)
            err("Failed to analyze project: ${e.message}")
        }
    }

    private fun detectProjectType(root: java.io.File): String {
        return when {
            root.resolve("app/build.gradle").exists() -> "Android (Gradle)"
            root.resolve("build.gradle.kts").exists() -> "Kotlin (Gradle)"
            root.resolve("pom.xml").exists() -> "Java (Maven)"
            root.resolve("package.json").exists() -> "Node.js"
            root.resolve("pubspec.yaml").exists() -> "Flutter/Dart"
            root.resolve("CMakeLists.txt").exists() -> "C/C++ (CMake)"
            root.resolve("Makefile").exists() -> "C/C++ (Make)"
            else -> "Unknown"
        }
    }

    fun getBuildInfo(): String {
        Log.d("ProjectTools", "getBuildInfo")
        FileLogger.d("ProjectTools", "getBuildInfo")

        val fm = fileManager ?: return err("No project folder is open.")

        return try {
            val projectDir = fm.projectDirPath.ifEmpty { fm.storageRootPath }
            val buildFile = java.io.File(projectDir, "build.gradle")
            val buildFileKts = java.io.File(projectDir, "build.gradle.kts")

            if (!buildFile.exists() && !buildFileKts.exists()) {
                return err("No build.gradle found in project root")
            }

            val targetFile = if (buildFile.exists()) buildFile else buildFileKts
            val content = targetFile.readText()

            val versionName = Regex("versionName\\s*[=:]\\s*[\"']([^\"']+)[\"']").find(content)?.groupValues?.get(1) ?: "Unknown"
            val versionCode = Regex("versionCode\\s*[=:]\\s*(\\d+)").find(content)?.groupValues?.get(1) ?: "Unknown"
            val compileSdk = Regex("compileSdk\\s*[=:]\\s*(\\d+)").find(content)?.groupValues?.get(1) ?: "Unknown"
            val minSdk = Regex("minSdk\\s*[=:]\\s*(\\d+)").find(content)?.groupValues?.get(1) ?: "Unknown"
            val targetSdk = Regex("targetSdk\\s*[=:]\\s*(\\d+)").find(content)?.groupValues?.get(1) ?: "Unknown"

            return buildString {
                appendLine(ok("Build Configuration:"))
                appendLine("  Version Name: $versionName")
                appendLine("  Version Code: $versionCode")
                appendLine("  Compile SDK: $compileSdk")
                appendLine("  Min SDK: $minSdk")
                appendLine("  Target SDK: $targetSdk")
            }.trimEnd()

        } catch (e: Exception) {
            Log.e("ProjectTools", "getBuildInfo failed: ${e.message}", e)
            FileLogger.e("ProjectTools", "getBuildInfo failed: ${e.message}", e)
            err("Failed to get build info: ${e.message}")
        }
    }

    fun listDependencies(
        workingDir: String = "",
    ): String {
        Log.d("ProjectTools", "listDependencies: workingDir=$workingDir")
        FileLogger.d("ProjectTools", "listDependencies")

        val fm = fileManager ?: return err("No project folder is open.")
        val cwd = if (workingDir.isBlank()) {
            fm.projectDirPath.ifEmpty { fm.storageRootPath }
        } else {
            if (workingDir.startsWith("/")) workingDir else "${fm.projectDirPath}/$workingDir"
        }

        return try {
            val process = ProcessBuilder()
                .command("/system/bin/sh", "-c", "cd $cwd && ./gradlew dependencies --configuration compileClasspath 2>&1 | head -100")
                .redirectErrorStream(true)
                .start()

            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }

            val completed = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return err("Gradle dependencies command timed out")
            }

            ok(output.toString().trimEnd())
        } catch (e: Exception) {
            Log.e("ProjectTools", "listDependencies failed: ${e.message}", e)
            FileLogger.e("ProjectTools", "listDependencies failed: ${e.message}", e)
            err("Failed to list dependencies: ${e.message}")
        }
    }

    fun createBuildConfig(
        config: String,
        workingDir: String = "",
    ): String {
        Log.d("ProjectTools", "createBuildConfig: workingDir=$workingDir")
        FileLogger.d("ProjectTools", "createBuildConfig")

        val fm = fileManager ?: return err("No project folder is open.")
        val cwd = if (workingDir.isBlank()) {
            fm.projectDirPath.ifEmpty { fm.storageRootPath }
        } else {
            if (workingDir.startsWith("/")) workingDir else "${fm.projectDirPath}/$workingDir"
        }

        return try {
            val buildConfigFile = java.io.File(cwd, "build.config.json")
            buildConfigFile.writeText(config)
            ok("Build config created at ${buildConfigFile.path}")
        } catch (e: Exception) {
            Log.e("ProjectTools", "createBuildConfig failed: ${e.message}", e)
            FileLogger.e("ProjectTools", "createBuildConfig failed: ${e.message}", e)
            err("Failed to create build config: ${e.message}")
        }
    }

    companion object {
        fun buildOpenAIToolsJson(): org.json.JSONArray {
            val arr = org.json.JSONArray()
            arr.put(buildInitGradleProjectTool())
            arr.put(buildInitGitRepositoryTool())
            arr.put(buildCreateGitIgnoreTool())
            arr.put(buildAnalyzeProjectStructureTool())
            arr.put(buildGetBuildInfoTool())
            arr.put(buildListDependenciesTool())
            arr.put(buildCreateBuildConfigTool())
            return arr
        }

        fun toolNames(): List<String> = listOf(
            "initGradleProject", "initGitRepository", "createGitIgnore",
            "analyzeProjectStructure", "getBuildInfo", "listDependencies",
            "createBuildConfig",
        )

        private fun buildInitGradleProjectTool() = toolDef("initGradleProject",
            "初始化新的 Gradle 项目。支持 Android、Kotlin、Java 模板。",
            listOf("projectName"),
            "projectName" to p("string", "项目名称"),
            "template" to p("string", "项目模板：android/kotlin/java/gradle，默认android"),
        )

        private fun buildInitGitRepositoryTool() = toolDef("initGitRepository",
            "初始化 Git 仓库。在指定目录创建新的 Git 仓库。",
            props = arrayOf(
                "workingDir" to p("string", "工作目录，相对项目根目录，留空使用项目根目录"),
            ),
        )

        private fun buildCreateGitIgnoreTool() = toolDef("createGitIgnore",
            "创建 .gitignore 文件。根据项目类型生成合适的忽略规则。",
            props = arrayOf(
                "projectType" to p("string", "项目类型：android/kotlin/java/flutter/node，默认android"),
                "targetPath" to p("string", "目标路径，留空在项目根目录创建"),
            ),
        )

        private fun buildAnalyzeProjectStructureTool() = toolDef("analyzeProjectStructure",
            "分析项目结构，识别项目类型、构建文件和源码目录。",
            props = emptyArray(),
        )

        private fun buildGetBuildInfoTool() = toolDef("getBuildInfo",
            "获取项目构建配置信息，包括版本号、SDK 版本等。",
            props = emptyArray(),
        )

        private fun buildListDependenciesTool() = toolDef("listDependencies",
            "列出项目依赖。使用 Gradle 命令获取依赖树。",
            props = arrayOf(
                "workingDir" to p("string", "工作目录，相对项目根目录"),
            ),
        )

        private fun buildCreateBuildConfigTool() = toolDef("createBuildConfig",
            "创建构建配置文件。将配置内容写入 build.config.json。",
            listOf("config"),
            "config" to p("string", "JSON 格式的构建配置内容"),
            "workingDir" to p("string", "工作目录，相对项目根目录"),
        )
    }
}