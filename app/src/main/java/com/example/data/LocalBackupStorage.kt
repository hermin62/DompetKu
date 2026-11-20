package com.example.data

import android.content.Context
import java.io.File

class LocalBackupStorage(private val context: Context) {

    private fun fileForAccount(accountKey: String): File {
        val safeKey = accountKey
            .trim()
            .lowercase()
            .ifBlank { "local_offline" }
            .replace(Regex("[^a-z0-9._-]"), "_")
        return File(context.filesDir, "dompetku_snapshot_$safeKey.json")
    }

    fun hasSnapshot(accountKey: String): Boolean {
        return fileForAccount(accountKey).exists()
    }

    fun saveSnapshot(accountKey: String, snapshot: DompetKuSnapshot) {
        try {
            val file = fileForAccount(accountKey)
            val json = SnapshotSerializer.toJsonString(snapshot)
            file.writeText(json, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadSnapshot(accountKey: String): DompetKuSnapshot? {
        return try {
            val file = fileForAccount(accountKey)
            if (!file.exists()) return null
            val json = file.readText(Charsets.UTF_8)
            SnapshotSerializer.fromJsonString(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
