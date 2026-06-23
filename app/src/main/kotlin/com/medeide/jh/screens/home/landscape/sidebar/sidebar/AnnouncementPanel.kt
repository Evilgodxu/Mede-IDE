package com.medeide.jh.screens.home.landscape.sidebar.sidebar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementPanel(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("用户说明") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Mede IDE 用户使用指南",
                    style = MaterialTheme.typography.titleLarge
                )

                SectionTitle("快速开始")

                BulletPoint("首次启动应用，允许存储权限以访问文件")
                BulletPoint("点击左侧边栏的文件夹图标打开资源管理器")
                BulletPoint("选择目录作为项目根目录，支持 Android 项目和普通代码项目")
                BulletPoint("点击文件即可在编辑器中打开，支持语法高亮")
                BulletPoint("右侧面板是 AI 对话区域，可以与 AI 交互完成各种任务")

                SectionTitle("侧边栏功能说明")

                BulletPoint("文件夹图标：打开资源管理器，浏览项目文件")
                BulletPoint("搜索图标：在项目中搜索文件或代码内容")
                BulletPoint("终端图标：打开/关闭底部终端面板")
                BulletPoint("代码片段图标：管理代码片段模板")
                BulletPoint("书签图标：查看已保存的文件书签")
                BulletPoint("最近文件图标：快速访问最近打开的文件")
                BulletPoint("公告图标：查看用户使用指南")

                SectionTitle("内置终端")

                BulletPoint("点击左侧边栏的 Terminal 图标打开底部终端面板")
                BulletPoint("点击终端屏幕即可弹出键盘进行输入")
                BulletPoint("终端基于 Termux 源码构建，支持大多数 Linux 命令")
                BulletPoint("再次点击 Terminal 图标或右上角关闭按钮关闭终端")
                BulletPoint("在设置中可以调整终端高度（范围 100-500dp）")
                BulletPoint("支持执行 shell 命令、编译项目、Git 操作等")

                SectionTitle("AI 助手功能")

                BulletPoint("右侧面板是 AI 对话区域，支持自然语言交互")
                BulletPoint("可以让 AI 帮你读写文件、执行命令、搜索代码")
                BulletPoint("支持本地模型和云端 API，点击顶部模型选择器切换")
                BulletPoint("AI 可以自动调用工具完成任务，无需手动操作")

                SectionTitle("AI 工具能力")

                BulletPoint("文件操作：读取、写入、编辑、删除、复制、移动文件")
                BulletPoint("代码搜索：正则搜索、语义搜索、文件名搜索")
                BulletPoint("终端命令：执行 shell 命令、编译项目、运行测试")
                BulletPoint("项目管理：初始化项目、创建 Git 仓库、分析项目结构")
                BulletPoint("代码处理：格式化代码、验证 JSON/XML、生成 UUID")
                BulletPoint("系统信息：获取设备信息、存储状态、网络状态")

                SectionTitle("AI 使用示例")

                CodeBlock("""# 文件操作示例
"打开 MainActivity.kt 文件"
"读取 build.gradle 的内容"
"在 app/build.gradle 中添加依赖"
"创建一个新的 Kotlin 文件 Utils.kt"

# 代码搜索示例
"搜索项目中所有的函数定义"
"查找使用了 RecyclerView 的代码"
"列出所有 Kotlin 文件"

# 终端命令示例
"编译当前项目"
"执行 ls -la 命令"
"查看 Git 状态"
"运行项目测试"

# 项目管理示例
"初始化一个新的 Android 项目 MyApp"
"创建 .gitignore 文件"
"分析当前项目结构"

# 系统信息示例
"查看设备信息"
"获取存储空间状态"
"检查网络连接""")

                SectionTitle("编辑器功能")

                BulletPoint("语法高亮支持 Kotlin、Java、XML、JSON、Markdown 等多种语言")
                BulletPoint("支持搜索替换功能（点击编辑器右上角搜索图标）")
                BulletPoint("支持多标签页编辑，点击标签切换文件")
                BulletPoint("支持 Markdown 预览模式，实时查看渲染效果")
                BulletPoint("支持代码折叠、行号显示")

                SectionTitle("开发工具命令")

                CodeBlock("""# 查看菜单
bash /sdcard/Download/mede_ide/android_dev_toolkit.sh menu

# 环境检测
bash /sdcard/Download/mede_ide/android_dev_toolkit.sh check_env

# 列出项目
bash /sdcard/Download/mede_ide/android_dev_toolkit.sh list_projects

# 创建项目
bash /sdcard/Download/mede_ide/android_dev_toolkit.sh create_project MyApp com.example.myapp java

# 编译 Debug
bash /sdcard/Download/mede_ide/android_dev_toolkit.sh build_debug MyApp

# 一键配置
bash /sdcard/Download/mede_ide/android_dev_toolkit.sh quick_setup""")

                SectionTitle("设置别名（推荐）")

                CodeBlock("""echo "alias mede='bash /sdcard/Download/mede_ide/android_dev_toolkit.sh'" >> ~/.bashrc
source ~/.bashrc

# 设置后可以直接使用
mede menu
mede check_env
mede build_debug MyApp""")

                SectionTitle("常见问题")

                BulletPoint("Q: 终端无法输入？")
                BulletPoint("A: 点击终端屏幕区域，键盘会自动弹出")

                BulletPoint("Q: 终端高度占用太多编辑区域？")
                BulletPoint("A: 在设置 → 终端设置中调整终端高度")

                BulletPoint("Q: 命令执行无输出？")
                BulletPoint("A: 终端基于系统 sh，部分命令可能需要安装相关工具")

                BulletPoint("Q: AI 工具调用失败？")
                BulletPoint("A: 确保已打开项目目录，部分工具需要项目上下文")

                BulletPoint("Q: 编译项目失败？")
                BulletPoint("A: 检查 Gradle 配置，确保项目结构正确")

                BulletPoint("Q: 显示乱字符？")
                BulletPoint("A: 更新到最新版本即可")

                SectionTitle("获取帮助")

                BulletPoint("GitHub Issues: https://github.com/Evilgodxu/Mede-IDE/issues")
                BulletPoint("提交新 Issue 时附上：设备型号、Android 版本、错误截图或日志")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun BulletPoint(text: String) {
    Text(
        text = "  -  $text",
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun CodeBlock(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp)
        )
    }
}
