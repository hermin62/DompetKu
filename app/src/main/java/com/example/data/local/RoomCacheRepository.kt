package com.example.data.local

import android.content.Context
import com.example.data.DompetKuSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class RoomCacheStatus(
    val cachedTransactionCount: Int = 0,
    val pendingCloudSync: Boolean = false,
    val lastCloudSyncMillis: Long = 0L
)

class RoomCacheRepository(private val dao: DompetKuDao) {

    companion object {
        fun fromContext(context: Context): RoomCacheRepository {
            return RoomCacheRepository(DompetKuDatabase.getInstance(context).dompetKuDao())
        }
    }

    fun observeCacheStatus(accountKey: String): Flow<RoomCacheStatus> {
        val cleanKey = normalizeKey(accountKey)
        return dao.observeWorkspaceMeta(cleanKey).map { meta ->
            val txCount = dao.getTransactions(cleanKey).size
            RoomCacheStatus(
                cachedTransactionCount = txCount,
                pendingCloudSync = meta?.pendingCloudSync ?: false,
                lastCloudSyncMillis = meta?.lastSyncedAtMillis ?: 0L
            )
        }
    }

    fun observeTransactionCount(accountKey: String): Flow<Int> {
        return dao.observeTransactionCount(normalizeKey(accountKey))
    }

    suspend fun loadSnapshot(accountKey: String): DompetKuSnapshot? {
        val cleanKey = normalizeKey(accountKey)
        val meta = dao.getWorkspaceMeta(cleanKey) ?: return null
        val accounts = dao.getAccounts(cleanKey).map { it.toDomain() }
        val categories = dao.getCategories(cleanKey).map { it.toDomain() }
        val transactions = dao.getTransactions(cleanKey).map { it.toDomain() }
        val debts = dao.getDebts(cleanKey).map { it.toDomain() }
        val bills = dao.getBills(cleanKey).map { it.toDomain() }
        return DompetKuSnapshot(
            ownerEmail = meta.ownerEmail,
            currencyCode = meta.currencyCode,
            overallMonthlyBudget = meta.overallMonthlyBudget,
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            debts = debts,
            bills = bills,
            updatedAtMillis = meta.updatedAtMillis
        )
    }

    suspend fun saveSnapshot(
        accountKey: String,
        snapshot: DompetKuSnapshot,
        pendingCloudSync: Boolean
    ) {
        val cleanKey = normalizeKey(accountKey)
        val existingMeta = dao.getWorkspaceMeta(cleanKey)
        val meta = CachedWorkspaceMetaEntity(
            accountKey = cleanKey,
            ownerEmail = snapshot.ownerEmail,
            currencyCode = snapshot.currencyCode,
            overallMonthlyBudget = snapshot.overallMonthlyBudget,
            updatedAtMillis = snapshot.updatedAtMillis,
            lastSyncedAtMillis = if (!pendingCloudSync) System.currentTimeMillis() else (existingMeta?.lastSyncedAtMillis ?: 0L),
            pendingCloudSync = pendingCloudSync
        )
        dao.replaceWorkspaceCache(
            accountKey = cleanKey,
            meta = meta,
            accounts = snapshot.accounts.map { it.toRoom(cleanKey) },
            categories = snapshot.categories.map { it.toRoom(cleanKey) },
            transactions = snapshot.transactions.map { it.toRoom(cleanKey) },
            debts = snapshot.debts.map { it.toRoom(cleanKey) },
            bills = snapshot.bills.map { it.toRoom(cleanKey) }
        )
    }

    suspend fun markCloudSynced(accountKey: String) {
        dao.markCloudSynced(normalizeKey(accountKey), System.currentTimeMillis())
    }

    suspend fun isPendingCloudSync(accountKey: String): Boolean {
        return dao.getWorkspaceMeta(normalizeKey(accountKey))?.pendingCloudSync ?: false
    }

    private fun normalizeKey(accountKey: String): String {
        return accountKey.trim().lowercase().ifBlank { "local_offline" }
    }
}
