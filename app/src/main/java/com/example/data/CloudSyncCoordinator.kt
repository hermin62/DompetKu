package com.example.data

sealed class SyncOutcome {
    data class Success(val message: String, val spreadsheetUrl: String) : SyncOutcome()
    data class Unauthorized(val message: String) : SyncOutcome()
    data class Error(val message: String) : SyncOutcome()
}

class CloudSyncCoordinator(
    private val client: GoogleDriveSheetsClient = GoogleDriveSheetsClient()
) {

    suspend fun completeSignInAndRestore(
        accessToken: String,
        initialIdentity: GoogleSignInIdentity?,
        wasOfflineUpgrade: Boolean,
        repository: FinanceRepository,
        prefsManager: PreferencesManager,
        localBackup: LocalBackupStorage
    ): SyncOutcome {
        return try {
            val fetchedProfile = client.fetchUserProfile(accessToken)
            val email = (fetchedProfile?.email?.takeIf { it.isNotBlank() }
                ?: initialIdentity?.email.orEmpty()).trim().lowercase()
            if (email.isBlank()) {
                return SyncOutcome.Error("Gagal mendapatkan alamat email akun Google.")
            }
            val displayName = fetchedProfile?.name?.takeIf { it.isNotBlank() }
                ?: initialIdentity?.displayName?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@")
            val photoUrl = fetchedProfile?.photoUrl?.takeIf { it.isNotBlank() }
                ?: initialIdentity?.photoUrl.orEmpty()

            prefsManager.saveGoogleSession(email, displayName, photoUrl)

            val existingFile = client.findDompetKuSpreadsheet(accessToken, email)
            if (existingFile != null) {
                prefsManager.setSpreadsheetInfo(existingFile.id, existingFile.webViewLink)
                val remoteSnapshot = client.readSnapshot(accessToken, existingFile.id, email)
                val localSaved = localBackup.loadSnapshot(email)
                    ?.takeIf { it.ownerEmail.isBlank() || it.ownerEmail.equals(email, ignoreCase = true) }

                if (remoteSnapshot != null && (localSaved == null || remoteSnapshot.updatedAtMillis >= localSaved.updatedAtMillis)) {
                    repository.restoreSnapshot(remoteSnapshot)
                    prefsManager.setCurrency(remoteSnapshot.currencyCode)
                    prefsManager.setOverallMonthlyBudget(remoteSnapshot.overallMonthlyBudget)
                    localBackup.saveSnapshot(email, remoteSnapshot.copy(ownerEmail = email))
                    prefsManager.updateLastSyncTime("Data akun dipulihkan dari Google Spreadsheet")
                    return SyncOutcome.Success(
                        "Berhasil masuk! Database akun $email dipulihkan.",
                        existingFile.webViewLink
                    )
                } else if (localSaved != null) {
                    repository.restoreSnapshot(localSaved)
                    prefsManager.setCurrency(localSaved.currencyCode)
                    prefsManager.setOverallMonthlyBudget(localSaved.overallMonthlyBudget)
                    val snapshotToPush = repository.exportSnapshot(ownerEmail = email)
                    client.writeSnapshot(accessToken, existingFile.id, snapshotToPush)
                    localBackup.saveSnapshot(email, snapshotToPush)
                    prefsManager.updateLastSyncTime("Database lokal akun disinkronkan ke Google Sheets")
                    return SyncOutcome.Success(
                        "Berhasil masuk! Database akun $email disinkronkan.",
                        existingFile.webViewLink
                    )
                } else {
                    if (!wasOfflineUpgrade) {
                        repository.resetToStarterData()
                    }
                    val snapshotToPush = repository.exportSnapshot(ownerEmail = email)
                    client.writeSnapshot(accessToken, existingFile.id, snapshotToPush)
                    localBackup.saveSnapshot(email, snapshotToPush)
                    prefsManager.updateLastSyncTime("Google Spreadsheet diinisialisasi")
                    return SyncOutcome.Success(
                        "Berhasil terhubung ke Google Spreadsheet untuk $email.",
                        existingFile.webViewLink
                    )
                }
            } else {
                val localSaved = localBackup.loadSnapshot(email)
                    ?.takeIf { it.ownerEmail.isBlank() || it.ownerEmail.equals(email, ignoreCase = true) }
                if (localSaved != null) {
                    repository.restoreSnapshot(localSaved)
                    prefsManager.setCurrency(localSaved.currencyCode)
                    prefsManager.setOverallMonthlyBudget(localSaved.overallMonthlyBudget)
                } else if (!wasOfflineUpgrade) {
                    repository.resetToStarterData()
                }
                val newFile = client.createDompetKuSpreadsheet(accessToken, email)
                prefsManager.setSpreadsheetInfo(newFile.id, newFile.webViewLink)
                val snapshotToPush = repository.exportSnapshot(ownerEmail = email)
                client.writeSnapshot(accessToken, newFile.id, snapshotToPush)
                localBackup.saveSnapshot(email, snapshotToPush)
                prefsManager.updateLastSyncTime("Database Spreadsheet baru dibuat")
                return SyncOutcome.Success(
                    "Database ${newFile.name} dibuat terpisah di Google Drive Anda.",
                    newFile.webViewLink
                )
            }
        } catch (e: GoogleApiException) {
            if (e.code == 401) {
                SyncOutcome.Unauthorized("Sesi akses Google kedaluwarsa atau tidak valid, silakan hubungkan ulang.")
            } else {
                prefsManager.setLastSyncStatus("Gagal sinkronisasi: ${e.message}")
                SyncOutcome.Error(e.message)
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Gagal terhubung ke Google Drive & Spreadsheet"
            prefsManager.setLastSyncStatus("Gagal sinkronisasi: $msg")
            SyncOutcome.Error(msg)
        }
    }

    suspend fun pushSnapshot(
        accessToken: String,
        repository: FinanceRepository,
        prefsManager: PreferencesManager,
        localBackup: LocalBackupStorage
    ): SyncOutcome {
        val prefs = prefsManager.userPreferences.value
        val email = prefs.userEmail.trim().lowercase()
        if (!prefs.isLoggedIn || email.isBlank()) {
            return SyncOutcome.Error("Belum masuk dengan akun Google.")
        }

        try {
            val tokenOwner = client.fetchUserProfile(accessToken)?.email?.trim()?.lowercase()
            if (!tokenOwner.isNullOrBlank() && tokenOwner != email) {
                return SyncOutcome.Unauthorized("Token Google bukan milik akun $email, silakan otorisasi ulang.")
            }
        } catch (e: GoogleApiException) {
            if (e.code == 401) {
                return SyncOutcome.Unauthorized("Token akses Google tidak valid atau kedaluwarsa")
            }
            // Non-401 profile read failures fall through so Drive/Sheets calls can still succeed if valid
        } catch (_: Exception) {
            // Network timeout or transient profile read issue handled below
        }

        val snapshot = repository.exportSnapshot(ownerEmail = email)
        localBackup.saveSnapshot(email, snapshot)

        return try {
            var spreadsheetId = prefs.spreadsheetId
            var spreadsheetUrl = prefs.spreadsheetUrl

            if (spreadsheetId.isBlank()) {
                val file = client.findDompetKuSpreadsheet(accessToken, email)
                    ?: client.createDompetKuSpreadsheet(accessToken, email)
                spreadsheetId = file.id
                spreadsheetUrl = file.webViewLink
                prefsManager.setSpreadsheetInfo(spreadsheetId, spreadsheetUrl)
            }

            try {
                client.writeSnapshot(accessToken, spreadsheetId, snapshot)
            } catch (e: GoogleApiException) {
                // Recover transparently if spreadsheet was deleted/trashed or inaccessible (404 / 403 / 400)
                if (e.code == 404 || e.code == 403 || e.code == 400) {
                    val freshFile = client.findDompetKuSpreadsheet(accessToken, email)
                        ?: client.createDompetKuSpreadsheet(accessToken, email)
                    spreadsheetId = freshFile.id
                    spreadsheetUrl = freshFile.webViewLink
                    prefsManager.setSpreadsheetInfo(spreadsheetId, spreadsheetUrl)
                    client.writeSnapshot(accessToken, spreadsheetId, snapshot)
                } else {
                    throw e
                }
            }

            prefsManager.updateLastSyncTime("Tersinkron ke Google Spreadsheet")
            SyncOutcome.Success("Database $email tersinkron ke Google Spreadsheet.", spreadsheetUrl)
        } catch (e: GoogleApiException) {
            if (e.code == 401) {
                SyncOutcome.Unauthorized("Token akses kedaluwarsa atau tidak valid")
            } else {
                prefsManager.setLastSyncStatus("Sinkronisasi gagal (${e.message})")
                SyncOutcome.Error(e.message)
            }
        } catch (e: Exception) {
            prefsManager.setLastSyncStatus("Mode offline (tersimpan lokal)")
            SyncOutcome.Error(e.localizedMessage ?: "Tidak dapat menjangkau Google Drive")
        }
    }

    suspend fun syncOnStartup(
        accessToken: String,
        repository: FinanceRepository,
        prefsManager: PreferencesManager,
        localBackup: LocalBackupStorage
    ): SyncOutcome {
        val prefs = prefsManager.userPreferences.value
        val email = prefs.userEmail.trim().lowercase()
        if (!prefs.isLoggedIn || email.isBlank()) {
            return SyncOutcome.Error("Belum masuk akun Google.")
        }

        try {
            val tokenOwner = client.fetchUserProfile(accessToken)?.email?.trim()?.lowercase()
            if (!tokenOwner.isNullOrBlank() && tokenOwner != email) {
                return SyncOutcome.Unauthorized("Akun token berbeda dengan akun aktif ($email).")
            }
        } catch (e: GoogleApiException) {
            if (e.code == 401) {
                return SyncOutcome.Unauthorized("Token akses Google kedaluwarsa atau tidak valid.")
            }
        } catch (_: Exception) {
        }

        return try {
            var targetFile = if (prefs.spreadsheetId.isNotBlank()) {
                DriveFileInfo(
                    id = prefs.spreadsheetId,
                    name = GoogleDriveSheetsClient.spreadsheetTitleFor(email),
                    webViewLink = prefs.spreadsheetUrl.ifBlank {
                        "https://docs.google.com/spreadsheets/d/${prefs.spreadsheetId}"
                    }
                )
            } else {
                client.findDompetKuSpreadsheet(accessToken, email)
                    ?: client.createDompetKuSpreadsheet(accessToken, email)
            }
            prefsManager.setSpreadsheetInfo(targetFile.id, targetFile.webViewLink)

            val localSnapshot = localBackup.loadSnapshot(email)
                ?.takeIf { it.ownerEmail.isBlank() || it.ownerEmail.equals(email, ignoreCase = true) }

            val remoteSnapshot = try {
                client.readSnapshot(accessToken, targetFile.id, email)
            } catch (e: GoogleApiException) {
                if (e.code == 401) throw e
                if (e.code == 404 || e.code == 403 || e.code == 400) {
                    targetFile = client.findDompetKuSpreadsheet(accessToken, email)
                        ?: client.createDompetKuSpreadsheet(accessToken, email)
                    prefsManager.setSpreadsheetInfo(targetFile.id, targetFile.webViewLink)
                    runCatching { client.readSnapshot(accessToken, targetFile.id, email) }.getOrNull()
                } else {
                    null
                }
            }

            if (remoteSnapshot != null && (localSnapshot == null || remoteSnapshot.updatedAtMillis > localSnapshot.updatedAtMillis)) {
                repository.restoreSnapshot(remoteSnapshot)
                prefsManager.setCurrency(remoteSnapshot.currencyCode)
                prefsManager.setOverallMonthlyBudget(remoteSnapshot.overallMonthlyBudget)
                localBackup.saveSnapshot(email, remoteSnapshot.copy(ownerEmail = email))
                prefsManager.updateLastSyncTime("Diperbarui dari Google Spreadsheet")
                SyncOutcome.Success("Database $email diperbarui dari Google Spreadsheet.", targetFile.webViewLink)
            } else {
                val snapshotToPush = (localSnapshot ?: repository.exportSnapshot(ownerEmail = email)).copy(ownerEmail = email)
                client.writeSnapshot(accessToken, targetFile.id, snapshotToPush)
                localBackup.saveSnapshot(email, snapshotToPush)
                prefsManager.updateLastSyncTime("Tersinkron ke Google Spreadsheet")
                SyncOutcome.Success("Database Google Spreadsheet $email tersinkron.", targetFile.webViewLink)
            }
        } catch (e: GoogleApiException) {
            if (e.code == 401) {
                SyncOutcome.Unauthorized("Perlu otorisasi ulang Google Drive")
            } else {
                prefsManager.setLastSyncStatus("Tersimpan lokal (${e.message})")
                SyncOutcome.Error(e.message)
            }
        } catch (e: Exception) {
            prefsManager.setLastSyncStatus("Offline (menggunakan cadangan lokal)")
            SyncOutcome.Error(e.localizedMessage ?: "Gagal sinkronisasi awal")
        }
    }
}
