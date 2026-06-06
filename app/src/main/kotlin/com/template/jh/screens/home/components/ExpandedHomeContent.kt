package com.template.jh.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.template.jh.R

// 扩展布局内容（平板、横屏）
@Composable
fun ExpandedHomeContent(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(24.dp)
            .fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            WelcomeCard(modifier = Modifier.fillMaxWidth())
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            FeatureCard(
                title = stringResource(R.string.home_feature_1),
                description = stringResource(R.string.home_feature_1_desc),
                modifier = Modifier.fillMaxWidth()
            )

            FeatureCard(
                title = stringResource(R.string.home_feature_2),
                description = stringResource(R.string.home_feature_2_desc),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
