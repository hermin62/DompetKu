package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InsightPeriod
import com.example.data.WeeklySpendingMetrics
import com.example.ui.theme.GreenIncome
import com.example.ui.theme.PurpleWants
import com.example.ui.theme.RedExpense
import com.example.ui.viewmodel.PartialStreamingInsight
import com.example.ui.viewmodel.WeeklyInsightUiState
import com.example.util.CurrencyHelper
import com.example.util.DateUtils
import java.util.Locale
import kotlin.math.abs

@Composable
fun WeeklyGeminiInsightCard(
    metrics: WeeklySpendingMetrics,
    uiState: WeeklyInsightUiState,
    selectedPeriod: InsightPeriod = metrics.period,
    onSelectPeriod: (InsightPeriod) -> Unit = {},
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDetails by remember { mutableStateOf(true) }
    val isLoading = uiState is WeeklyInsightUiState.Loading ||
        uiState is WeeklyInsightUiState.Idle ||
        (uiState is WeeklyInsightUiState.Success && uiState.isRefreshing)

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val isMonthly = selectedPeriod == InsightPeriod.MONTHLY

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_insight_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, primary.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Accent Gradient Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.12f),
                                tertiary.copy(alpha = 0.10f)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Analisis Keuangan AI",
                                tint = primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isMonthly) "Ringkasan Bulanan AI" else "Ringkasan Mingguan AI",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isMonthly) {
                                    "Bulan Berjalan (${metrics.periodLabel})"
                                } else {
                                    "7 Hari Terakhir (${metrics.periodLabel})"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (uiState is WeeklyInsightUiState.Success) {
                            SpendingStatusBadge(status = uiState.insight.spendingStatus)
                            Spacer(modifier = Modifier.width(4.dp))
                        } else if (uiState is WeeklyInsightUiState.Loading && uiState.partial?.spendingStatus != null) {
                            SpendingStatusBadge(status = uiState.partial.spendingStatus)
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        IconButton(
                            onClick = onRefresh,
                            enabled = !isLoading,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("refresh_weekly_insight_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Perbarui Ringkasan AI",
                                tint = if (isLoading) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                } else {
                                    primary
                                }
                            )
                        }
                    }

                    // Period Selector Switcher: Mingguan vs Bulanan
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PeriodSwitchChip(
                                label = "Mingguan (7 Hari)",
                                selected = selectedPeriod == InsightPeriod.WEEKLY,
                                onClick = { onSelectPeriod(InsightPeriod.WEEKLY) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_period_weekly_btn")
                            )
                            PeriodSwitchChip(
                                label = "Bulanan (Bulan Ini)",
                                selected = selectedPeriod == InsightPeriod.MONTHLY,
                                onClick = { onSelectPeriod(InsightPeriod.MONTHLY) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_period_monthly_btn")
                            )
                        }
                    }
                }
            }

            if (uiState is WeeklyInsightUiState.Success && uiState.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = primary
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Deterministic Metrics Summary Strip (adapts to Weekly vs Monthly)
                WeeklyMetricsStrip(metrics = metrics)

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Reactive Gemini State Content with Skeleton Loader & Live Streaming Response
                when (uiState) {
                    is WeeklyInsightUiState.Idle,
                    is WeeklyInsightUiState.Loading -> {
                        val loadingState = uiState as? WeeklyInsightUiState.Loading
                        AiInsightSkeletonAndStreamLoader(
                            statusLabel = loadingState?.statusLabel ?: "Menghubungkan ke Gemini AI...",
                            partial = loadingState?.partial,
                            accentColor = primary
                        )
                    }

                    is WeeklyInsightUiState.Error -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("weekly_insight_error")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Analisis AI gagal dimuat",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Text(
                                    text = uiState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                OutlinedButton(
                                    onClick = onRefresh,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("retry_weekly_insight_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Coba Lagi")
                                }
                            }
                        }
                    }

                    is WeeklyInsightUiState.Success -> {
                        val insight = uiState.insight
                        val livePartial = if (uiState.isRefreshing) uiState.streamingPartial else null
                        val activeHeadline = livePartial?.headline ?: insight.headline
                        val activeSummary = livePartial?.summaryParagraph ?: insight.summaryParagraph
                        val activeInsights = if (!livePartial?.keyInsights.isNullOrEmpty()) livePartial!!.keyInsights else insight.keyInsights
                        val activeTips = if (!livePartial?.actionableTips.isNullOrEmpty()) livePartial!!.actionableTips else insight.actionableTips

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("weekly_insight_success"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeHeadline,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (uiState.isRefreshing) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = primary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = if ((livePartial?.receivedChars ?: 0) > 0) "Streaming..." else "Memperbarui...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = primary,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = activeSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 19.sp
                            )

                            TextButton(
                                onClick = { showDetails = !showDetails },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("toggle_weekly_insight_details_btn")
                            ) {
                                Text(
                                    text = if (showDetails) "Sembunyikan Detail Insight" else "Lihat Detail Insight & Saran",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showDetails) "Sembunyikan detail" else "Tampilkan detail",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            AnimatedVisibility(visible = showDetails) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (metrics.topCategories.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Kategori Pengeluaran Terbesar",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                metrics.topCategories.take(3).forEach { cat ->
                                                    val catColor = remember(cat.colorHex) {
                                                        try {
                                                            Color(android.graphics.Color.parseColor(cat.colorHex))
                                                        } catch (_: Exception) {
                                                            primary
                                                        }
                                                    }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .clip(CircleShape)
                                                                .background(catColor)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = cat.categoryName,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Text(
                                                            text = "${CurrencyHelper.format(cat.amount, metrics.currencyCode)} (${String.format(Locale.US, "%.0f%%", cat.sharePercent)})",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (activeInsights.isNotEmpty()) {
                                        InsightSectionBlock(
                                            title = "Insight Pengeluaran",
                                            items = activeInsights,
                                            icon = Icons.Default.Insights,
                                            tint = primary
                                        )
                                    }

                                    if (activeTips.isNotEmpty()) {
                                        InsightSectionBlock(
                                            title = if (isMonthly) "Saran Bulan Depan" else "Saran Minggu Depan",
                                            items = activeTips,
                                            icon = Icons.Default.Lightbulb,
                                            tint = Color(0xFFD97706)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Dianalisis otomatis oleh Gemini",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        )
                                        Text(
                                            text = DateUtils.formatTime(insight.generatedAtMillis),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodSwitchChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AiInsightSkeletonAndStreamLoader(
    statusLabel: String,
    partial: PartialStreamingInsight?,
    accentColor: Color
) {
    val transition = rememberInfiniteTransition(label = "ai_skeleton_shimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )

    val baseColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(shift - 320f, 0f),
        end = Offset(shift, 120f)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weekly_insight_loading"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = accentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if ((partial?.receivedChars ?: 0) > 0) {
                    "Streaming respons Gemini (${partial?.receivedChars} karakter)..."
                } else {
                    statusLabel
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accentColor
            )
        }

        // Headline: streamed live or shimmer skeleton bar
        val liveHeadline = partial?.headline
        if (!liveHeadline.isNullOrBlank()) {
            Text(
                text = "$liveHeadline ▍",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmerBrush)
            )
        }

        // Summary paragraph: streamed live or multi-line shimmer skeleton
        val liveSummary = partial?.summaryParagraph
        if (!liveSummary.isNullOrBlank()) {
            Text(
                text = "$liveSummary ▍",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(13.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(13.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(13.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
            }
        }

        // Key insights: streamed live or skeleton bullet rows
        val liveInsights = partial?.keyInsights.orEmpty()
        if (liveInsights.isNotEmpty()) {
            InsightSectionBlock(
                title = "Insight Pengeluaran",
                items = liveInsights,
                icon = Icons.Default.Insights,
                tint = accentColor
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.42f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.80f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
            }
        }

        // Actionable tips: streamed live or skeleton bars
        val liveTips = partial?.actionableTips.orEmpty()
        if (liveTips.isNotEmpty()) {
            InsightSectionBlock(
                title = "Saran Praktis",
                items = liveTips,
                icon = Icons.Default.Lightbulb,
                tint = Color(0xFFD97706)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.74f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmerBrush)
            )
        }
    }
}

@Composable
private fun WeeklyMetricsStrip(metrics: WeeklySpendingMetrics) {
    val isMonthly = metrics.period == InsightPeriod.MONTHLY
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = if (isMonthly) "Keluar Bulan Ini" else "Keluar 7 Hari",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = CurrencyHelper.format(metrics.weekExpense, metrics.currencyCode),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    metrics.expenseChangePercent?.let { pct ->
                        val isIncrease = pct > 0.0
                        val deltaColor = if (isIncrease) RedExpense else GreenIncome
                        val prefix = if (isIncrease) "▲ +" else "▼ -"
                        val compareLabel = if (isMonthly) "vs bulan lalu" else "vs minggu lalu"
                        Text(
                            text = String.format(Locale.US, "%s%.0f%% %s", prefix, abs(pct), compareLabel),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = deltaColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } ?: Text(
                        text = "${metrics.expenseTransactionCount} transaksi keluar",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Rata-rata / Hari",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = CurrencyHelper.format(metrics.dailyAverageExpense, metrics.currencyCode),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Masuk: ${CurrencyHelper.format(metrics.weekIncome, metrics.currencyCode)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = GreenIncome,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (metrics.weekExpense > 0.0) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Kebutuhan ${String.format(Locale.US, "%.0f%%", metrics.needsPercent)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Keinginan ${String.format(Locale.US, "%.0f%%", metrics.wantsPercent)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = PurpleWants,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                ) {
                    val needsWeight = (metrics.needsPercent / 100f).coerceIn(0f, 1f)
                    val wantsWeight = (metrics.wantsPercent / 100f).coerceIn(0f, 1f)
                    if (needsWeight > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(needsWeight)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    if (wantsWeight > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(wantsWeight)
                                .fillMaxHeight()
                                .background(PurpleWants)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingStatusBadge(status: String) {
    val (bg, fg, label) = when (status.uppercase(Locale.ROOT)) {
        "HEMAT" -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), "Hemat")
        "TERKENDALI" -> Triple(Color(0xFFCCFBF1), Color(0xFF0F766E), "Terkendali")
        "WASPADA" -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Waspada")
        "BOROS" -> Triple(Color(0xFFFEE2E2), Color(0xFFB91C1C), "Boros")
        else -> Triple(Color(0xFFCCFBF1), Color(0xFF0F766E), status)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun InsightSectionBlock(
    title: String,
    items: List<String>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        items.forEach { bullet ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = bullet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
