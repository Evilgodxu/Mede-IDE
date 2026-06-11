package com.template.jh.screens.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val INTRO_PAGES_COUNT = 2
private const val WELCOME_PAGE_INDEX = INTRO_PAGES_COUNT
private const val BASIC_PERMISSIONS_PAGE_INDEX = INTRO_PAGES_COUNT + 1
private const val TOTAL_PAGES_COUNT = INTRO_PAGES_COUNT + 2

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PermissionGuideScreen(
    viewModel: PermissionGuideViewModel = viewModel(),
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { TOTAL_PAGES_COUNT })

    var showPermissionWarning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.checkPermissions(context) }

    // 存储权限请求启动器 (适用于Android 10及以下版本)
    val storagePermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val readGranted =
                    permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
                val writeGranted =
                    permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
                if (readGranted && writeGranted) {
                    viewModel.checkPermissions(context)
                }
            }
        }

    LaunchedEffect(pagerState.currentPage) {
        when (pagerState.currentPage) {
            in 0..WELCOME_PAGE_INDEX ->
                viewModel.setCurrentStep(PermissionGuideViewModel.Step.WELCOME)
            BASIC_PERMISSIONS_PAGE_INDEX ->
                viewModel.setCurrentStep(PermissionGuideViewModel.Step.BASIC_PERMISSIONS)
        }
    }

    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            delay(500)
            onComplete()
        }
    }

    if (showPermissionWarning) {
        AlertDialog(
            onDismissRequest = { showPermissionWarning = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "权限警告",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "缺少必要权限可能会影响应用功能。建议授予所有权限以获得最佳体验。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionWarning = false
                        viewModel.completeGuide()
                    }
                ) { Text("继续") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionWarning = false }) {
                    Text("返回")
                }
            }
        )
    }

    // 横屏布局：左右结构
    Row(
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // 左侧：进度和导航
        Column(
            modifier = Modifier.fillMaxHeight().width(200.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "权限设置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { (pagerState.currentPage + 1).toFloat() / pagerState.pageCount },
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                // 步骤指示器
                StepIndicator(
                    currentStep = pagerState.currentPage,
                    totalSteps = TOTAL_PAGES_COUNT
                )
            }

            // 底部导航按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (pagerState.currentPage > 0) {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    },
                    enabled = pagerState.currentPage > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("上一步")
                }

                if (pagerState.currentPage < TOTAL_PAGES_COUNT - 1) {
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    ) {
                        Text("下一步")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                } else {
                    Button(
                        onClick = {
                            if (uiState.allBasicPermissionsGranted) {
                                viewModel.completeGuide()
                            } else {
                                showPermissionWarning = true
                            }
                        }
                    ) {
                        Text("完成")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
            }
        }

        // 右侧：内容区域
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) { page ->
            when (page) {
                0 ->
                    IntroductionPage(
                        title = "欢迎使用",
                        description = "本应用需要一些权限才能正常工作。接下来将引导您完成权限设置。",
                        pageIndex = 0
                    )
                1 ->
                    IntroductionPage(
                        title = "权限说明",
                        description = "我们需要存储权限来管理文件，以及电池优化豁免权限以确保应用在后台正常运行。",
                        pageIndex = 1
                    )
                WELCOME_PAGE_INDEX -> WelcomePage()
                BASIC_PERMISSIONS_PAGE_INDEX ->
                    BasicPermissionsPage(
                        hasStoragePermission = uiState.hasStoragePermission,
                        hasBatteryOptimizationExemption = uiState.hasBatteryOptimizationExemption,
                        onStoragePermissionClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val intent =
                                        Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                        )
                                            .apply {
                                                data =
                                                    Uri.parse(
                                                        "package:${context.packageName}"
                                                    )
                                            }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent =
                                            Intent(
                                                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                            )
                                        context.startActivity(intent)
                                    } catch (e2: Exception) {
                                        Toast.makeText(
                                            context,
                                            "无法打开存储权限设置",
                                            Toast.LENGTH_SHORT
                                        )
                                            .show()
                                    }
                                }
                            } else {
                                storagePermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                )
                            }
                        },
                        onBatteryOptimizationClick = {
                            try {
                                // 直接请求忽略电池优化，无需用户搜索应用
                                val intent =
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                    )
                                        .apply {
                                            data =
                                                Uri.parse(
                                                    "package:" +
                                                        context.packageName
                                                )
                                        }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // 如果直接请求失败，尝试打开电池优化设置页面
                                try {
                                    val intent =
                                        Intent(
                                            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                        )
                                    context.startActivity(intent)
                                    Toast.makeText(
                                        context,
                                        "请在列表中找到应用并设置为\"不优化\"",
                                        Toast.LENGTH_LONG
                                    )
                                        .show()
                                } catch (e2: Exception) {
                                    // 最后回退到应用详情页面
                                    try {
                                        val intent =
                                            Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.parse("package:" + context.packageName)
                                            )
                                        context.startActivity(intent)
                                        Toast.makeText(
                                            context,
                                            "请在应用详情中找到电池选项并设置为\"无限制\"",
                                            Toast.LENGTH_LONG
                                        )
                                            .show()
                                    } catch (e3: Exception) {
                                        Toast.makeText(
                                            context,
                                            "无法打开电池优化设置，请手动设置",
                                            Toast.LENGTH_SHORT
                                        )
                                            .show()
                                    }
                                }
                            }
                        },
                        onRefresh = { viewModel.checkPermissions(context) }
                    )
            }
        }
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    totalSteps: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(totalSteps) { index ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier =
                        Modifier.size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    index == currentStep -> MaterialTheme.colorScheme.primary
                                    index < currentStep -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                    contentAlignment = Alignment.Center
                ) {
                    if (index < currentStep) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (index == currentStep)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = when (index) {
                        0 -> "欢迎"
                        1 -> "说明"
                        2 -> "开始"
                        3 -> "权限设置"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (index == currentStep)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IntroductionPage(
    title: String,
    description: String,
    pageIndex: Int
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(500),
        label = "scale"
    )

    Row(
        modifier =
            Modifier.fillMaxSize()
                .padding(24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(48.dp)
    ) {
        Box(
            modifier =
                Modifier.size(160.dp)
                    .clip(RoundedCornerShape(80.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (pageIndex == 0) Icons.Default.Storage else Icons.Default.BatteryFull,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WelcomePage() {
    Row(
        modifier =
            Modifier.fillMaxSize()
                .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(48.dp)
    ) {
        Box(
            modifier =
                Modifier.size(140.dp)
                    .clip(RoundedCornerShape(70.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(70.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "权限设置",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "请点击下一步开始配置应用所需权限",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BasicPermissionsPage(
    hasStoragePermission: Boolean,
    hasBatteryOptimizationExemption: Boolean,
    onStoragePermissionClick: () -> Unit,
    onBatteryOptimizationClick: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(16.dp)
    ) {
        Text(
            text = "基本权限",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "请授予以下权限以确保应用正常工作",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 横屏：权限卡片并排显示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PermissionCard(
                title = "存储权限",
                description = "用于访问和管理设备上的文件",
                isGranted = hasStoragePermission,
                onClick = onStoragePermissionClick,
                icon = Icons.Default.Storage,
                modifier = Modifier.weight(1f)
            )

            PermissionCard(
                title = "电池优化豁免",
                description = "允许应用在后台持续运行",
                isGranted = hasBatteryOptimizationExemption,
                onClick = onBatteryOptimizationClick,
                icon = Icons.Default.BatteryFull,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.width(200.dp)
        ) {
            Text("刷新权限状态")
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier =
            modifier
                .clickable(enabled = !isGranted, onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isGranted) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface
            ),
        border =
            if (isGranted) null
            else
                androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline
                )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier =
                    Modifier.size(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            if (isGranted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint =
                        if (isGranted) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isGranted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "已授予",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "已授予",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Button(onClick = onClick) {
                    Text("授予权限")
                }
            }
        }
    }
}
