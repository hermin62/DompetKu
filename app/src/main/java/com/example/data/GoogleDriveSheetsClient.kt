package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GoogleUserProfile(
    val email: String,
    val name: String,
    val photoUrl: String
)

data class DriveFileInfo(
    val id: String,
    val name: String,
    val webViewLink: String
)

class GoogleApiException(val code: Int, override val message: String) : Exception(message)

class GoogleDriveSheetsClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        const val LEGACY_SPREADSHEET_TITLE = "DompetKu_Database"
        const val APP_PROP_OWNER_EMAIL = "dompetkuOwnerEmail"

        fun spreadsheetTitleFor(ownerEmail: String): String {
            val clean = ownerEmail.trim().lowercase()
            return if (clean.isBlank()) LEGACY_SPREADSHEET_TITLE else "DompetKu_Database ($clean)"
        }

        private fun quotedRange(sheetName: String, a1Suffix: String): String {
            val escaped = sheetName.replace("'", "''")
            return "'$escaped'!$a1Suffix"
        }
    }

    suspend fun fetchUserProfile(accessToken: String): GoogleUserProfile? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v3/userinfo")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 401) {
                    throw GoogleApiException(401, "Sesi token Google tidak valid atau kedaluwarsa")
                }
                if (response.code == 403) {
                    throw GoogleApiException(403, parseApiError(body, "Izin profil Google ditolak (403)"))
                }
                return@withContext null
            }
            val json = JSONObject(body)
            val email = json.optString("email", "").trim().lowercase()
            if (email.isBlank()) return@withContext null
            GoogleUserProfile(
                email = email,
                name = json.optString("name", email.substringBefore("@")),
                photoUrl = json.optString("picture", "")
            )
        }
    }

    suspend fun findDompetKuSpreadsheet(
        accessToken: String,
        ownerEmail: String
    ): DriveFileInfo? = withContext(Dispatchers.IO) {
        val normalizedEmail = ownerEmail.trim().lowercase()
        val expectedTitle = spreadsheetTitleFor(normalizedEmail)

        // 1. Search by appProperties tagged for this specific account email
        if (normalizedEmail.isNotBlank()) {
            val escapedEmail = normalizedEmail.replace("'", "\\'")
            val propQuery =
                "appProperties has { key='$APP_PROP_OWNER_EMAIL' and value='$escapedEmail' } and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
            querySingleDriveFile(accessToken, propQuery)?.let { return@withContext it }

            // 2. Search by account-specific spreadsheet title
            val escapedTitle = expectedTitle.replace("'", "\\'")
            val titleQuery =
                "name = '$escapedTitle' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
            querySingleDriveFile(accessToken, titleQuery)?.let { file ->
                tagFileWithOwnerEmail(accessToken, file.id, normalizedEmail)
                return@withContext file
            }
        }

        // 3. Legacy fallback ("DompetKu_Database") only if unowned or owned by this exact email
        val legacyQuery =
            "name = '$LEGACY_SPREADSHEET_TITLE' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
        val legacyFile = querySingleDriveFile(accessToken, legacyQuery)
        if (legacyFile != null) {
            val snapshotOwner = runCatching { readOwnerEmailOnly(accessToken, legacyFile.id) }.getOrNull().orEmpty()
            if (snapshotOwner.isBlank() || snapshotOwner.equals(normalizedEmail, ignoreCase = true)) {
                if (normalizedEmail.isNotBlank()) {
                    tagFileWithOwnerEmail(accessToken, legacyFile.id, normalizedEmail)
                }
                return@withContext legacyFile
            }
        }

        null
    }

    private fun querySingleDriveFile(accessToken: String, query: String): DriveFileInfo? {
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("orderBy", "modifiedTime desc")
            .addQueryParameter("pageSize", "5")
            .addQueryParameter("fields", "files(id,name,webViewLink)")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GoogleApiException(response.code, parseApiError(body, "Gagal mencari file di Google Drive (${response.code})"))
            }
            val files = JSONObject(body).optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            val first = files.getJSONObject(0)
            val id = first.getString("id")
            return DriveFileInfo(
                id = id,
                name = first.optString("name", LEGACY_SPREADSHEET_TITLE),
                webViewLink = first.optString("webViewLink", "https://docs.google.com/spreadsheets/d/$id")
            )
        }
    }

    private fun tagFileWithOwnerEmail(accessToken: String, fileId: String, ownerEmail: String) {
        runCatching {
            val payload = JSONObject().apply {
                put("appProperties", JSONObject().apply {
                    put(APP_PROP_OWNER_EMAIL, ownerEmail.trim().lowercase())
                })
            }
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .header("Authorization", "Bearer $accessToken")
                .patch(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().close()
        }
    }

    private fun readOwnerEmailOnly(accessToken: String, spreadsheetId: String): String {
        val range = quotedRange(DompetKuSnapshot.SHEET_SETTINGS, "A1:B20")
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values".toHttpUrl()
            .newBuilder()
            .addPathSegment(range)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ""
            val body = response.body?.string().orEmpty()
            val values = JSONObject(body).optJSONArray("values") ?: return ""
            for (i in 1 until values.length()) {
                val row = values.optJSONArray(i) ?: continue
                if (row.optString(0) == "ownerEmail") {
                    return row.optString(1, "").trim().lowercase()
                }
            }
            return ""
        }
    }

    suspend fun createDompetKuSpreadsheet(
        accessToken: String,
        ownerEmail: String
    ): DriveFileInfo = withContext(Dispatchers.IO) {
        val normalizedEmail = ownerEmail.trim().lowercase()
        val sheetTitle = spreadsheetTitleFor(normalizedEmail)
        val sheetsArray = JSONArray()
        DompetKuSnapshot.ALL_SHEET_NAMES.forEachIndexed { index, title ->
            sheetsArray.put(
                JSONObject().apply {
                    put(
                        "properties",
                        JSONObject().apply {
                            put("title", title)
                            put("index", index)
                            put("gridProperties", JSONObject().apply { put("frozenRowCount", 1) })
                        }
                    )
                }
            )
        }

        // Omit locale so Sheets API never rejects legacy/locale formatting codes with INVALID_ARGUMENT
        val payload = JSONObject().apply {
            put("properties", JSONObject().apply {
                put("title", sheetTitle)
            })
            put("sheets", sheetsArray)
        }

        val request = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets")
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GoogleApiException(response.code, parseApiError(body, "Gagal membuat Google Spreadsheet (${response.code})"))
            }
            val json = JSONObject(body)
            val spreadsheetId = json.getString("spreadsheetId")
            val spreadsheetUrl = json.optString("spreadsheetUrl", "https://docs.google.com/spreadsheets/d/$spreadsheetId")
            if (normalizedEmail.isNotBlank()) {
                tagFileWithOwnerEmail(accessToken, spreadsheetId, normalizedEmail)
            }
            DriveFileInfo(
                id = spreadsheetId,
                name = sheetTitle,
                webViewLink = spreadsheetUrl
            )
        }
    }

    suspend fun ensureRequiredSheetsExist(accessToken: String, spreadsheetId: String) = withContext(Dispatchers.IO) {
        val getReq = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId?fields=sheets.properties.title")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val existingTitles = mutableSetOf<String>()
        client.newCall(getReq).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GoogleApiException(response.code, parseApiError(body, "Gagal membaca struktur Spreadsheet (${response.code})"))
            }
            val sheets = JSONObject(body).optJSONArray("sheets")
            if (sheets != null) {
                for (i in 0 until sheets.length()) {
                    val title = sheets.getJSONObject(i).optJSONObject("properties")?.optString("title").orEmpty()
                    if (title.isNotEmpty()) existingTitles.add(title)
                }
            }
        }

        val missing = DompetKuSnapshot.ALL_SHEET_NAMES.filterNot { existingTitles.contains(it) }
        if (missing.isEmpty()) return@withContext

        val requestsArr = JSONArray()
        missing.forEach { title ->
            val propsObj = JSONObject().apply {
                put("title", title)
                put("gridProperties", JSONObject().apply { put("frozenRowCount", 1) })
            }
            val addSheetObj = JSONObject().apply {
                put("properties", propsObj)
            }
            requestsArr.put(JSONObject().apply { put("addSheet", addSheetObj) })
        }
        val batchPayload = JSONObject().apply { put("requests", requestsArr) }
        val batchReq = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId:batchUpdate")
            .header("Authorization", "Bearer $accessToken")
            .post(batchPayload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(batchReq).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GoogleApiException(response.code, parseApiError(body, "Gagal menyiapkan tab Spreadsheet (${response.code})"))
            }
        }
    }

    suspend fun readSnapshot(
        accessToken: String,
        spreadsheetId: String,
        expectedOwnerEmail: String = ""
    ): DompetKuSnapshot? = withContext(Dispatchers.IO) {
        ensureRequiredSheetsExist(accessToken, spreadsheetId)

        // Unbounded column A1 ranges safely read all rows without hitting fixed grid-row limits
        val urlBuilder = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchGet".toHttpUrl().newBuilder()
        urlBuilder.addQueryParameter("ranges", quotedRange(DompetKuSnapshot.SHEET_SETTINGS, "A:B"))
        urlBuilder.addQueryParameter("ranges", quotedRange(DompetKuSnapshot.SHEET_ACCOUNTS, "A:H"))
        urlBuilder.addQueryParameter("ranges", quotedRange(DompetKuSnapshot.SHEET_CATEGORIES, "A:H"))
        urlBuilder.addQueryParameter("ranges", quotedRange(DompetKuSnapshot.SHEET_TRANSACTIONS, "A:K"))
        urlBuilder.addQueryParameter("ranges", quotedRange(DompetKuSnapshot.SHEET_DEBTS, "A:J"))
        urlBuilder.addQueryParameter("ranges", quotedRange(DompetKuSnapshot.SHEET_BILLS, "A:I"))
        urlBuilder.addQueryParameter("valueRenderOption", "UNFORMATTED_VALUE")

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GoogleApiException(response.code, parseApiError(body, "Gagal mengambil data dari Google Sheets (${response.code})"))
            }
            val valueRanges = JSONObject(body).optJSONArray("valueRanges") ?: return@withContext null
            val tables = mutableMapOf<String, List<List<Any?>>>()
            for (i in 0 until valueRanges.length()) {
                val vr = valueRanges.getJSONObject(i)
                val rangeStr = vr.optString("range", "")
                val sheetName = rangeStr.substringBefore("!").trim('\'')
                val valuesArr = vr.optJSONArray("values")
                val rows = mutableListOf<List<Any?>>()
                if (valuesArr != null) {
                    for (r in 0 until valuesArr.length()) {
                        val rowArr = valuesArr.getJSONArray(r)
                        val rowList = mutableListOf<Any?>()
                        for (c in 0 until rowArr.length()) {
                            rowList.add(if (rowArr.isNull(c)) null else rowArr.get(c))
                        }
                        rows.add(rowList)
                    }
                }
                tables[sheetName] = rows
            }
            val snapshot = SnapshotSerializer.fromSheetTables(tables) ?: return@withContext null
            if (expectedOwnerEmail.isNotBlank() &&
                snapshot.ownerEmail.isNotBlank() &&
                !snapshot.ownerEmail.equals(expectedOwnerEmail.trim(), ignoreCase = true)
            ) {
                return@withContext null
            }
            snapshot
        }
    }

    suspend fun writeSnapshot(accessToken: String, spreadsheetId: String, snapshot: DompetKuSnapshot) = withContext(Dispatchers.IO) {
        ensureRequiredSheetsExist(accessToken, spreadsheetId)

        // Use unbounded column ranges (A:Z) so batchClear never fails with "Range exceeds grid limits"
        val clearRanges = JSONArray().apply {
            DompetKuSnapshot.ALL_SHEET_NAMES.forEach { sheetName ->
                put(quotedRange(sheetName, "A:Z"))
            }
        }
        val clearPayload = JSONObject().apply { put("ranges", clearRanges) }
        val clearReq = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchClear")
            .header("Authorization", "Bearer $accessToken")
            .post(clearPayload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(clearReq).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GoogleApiException(response.code, parseApiError(body, "Gagal membersihkan sheet sebelum sinkronisasi (${response.code})"))
            }
        }

        val tables = SnapshotSerializer.toSheetTables(snapshot)
        val dataArray = JSONArray()
        tables.forEach { (sheetName, rows) ->
            val valuesJson = JSONArray()
            rows.forEach { row ->
                val rowJson = JSONArray()
                row.forEach { cell ->
                    when (cell) {
                        is Double -> rowJson.put(if (cell.isFinite()) cell else 0.0)
                        is Float -> rowJson.put(if (cell.isFinite()) cell.toDouble() else 0.0)
                        else -> rowJson.put(cell)
                    }
                }
                valuesJson.put(rowJson)
            }
            dataArray.put(
                JSONObject().apply {
                    put("range", quotedRange(sheetName, "A1"))
                    put("majorDimension", "ROWS")
                    put("values", valuesJson)
                }
            )
        }

        val updatePayload = JSONObject().apply {
            put("valueInputOption", "RAW")
            put("data", dataArray)
        }

        val updateReq = Request.Builder()
            .url("https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values:batchUpdate")
            .header("Authorization", "Bearer $accessToken")
            .post(updatePayload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(updateReq).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GoogleApiException(response.code, parseApiError(body, "Gagal menyimpan data ke Google Sheets (${response.code})"))
            }
        }
    }

    private fun parseApiError(body: String, fallback: String): String {
        return try {
            val root = JSONObject(body)
            val errObj = root.optJSONObject("error")
            val message = errObj?.optString("message")?.takeIf { it.isNotBlank() }
                ?: root.optString("error_description").takeIf { it.isNotBlank() }
            message ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }
}
