# ============================================================
# 基础规则：保留行号信息用于 Release 崩溃堆栈分析
# ============================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# Kotlin Serialization（官方推荐规则）
# https://github.com/Kotlin/kotlinx.serialization/blob/master/rules/common.pro
# ============================================================
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.internal.ClassValueReferences
-keepclassmembers public class **$$serializer {
    private ** descriptor;
}

# ============================================================
# Koin 依赖注入（官方推荐规则）
# https://github.com/InsertKoinIO/koin/blob/main/projects/android/koin-android/proguard-rules.pro
# ============================================================
-keep class * extends org.koin.core.module.Module { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* *;
}

# ============================================================
# Navigation Compose 类型安全 API
# ============================================================
-keep class * implements androidx.navigation.NavType { *; }
-keepclassmembers class * {
    @androidx.navigation.NavType <fields>;
}

# ============================================================
# LiteRT-LM (JNI 反射 + native 异常查找)
# native 代码通过 FindClass 按字符串名查找类，必须保留原名
# ============================================================
-keep class com.google.ai.edge.litertlm.LiteRtLmJniException { *; }
-keep class com.google.ai.edge.litertlm.LiteRtLmJni { *; }
-keep class com.google.ai.edge.litertlm.LiteRtLmJni$* { *; }
-keep class com.google.ai.edge.litertlm.NativeLibraryLoader { *; }
-keep class com.google.ai.edge.litertlm.Engine { *; }
-keep class com.google.ai.edge.litertlm.Conversation { *; }
-keep class com.google.ai.edge.litertlm.EngineConfig { *; }
-keep class com.google.ai.edge.litertlm.ConversationConfig { *; }
-keep class com.google.ai.edge.litertlm.Config* { *; }
-keep class com.google.ai.edge.litertlm.Message { *; }
-keep class com.google.ai.edge.litertlm.Message$* { *; }
-keep class com.google.ai.edge.litertlm.Contents { *; }
-keep class com.google.ai.edge.litertlm.Contents$* { *; }
-keep class com.google.ai.edge.litertlm.Content { *; }
-keep class com.google.ai.edge.litertlm.Content$* { *; }
-keep class com.google.ai.edge.litertlm.Role { *; }
-keep class com.google.ai.edge.litertlm.Backend { *; }
-keep class com.google.ai.edge.litertlm.Backend$* { *; }
-keep class com.google.ai.edge.litertlm.SamplerConfig { *; }
-keep class com.google.ai.edge.litertlm.LogSeverity { *; }
-keep class com.google.ai.edge.litertlm.ExperimentalFlags { *; }
-keep class com.google.ai.edge.litertlm.JsonConvertersKt { *; }
-keep class com.google.ai.edge.litertlm.ToolManager { *; }
-keep class com.google.ai.edge.litertlm.ToolProvider { *; }
-keep class com.google.ai.edge.litertlm.ToolSet { *; }
-keep class com.google.ai.edge.litertlm.OpenApiTool { *; }
-keep class com.google.ai.edge.litertlm.ToolCall { *; }
-keep class com.google.ai.edge.litertlm.ToolProvider { *; }
-keep class com.google.ai.edge.litertlm.Tool { *; }
-keep class com.google.ai.edge.litertlm.ToolParam { *; }
-keep class com.google.ai.edge.litertlm.ToolAnnotatedMethod { *; }
-keep class com.google.ai.edge.litertlm.Channel { *; }
-keep class com.google.ai.edge.litertlm.LoraConfig { *; }
-keep class com.google.ai.edge.litertlm.Session { *; }
-keep class com.google.ai.edge.litertlm.SessionConfig { *; }
-keep class com.google.ai.edge.litertlm.MessageCallback { *; }
-keep class com.google.ai.edge.litertlm.MessageCallback$* { *; }
-keep class com.google.ai.edge.litertlm.BenchmarkInfo { *; }
-keep class com.google.ai.edge.litertlm.Capabilities { *; }

# 自定义 ToolSet（反射调用）
-keep class com.medeide.jh.core.ai.WebSearchTool { *; }
-keepclassmembers class com.medeide.jh.core.ai.WebSearchTool {
    @com.google.ai.edge.litertlm.Tool <methods>;
}

# Gson (LiteRT-LM 依赖)
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.gson.**

# ============================================================
# Zip4j — 加密/压缩反射类
# ============================================================
-keep class net.lingala.zip4j.** { *; }
-dontwarn net.lingala.zip4j.**
