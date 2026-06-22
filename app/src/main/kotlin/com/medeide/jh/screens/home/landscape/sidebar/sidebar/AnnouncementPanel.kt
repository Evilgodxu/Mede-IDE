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

                SectionTitle("环境配置")

                BulletPoint("从 F-Droid 下载 Termux：https://f-droid.org/packages/com.termux/")
                BulletPoint("配置外部应用权限：")
                CodeBlock("""mkdir -p ~/.termux
echo 'allow-external-apps=true' >> ~/.termux/termux.properties
termux-reload-settings""")
                BulletPoint("安装开发工具（可选）：")
                CodeBlock("pkg install openjdk-17 gradle wget unzip aapt2")

                SectionTitle("终端使用")

                BulletPoint("点击侧边栏的「终端」图标打开终端面板")
                BulletPoint("在底部输入框输入命令，点击发送按钮或按回车执行")
                BulletPoint("命令输出会在 2 秒后显示在终端窗口中")
                BulletPoint("如需转发到 Termux 执行，点击输入框右侧的绿色跳转按钮")

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

                BulletPoint("Q: 终端显示「需要 Termux」但已安装？")
                BulletPoint("A: 确认从 F-Droid 安装（不是 Google Play），然后点击终端右上角的刷新按钮重新检测")

                BulletPoint("Q: 命令执行无输出？")
                BulletPoint("A: 在 Termux 中配置 allow-external-apps=true 并重启 Termux 和应用")

                BulletPoint("Q: 提示「No such file or directory」？")
                BulletPoint("A: 脚本不在当前目录，必须使用完整路径 /sdcard/Download/mede_ide/android_dev_toolkit.sh")

                BulletPoint("Q: 显示乱字符？")
                BulletPoint("A: 更新到最新版本即可，特殊字符已替换为终端可识别的 ASCII 字符")

                SectionTitle("获取帮助")

                BulletPoint("GitHub Issues: https://github.com/Evilgodxu/Mede-IDE/issues")
                BulletPoint("提交新 Issue 时附上：设备型号、Android 版本、Termux 版本、错误截图或日志")
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
