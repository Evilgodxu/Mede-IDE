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
                BulletPoint("选择目录作为项目根目录")
                BulletPoint("点击文件即可在编辑器中打开")

                SectionTitle("内置终端")

                BulletPoint("点击左侧边栏的 Terminal 图标打开底部终端面板")
                BulletPoint("点击终端屏幕即可弹出键盘进行输入")
                BulletPoint("终端基于 Termux 源码构建，支持大多数 Linux 命令")
                BulletPoint("再次点击 Terminal 图标或右上角关闭按钮关闭终端")
                BulletPoint("在设置中可以调整终端高度（范围 100-500dp）")

                SectionTitle("AI 助手")

                BulletPoint("右侧面板是 AI 对话区域")
                BulletPoint("可以让 AI 帮你读写文件、执行命令、搜索代码")
                BulletPoint("支持本地模型和云端 API")
                BulletPoint("点击顶部模型选择器切换模型")

                SectionTitle("编辑器功能")

                BulletPoint("语法高亮支持多种编程语言")
                BulletPoint("支持搜索替换（Ctrl+F）")
                BulletPoint("支持多标签页编辑")
                BulletPoint("支持 Markdown 预览模式")

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
