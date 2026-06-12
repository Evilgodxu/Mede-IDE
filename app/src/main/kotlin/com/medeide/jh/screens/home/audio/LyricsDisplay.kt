package com.medeide.jh.screens.home.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 歌词同步显示组件
 * 参考 Echo-Music 的 Lyrics.kt 核心同步逻辑
 */
@Composable
fun LyricsDisplay(
    lyrics: List<LyricLine>,
    currentLineIndex: Int,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // 自动滚动到当前歌词行
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex > 0 && currentLineIndex < lyrics.size) {
            listState.animateScrollToItem(currentLineIndex - 1) // 让当前行居中
        }
    }

    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "未找到歌词",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(lyrics, key = { i, _ -> i }) { index, line ->
            val isCurrent = index == currentLineIndex
            Text(
                text = line.text,
                fontSize = if (isCurrent) 18.sp else 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                maxLines = 2,
            )
        }
    }
}
