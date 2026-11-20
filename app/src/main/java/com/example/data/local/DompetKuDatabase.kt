package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedTransactionEntity::class,
        CachedAccountEntity::class,
        CachedCategoryEntity::class,
        CachedDebtEntity::class,
        CachedBillEntity::class,
        CachedWorkspaceMetaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DompetKuDatabase : RoomDatabase() {
    abstract fun dompetKuDao(): DompetKuDao

    companion object {
        @Volatile
        private var INSTANCE: DompetKuDatabase? = null

        fun getInstance(context: Context): DompetKuDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DompetKuDatabase::class.java,
                    "dompetku_offline_cache.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
