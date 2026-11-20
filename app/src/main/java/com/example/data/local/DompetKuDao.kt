package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DompetKuDao {

    @Query("SELECT * FROM cached_transactions WHERE accountKey = :accountKey ORDER BY dateMillis DESC, id DESC")
    fun observeTransactions(accountKey: String): Flow<List<CachedTransactionEntity>>

    @Query("SELECT * FROM cached_transactions WHERE accountKey = :accountKey ORDER BY dateMillis DESC, id DESC")
    suspend fun getTransactions(accountKey: String): List<CachedTransactionEntity>

    @Query("SELECT COUNT(*) FROM cached_transactions WHERE accountKey = :accountKey")
    fun observeTransactionCount(accountKey: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(tx: CachedTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(txs: List<CachedTransactionEntity>)

    @Query("DELETE FROM cached_transactions WHERE accountKey = :accountKey AND id = :txId")
    suspend fun deleteTransaction(accountKey: String, txId: Long)

    @Query("DELETE FROM cached_transactions WHERE accountKey = :accountKey")
    suspend fun clearTransactions(accountKey: String)

    @Query("SELECT * FROM cached_accounts WHERE accountKey = :accountKey ORDER BY id ASC")
    fun observeAccounts(accountKey: String): Flow<List<CachedAccountEntity>>

    @Query("SELECT * FROM cached_accounts WHERE accountKey = :accountKey ORDER BY id ASC")
    suspend fun getAccounts(accountKey: String): List<CachedAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccounts(accounts: List<CachedAccountEntity>)

    @Query("DELETE FROM cached_accounts WHERE accountKey = :accountKey")
    suspend fun clearAccounts(accountKey: String)

    @Query("SELECT * FROM cached_categories WHERE accountKey = :accountKey ORDER BY id ASC")
    fun observeCategories(accountKey: String): Flow<List<CachedCategoryEntity>>

    @Query("SELECT * FROM cached_categories WHERE accountKey = :accountKey ORDER BY id ASC")
    suspend fun getCategories(accountKey: String): List<CachedCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CachedCategoryEntity>)

    @Query("DELETE FROM cached_categories WHERE accountKey = :accountKey")
    suspend fun clearCategories(accountKey: String)

    @Query("SELECT * FROM cached_debts WHERE accountKey = :accountKey ORDER BY dueDateMillis ASC")
    fun observeDebts(accountKey: String): Flow<List<CachedDebtEntity>>

    @Query("SELECT * FROM cached_debts WHERE accountKey = :accountKey ORDER BY dueDateMillis ASC")
    suspend fun getDebts(accountKey: String): List<CachedDebtEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDebts(debts: List<CachedDebtEntity>)

    @Query("DELETE FROM cached_debts WHERE accountKey = :accountKey")
    suspend fun clearDebts(accountKey: String)

    @Query("SELECT * FROM cached_bills WHERE accountKey = :accountKey ORDER BY dueDateMillis ASC")
    fun observeBills(accountKey: String): Flow<List<CachedBillEntity>>

    @Query("SELECT * FROM cached_bills WHERE accountKey = :accountKey ORDER BY dueDateMillis ASC")
    suspend fun getBills(accountKey: String): List<CachedBillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBills(bills: List<CachedBillEntity>)

    @Query("DELETE FROM cached_bills WHERE accountKey = :accountKey")
    suspend fun clearBills(accountKey: String)

    @Query("SELECT * FROM cached_workspace_meta WHERE accountKey = :accountKey LIMIT 1")
    fun observeWorkspaceMeta(accountKey: String): Flow<CachedWorkspaceMetaEntity?>

    @Query("SELECT * FROM cached_workspace_meta WHERE accountKey = :accountKey LIMIT 1")
    suspend fun getWorkspaceMeta(accountKey: String): CachedWorkspaceMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkspaceMeta(meta: CachedWorkspaceMetaEntity)

    @Query("UPDATE cached_workspace_meta SET pendingCloudSync = 0, lastSyncedAtMillis = :syncedAtMillis WHERE accountKey = :accountKey")
    suspend fun markCloudSynced(accountKey: String, syncedAtMillis: Long)

    @Transaction
    suspend fun replaceWorkspaceCache(
        accountKey: String,
        meta: CachedWorkspaceMetaEntity,
        accounts: List<CachedAccountEntity>,
        categories: List<CachedCategoryEntity>,
        transactions: List<CachedTransactionEntity>,
        debts: List<CachedDebtEntity>,
        bills: List<CachedBillEntity>
    ) {
        upsertWorkspaceMeta(meta)
        clearAccounts(accountKey)
        if (accounts.isNotEmpty()) upsertAccounts(accounts)
        clearCategories(accountKey)
        if (categories.isNotEmpty()) upsertCategories(categories)
        clearTransactions(accountKey)
        if (transactions.isNotEmpty()) upsertTransactions(transactions)
        clearDebts(accountKey)
        if (debts.isNotEmpty()) upsertDebts(debts)
        clearBills(accountKey)
        if (bills.isNotEmpty()) upsertBills(bills)
    }
}
