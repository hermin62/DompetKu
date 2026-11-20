package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ExpenseGroup
import com.example.data.InsightPeriod
import com.example.data.WeeklySpendingMetrics
import com.example.util.CurrencyHelper
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private const val TAG = "WeeklyInsightViewModel"
private const val GEMINI_MODEL_NAME = "gemini-3.5-flash-lite"
private const val INSIGHT_CACHE_PREFS = "dompetku_weekly_insight_cache"

data class WeeklyGeminiInsight(
    val headline: String,
    val spendingStatus: String,
    val summaryParagraph: String,
    val keyInsights: List<String>,
    val actionableTips: List<String>,
    val generatedAtMillis: Long
)

data class PartialStreamingInsight(
    val spendingStatus: String? = null,
    val headline: String? = null,
    val summaryParagraph: String? = null,
    val keyInsights: List<String> = emptyList(),
    val actionableTips: List<String> = emptyList(),
    val receivedChars: Int = 0
)

sealed interface WeeklyInsightUiState {
    data object Idle : WeeklyInsightUiState
    data class Loading(
        val metrics: WeeklySpendingMetrics,
        val statusLabel: String = "Menghubungkan ke Gemini AI...",
        val partial: PartialStreamingInsight? = null
    ) : WeeklyInsightUiState
    data class Success(
        val metrics: WeeklySpendingMetrics,
        val insight: WeeklyGeminiInsight,
        val isRefreshing: Boolean = false,
        val streamingPartial: PartialStreamingInsight? = null
    ) : WeeklyInsightUiState
    data class Error(
        val metrics: WeeklySpendingMetrics,
        val message: String
    ) : WeeklyInsightUiState
}

class WeeklyInsightViewModel(application: Application) : AndroidViewModel(application) {

    private val cachePrefs by lazy {
        getApplication<Application>().getSharedPreferences(INSIGHT_CACHE_PREFS, Context.MODE_PRIVATE)
    }

    private val insightSchema = Schema.obj(
        properties = mapOf(
            "headline" to Schema.string(
                description = "Judul singkat ringkasan keuangan periode ini (Bahasa Indonesia, maks 8 kata)."
            ),
            "spendingStatus" to Schema.enumeration(
                values = listOf("HEMAT", "TERKENDALI", "WASPADA", "BOROS"),
                description = "Status evaluasi pengeluaran pada periode terkait."
            ),
            "summaryParagraph" to Schema.string(
                description = "Ringkasan padat 2 kalimat dalam Bahasa Indonesia terkait total keluar periode ini, tren dibanding periode sebelumnya, dan keseimbangan Kebutuhan vs Keinginan."
            ),
            "keyInsights" to Schema.array(
                items = Schema.string(),
                description = "2 poin insight tajam berbasis angka aktual transaksi periode ini."
            ),
            "actionableTips" to Schema.array(
                items = Schema.string(),
                description = "2 saran praktis untuk periode berikutnya."
            )
        )
    )

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = GEMINI_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = insightSchema
                temperature = 0.35f
                maxOutputTokens = 480
            }
        )
    }

    private val _selectedPeriod = MutableStateFlow(InsightPeriod.WEEKLY)
    val selectedPeriod: StateFlow<InsightPeriod> = _selectedPeriod.asStateFlow()

    private val _weeklyState = MutableStateFlow<WeeklyInsightUiState>(WeeklyInsightUiState.Idle)
    val weeklyState: StateFlow<WeeklyInsightUiState> = _weeklyState.asStateFlow()

    private val _monthlyState = MutableStateFlow<WeeklyInsightUiState>(WeeklyInsightUiState.Idle)
    val monthlyState: StateFlow<WeeklyInsightUiState> = _monthlyState.asStateFlow()

    // Backwards-compatible alias pointing to weeklyState
    val uiState: StateFlow<WeeklyInsightUiState> = _weeklyState.asStateFlow()

    private val insightCache = mutableMapOf<String, Pair<String, WeeklyGeminiInsight>>()
    private val activeJobs = mutableMapOf<InsightPeriod, Job?>()
    private val inFlightSignatures = mutableMapOf<InsightPeriod, String?>()

    fun setSelectedPeriod(period: InsightPeriod) {
        _selectedPeriod.value = period
    }

    fun resetForAccountChange() {
        activeJobs.values.forEach { it?.cancel() }
        activeJobs.clear()
        inFlightSignatures.clear()
        _weeklyState.value = WeeklyInsightUiState.Idle
        _monthlyState.value = WeeklyInsightUiState.Idle
    }

    fun analyzeWeeklyFinances(
        accountKey: String,
        metrics: WeeklySpendingMetrics,
        forceRefresh: Boolean = false
    ) {
        analyzePeriodFinances(
            accountKey = accountKey,
            metrics = metrics,
            forceRefresh = forceRefresh
        )
    }

    fun analyzePeriodFinances(
        accountKey: String,
        metrics: WeeklySpendingMetrics,
        forceRefresh: Boolean = false
    ) {
        if (accountKey.isBlank()) return

        val period = metrics.period
        val targetStateFlow = if (period == InsightPeriod.MONTHLY) _monthlyState else _weeklyState
        val compositeKey = "${accountKey.trim().lowercase()}::${period.name}"

        val memCached = insightCache[compositeKey]
        val diskCached = if (memCached == null) loadDiskCache(compositeKey)?.also {
            insightCache[compositeKey] = it
        } else memCached

        if (!forceRefresh && diskCached != null && diskCached.first == metrics.dataSignature) {
            targetStateFlow.value = WeeklyInsightUiState.Success(metrics, diskCached.second, isRefreshing = false)
            return
        }

        val targetSig = "$compositeKey::${metrics.dataSignature}"
        if (!forceRefresh && inFlightSignatures[period] == targetSig && activeJobs[period]?.isActive == true) {
            return
        }

        val previousInsight = diskCached?.second ?: (targetStateFlow.value as? WeeklyInsightUiState.Success)?.insight

        activeJobs[period]?.cancel()
        inFlightSignatures[period] = targetSig

        activeJobs[period] = viewModelScope.launch {
            if (previousInsight != null) {
                targetStateFlow.value = WeeklyInsightUiState.Success(
                    metrics = metrics,
                    insight = previousInsight,
                    isRefreshing = true,
                    streamingPartial = PartialStreamingInsight()
                )
            } else {
                targetStateFlow.value = WeeklyInsightUiState.Loading(
                    metrics = metrics,
                    statusLabel = if (period == InsightPeriod.MONTHLY) {
                        "Menyiapkan analisis bulanan AI..."
                    } else {
                        "Menyiapkan analisis mingguan AI..."
                    },
                    partial = PartialStreamingInsight()
                )
            }

            if (!forceRefresh && previousInsight != null) {
                delay(550L)
            }

            try {
                val prompt = buildCompactPrompt(metrics)
                val responseBuilder = StringBuilder()

                withTimeout(30_000L) {
                    model.generateContentStream(prompt).collect { chunk ->
                        val part = chunk.text
                        if (!part.isNullOrEmpty()) {
                            responseBuilder.append(part)
                            val partial = parsePartialStream(responseBuilder.toString())
                            if (previousInsight != null) {
                                targetStateFlow.value = WeeklyInsightUiState.Success(
                                    metrics = metrics,
                                    insight = previousInsight,
                                    isRefreshing = true,
                                    streamingPartial = partial
                                )
                            } else {
                                targetStateFlow.value = WeeklyInsightUiState.Loading(
                                    metrics = metrics,
                                    statusLabel = "Menerima respons Gemini secara live...",
                                    partial = partial
                                )
                            }
                        }
                    }
                }

                val rawText = cleanJsonPayload(responseBuilder.toString())
                if (rawText.isBlank()) {
                    handleFailure(
                        targetStateFlow = targetStateFlow,
                        metrics = metrics,
                        previousInsight = previousInsight,
                        forceRefresh = forceRefresh,
                        errorMessage = "Model Gemini mengembalikan respons kosong. Silakan coba lagi."
                    )
                    return@launch
                }

                val parsed = parseStructuredInsight(rawText)
                insightCache[compositeKey] = metrics.dataSignature to parsed
                saveDiskCache(compositeKey, metrics.dataSignature, parsed)
                targetStateFlow.value = WeeklyInsightUiState.Success(metrics, parsed, isRefreshing = false, streamingPartial = null)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini insight timed out for $period", e)
                handleFailure(
                    targetStateFlow = targetStateFlow,
                    metrics = metrics,
                    previousInsight = previousInsight,
                    forceRefresh = forceRefresh,
                    errorMessage = "Analisis Gemini melewati batas waktu. Periksa koneksi internet dan coba lagi."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate Gemini financial summary for $period", e)
                handleFailure(
                    targetStateFlow = targetStateFlow,
                    metrics = metrics,
                    previousInsight = previousInsight,
                    forceRefresh = forceRefresh,
                    errorMessage = e.localizedMessage ?: "Gagal menghasilkan ringkasan keuangan AI."
                )
            } finally {
                if (inFlightSignatures[period] == targetSig) {
                    inFlightSignatures[period] = null
                }
            }
        }
    }

    private fun handleFailure(
        targetStateFlow: MutableStateFlow<WeeklyInsightUiState>,
        metrics: WeeklySpendingMetrics,
        previousInsight: WeeklyGeminiInsight?,
        forceRefresh: Boolean,
        errorMessage: String
    ) {
        if (previousInsight != null && !forceRefresh) {
            targetStateFlow.value = WeeklyInsightUiState.Success(
                metrics = metrics,
                insight = previousInsight,
                isRefreshing = false,
                streamingPartial = null
            )
        } else {
            targetStateFlow.value = WeeklyInsightUiState.Error(
                metrics = metrics,
                message = errorMessage
            )
        }
    }

    private fun parsePartialStream(rawStream: String): PartialStreamingInsight {
        val cleaned = cleanJsonPayload(rawStream)
        return PartialStreamingInsight(
            spendingStatus = extractPartialStringField(cleaned, "spendingStatus"),
            headline = extractPartialStringField(cleaned, "headline"),
            summaryParagraph = extractPartialStringField(cleaned, "summaryParagraph"),
            keyInsights = extractPartialArrayStrings(cleaned, "keyInsights"),
            actionableTips = extractPartialArrayStrings(cleaned, "actionableTips"),
            receivedChars = cleaned.length
        )
    }

    private fun extractPartialStringField(json: String, fieldName: String): String? {
        val regex = Regex("\"$fieldName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"?")
        val match = regex.find(json) ?: return null
        val rawValue = match.groupValues.getOrNull(1) ?: return null
        return unescapeJsonString(rawValue).takeIf { it.isNotBlank() }
    }

    private fun extractPartialArrayStrings(json: String, fieldName: String): List<String> {
        val startMatch = Regex("\"$fieldName\"\\s*:\\s*\\[").find(json) ?: return emptyList()
        val tail = json.substring(startMatch.range.last + 1)
        val endBracketIdx = tail.indexOf(']')
        val slice = if (endBracketIdx >= 0) tail.substring(0, endBracketIdx) else tail
        val itemRegex = Regex("\"((?:\\\\.|[^\"\\\\])+)\"")
        return itemRegex.findAll(slice)
            .mapNotNull { m -> unescapeJsonString(m.groupValues[1]).takeIf { it.isNotBlank() } }
            .toList()
    }

    private fun unescapeJsonString(s: String): String {
        return s.replace("\\n", " ")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .trim()
    }

    private fun cleanJsonPayload(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun parseStructuredInsight(jsonString: String): WeeklyGeminiInsight {
        val json = JSONObject(jsonString)
        val rawStatus = json.optString("spendingStatus", "TERKENDALI").uppercase(Locale.ROOT).trim()
        val validStatus = when (rawStatus) {
            "HEMAT", "TERKENDALI", "WASPADA", "BOROS" -> rawStatus
            else -> "TERKENDALI"
        }

        val headline = json.optString("headline").ifBlank {
            "Ringkasan Pengeluaran"
        }
        val summaryParagraph = json.optString("summaryParagraph").ifBlank {
            throw IllegalStateException("Respons ringkasan dari Gemini tidak lengkap.")
        }

        val insightsArray = json.optJSONArray("keyInsights")
        val keyInsights = if (insightsArray != null) {
            (0 until insightsArray.length())
                .mapNotNull { idx -> insightsArray.optString(idx).takeIf { it.isNotBlank() } }
        } else emptyList()

        val tipsArray = json.optJSONArray("actionableTips")
        val actionableTips = if (tipsArray != null) {
            (0 until tipsArray.length())
                .mapNotNull { idx -> tipsArray.optString(idx).takeIf { it.isNotBlank() } }
        } else emptyList()

        return WeeklyGeminiInsight(
            headline = headline,
            spendingStatus = validStatus,
            summaryParagraph = summaryParagraph,
            keyInsights = keyInsights,
            actionableTips = actionableTips,
            generatedAtMillis = System.currentTimeMillis()
        )
    }

    private fun cacheKeyFor(compositeKey: String): String {
        return "insight_${compositeKey.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "_")}"
    }

    private fun saveDiskCache(
        compositeKey: String,
        signature: String,
        insight: WeeklyGeminiInsight
    ) {
        runCatching {
            val payload = JSONObject().apply {
                put("signature", signature)
                put("headline", insight.headline)
                put("spendingStatus", insight.spendingStatus)
                put("summaryParagraph", insight.summaryParagraph)
                put("generatedAtMillis", insight.generatedAtMillis)
                put("keyInsights", JSONArray(insight.keyInsights))
                put("actionableTips", JSONArray(insight.actionableTips))
            }
            cachePrefs.edit().putString(cacheKeyFor(compositeKey), payload.toString()).apply()
        }
    }

    private fun loadDiskCache(compositeKey: String): Pair<String, WeeklyGeminiInsight>? {
        return runCatching {
            val raw = cachePrefs.getString(cacheKeyFor(compositeKey), null) ?: return null
            val json = JSONObject(raw)
            val sig = json.optString("signature", "").takeIf { it.isNotBlank() } ?: return null
            val summary = json.optString("summaryParagraph", "").takeIf { it.isNotBlank() } ?: return null
            val keyInsightsArr = json.optJSONArray("keyInsights")
            val tipsArr = json.optJSONArray("actionableTips")
            val insight = WeeklyGeminiInsight(
                headline = json.optString("headline", "Ringkasan Keuangan"),
                spendingStatus = json.optString("spendingStatus", "TERKENDALI"),
                summaryParagraph = summary,
                keyInsights = if (keyInsightsArr != null) {
                    (0 until keyInsightsArr.length()).mapNotNull { keyInsightsArr.optString(it).takeIf(String::isNotBlank) }
                } else emptyList(),
                actionableTips = if (tipsArr != null) {
                    (0 until tipsArr.length()).mapNotNull { tipsArr.optString(it).takeIf(String::isNotBlank) }
                } else emptyList(),
                generatedAtMillis = json.optLong("generatedAtMillis", System.currentTimeMillis())
            )
            sig to insight
        }.getOrNull()
    }

    private fun buildCompactPrompt(metrics: WeeklySpendingMetrics): String {
        val c = metrics.currencyCode
        val isMonthly = metrics.period == InsightPeriod.MONTHLY
        val periodName = if (isMonthly) "bulanan" else "mingguan (7 hari)"
        val prevLabel = if (isMonthly) "bulan lalu" else "minggu lalu"

        val trend = metrics.expenseChangePercent?.let { d ->
            String.format(Locale.US, "%+.0f%% vs %s (%s)", d, prevLabel, CurrencyHelper.format(metrics.prevWeekExpense, c))
        } ?: "belum ada data pembanding"

        val topCats = if (metrics.topCategories.isEmpty()) {
            "Nihil"
        } else {
            metrics.topCategories.take(4).joinToString("; ") { cat ->
                val grp = if (cat.expenseGroup == ExpenseGroup.WANTS) "Keinginan" else "Kebutuhan"
                String.format(
                    Locale.US,
                    "%s(%s): %s %.0f%%",
                    cat.categoryName,
                    grp,
                    CurrencyHelper.format(cat.amount, c),
                    cat.sharePercent
                )
            }
        }

        val topTx = if (metrics.recentHighlights.isEmpty()) {
            "Nihil"
        } else {
            metrics.recentHighlights.take(4).joinToString("; ") {
                "${it.title} (${it.categoryName}) ${CurrencyHelper.format(it.amount, c)}"
            }
        }

        val overBudget = if (metrics.overBudgetCategoryNames.isEmpty()) "Aman" else metrics.overBudgetCategoryNames.joinToString(", ")

        return """
            Analis keuangan DanaLoka (Bahasa Indonesia, evaluasi $periodName, tanpa markdown, jangan karang angka).
            Periode ${metrics.periodLabel} ($c):
            Masuk: ${CurrencyHelper.format(metrics.weekIncome, c)} (${metrics.incomeTransactionCount}x)
            Keluar: ${CurrencyHelper.format(metrics.weekExpense, c)} (${metrics.expenseTransactionCount}x), rata2 ${CurrencyHelper.format(metrics.dailyAverageExpense, c)}/hari, tren $trend
            Komposisi: Kebutuhan ${String.format(Locale.US, "%.0f", metrics.needsPercent)}% vs Keinginan ${String.format(Locale.US, "%.0f", metrics.wantsPercent)}%
            Bulan ini: ${CurrencyHelper.format(metrics.currentMonthTotalExpense, c)} / budget ${CurrencyHelper.format(metrics.overallMonthlyBudget, c)} | Overbudget: $overBudget
            Kategori teratas: $topCats
            Transaksi terbesar: $topTx
        """.trimIndent()
    }
}
