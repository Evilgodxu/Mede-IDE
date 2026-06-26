package com.medeide.jh.screens.home.portrait

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.medeide.jh.screens.home.cloudchat.CloudChatViewModel
import com.medeide.jh.screens.home.portrait.chat.PortraitChatSection
import com.medeide.jh.screens.home.portrait.inputbar.PortraitInputBar
import com.medeide.jh.screens.home.portrait.sidepanel.PortraitDashboardPanel
import com.medeide.jh.screens.home.portrait.sidepanel.PortraitFileBrowserPanel
import com.medeide.jh.screens.home.portrait.sidepanel.PortraitHistoryPanel
import com.medeide.jh.screens.home.portrait.sidepanel.PortraitSettingsPanel
import com.medeide.jh.ui.components.ParticleBackground

@Composable
fun HomePortraitScreen(
    chatViewModel: CloudChatViewModel,
    isHistoryOpen: Boolean = false,
    onHistoryDismiss: () -> Unit = {},
    isDashboardOpen: Boolean = false,
    onDashboardDismiss: () -> Unit = {},
    isFileBrowserOpen: Boolean = false,
    onFileBrowserDismiss: () -> Unit = {},
    isSettingsOpen: Boolean = false,
    onSettingsDismiss: () -> Unit = {},
    onSettingsOpen: () -> Unit = {},
    fileBrowserRoot: String = "",
    onAddToConversation: (String) -> Unit = {},
    onOpenAsProject: (String) -> Unit = {},
    onExitProjectMode: () -> Unit = {},
    isProjectModeActive: Boolean = false,
    userName: String = "江寒",
    agentName: String = "AI",
    userAvatarUri: String = "",
    agentAvatarUri: String = "",
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val panelFraction = 0.80f
    val isPanelOpen = isHistoryOpen || isDashboardOpen || isFileBrowserOpen || isSettingsOpen

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ParticleBackground(Modifier.fillMaxSize())

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            BoxWithConstraints(
                modifier = Modifier.weight(1f),
            ) {
                val panelWidth = maxWidth * panelFraction
                val chatOffset by animateDpAsState(
                    targetValue = when {
                        isHistoryOpen -> panelWidth
                        isDashboardOpen || isFileBrowserOpen || isSettingsOpen -> -panelWidth
                        else -> 0.dp
                    },
                    animationSpec = tween(350),
                    label = "chatOffset",
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(x = chatOffset)
                        .graphicsLayer {
                            rotationY = when {
                                isHistoryOpen -> -3f
                                isDashboardOpen || isFileBrowserOpen || isSettingsOpen -> 3f
                                else -> 0f
                            }
                            cameraDistance = 16f * density.density
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                ) {
                    PortraitChatSection(
                        viewModel = chatViewModel, userName = userName, agentName = agentName,
                        userAvatarUri = userAvatarUri, agentAvatarUri = agentAvatarUri,
                    )
                }

                if (isPanelOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    onHistoryDismiss()
                                    onDashboardDismiss()
                                    onFileBrowserDismiss()
                                    onSettingsDismiss()
                                },
                            ),
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(panelWidth),
                ) {
                    AnimatedVisibility(
                        visible = isHistoryOpen,
                        enter = slideInHorizontally(tween(350)) { -it } + fadeIn(tween(300)),
                        exit = slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(250)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                                .graphicsLayer {
                                    rotationY = 8f
                                    cameraDistance = 16f * density.density
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                },
                        ) {
                            PortraitHistoryPanel(viewModel = chatViewModel)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(panelWidth),
                ) {
                    AnimatedVisibility(
                        visible = isDashboardOpen,
                        enter = slideInHorizontally(tween(350)) { it } + fadeIn(tween(300)),
                        exit = slideOutHorizontally(tween(300)) { it } + fadeOut(tween(250)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                                .graphicsLayer {
                                    rotationY = -8f
                                    cameraDistance = 16f * density.density
                                    transformOrigin = TransformOrigin(1f, 0.5f)
                                },
                        ) {
                            PortraitDashboardPanel()
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(panelWidth),
                ) {
                    AnimatedVisibility(
                        visible = isFileBrowserOpen,
                        enter = slideInHorizontally(tween(350)) { it } + fadeIn(tween(300)),
                        exit = slideOutHorizontally(tween(300)) { it } + fadeOut(tween(250)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                                .graphicsLayer {
                                    rotationY = -8f
                                    cameraDistance = 16f * density.density
                                    transformOrigin = TransformOrigin(1f, 0.5f)
                                },
                        ) {
                            PortraitFileBrowserPanel(
                                rootPath = fileBrowserRoot,
                                onAddToConversation = onAddToConversation,
                                onOpenAsProject = onOpenAsProject,
                                onExitProjectMode = onExitProjectMode,
                                isProjectModeActive = isProjectModeActive,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(panelWidth),
                ) {
                    AnimatedVisibility(
                        visible = isSettingsOpen,
                        enter = slideInHorizontally(tween(350)) { it } + fadeIn(tween(300)),
                        exit = slideOutHorizontally(tween(300)) { it } + fadeOut(tween(250)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                                .graphicsLayer {
                                    rotationY = -8f
                                    cameraDistance = 16f * density.density
                                    transformOrigin = TransformOrigin(1f, 0.5f)
                                },
                        ) {
                            PortraitSettingsPanel(chatViewModel = chatViewModel)
                        }
                    }
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            PortraitInputBar(viewModel = chatViewModel, onOpenSettings = onSettingsOpen)
        }
    }
}
