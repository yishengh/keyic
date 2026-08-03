package com.yishenghuang.keyic.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.ui.adaptive.AdaptiveContentWidth
import com.yishenghuang.keyic.ui.components.GlassSurface
import com.yishenghuang.keyic.ui.components.KeyicBackdrop
import com.yishenghuang.keyic.ui.theme.glass
import kotlinx.coroutines.launch

private data class OnboardingPage(
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
    val visual: OnboardingVisual,
)

@Composable
fun OnboardingPager(
    onFinished: () -> Unit,
) {
    val pages = listOf(
        OnboardingPage(
            R.string.onboarding_title_1,
            R.string.onboarding_body_1,
            OnboardingVisual.OfflineVault,
        ),
        OnboardingPage(
            R.string.onboarding_title_2,
            R.string.onboarding_body_2,
            OnboardingVisual.SmartCapture,
        ),
        OnboardingPage(
            R.string.onboarding_title_3,
            R.string.onboarding_body_3,
            OnboardingVisual.AutofillAuth,
        ),
        OnboardingPage(
            R.string.onboarding_title_4,
            R.string.onboarding_body_4,
            OnboardingVisual.BackupSync,
        ),
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    KeyicBackdrop(modifier = Modifier.fillMaxSize()) {
        AdaptiveContentWidth(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onFinished) {
                        Text(stringResource(R.string.action_skip))
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { page ->
                    val item = pages[page]
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        strong = true,
                        contentPadding = PaddingValues(20.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            OnboardingIllustration(visual = item.visual)
                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = stringResource(item.title),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(item.body),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PageDots(
                        pageCount = pages.size,
                        currentPage = pagerState.currentPage,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (pagerState.currentPage < pages.lastIndex) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                onFinished()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            if (pagerState.currentPage < pages.lastIndex) {
                                stringResource(R.string.action_next)
                            } else {
                                stringResource(R.string.onboarding_get_started)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(
    pageCount: Int,
    currentPage: Int,
) {
    val glass = MaterialTheme.glass
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (selected) 22.dp else 8.dp,
                label = "dotWidth",
            )
            val color by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    glass.stroke
                },
                label = "dotColor",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
