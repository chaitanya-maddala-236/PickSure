package com.picksure.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PickEventEntity::class, TaskCacheEntity::class, TaskItemCacheEntity::class], version = 3, exportSchema = false)
abstract class PickSureDatabase : RoomDatabase() {
    abstract fun pickEventDao(): PickEventDao
    abstract fun taskCacheDao(): TaskCacheDao

    companion object {
        @Volatile private var instance: PickSureDatabase? = null

        fun get(context: Context): PickSureDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PickSureDatabase::class.java, "picksure.db")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `task_cache` (`id` TEXT NOT NULL, `orderId` TEXT NOT NULL, `priority` TEXT NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `task_item_cache` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, `orderId` TEXT NOT NULL, `sku` TEXT NOT NULL, `productName` TEXT NOT NULL, `barcode` TEXT NOT NULL, `requestedQty` INTEGER NOT NULL, `aisle` TEXT NOT NULL, `rack` TEXT NOT NULL, `shelf` TEXT NOT NULL, `serverPicked` INTEGER NOT NULL, `serverStatus` TEXT NOT NULL, PRIMARY KEY(`id`))")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_task_item_cache_taskId` ON `task_item_cache` (`taskId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `task_item_cache` ADD COLUMN `recommendedLotId` TEXT")
                database.execSQL("ALTER TABLE `task_item_cache` ADD COLUMN `lotCode` TEXT")
                database.execSQL("ALTER TABLE `task_item_cache` ADD COLUMN `lotTag` TEXT")
                database.execSQL("ALTER TABLE `task_item_cache` ADD COLUMN `expiresOn` TEXT")
                database.execSQL("ALTER TABLE `task_item_cache` ADD COLUMN `lotStatus` TEXT NOT NULL DEFAULT 'no_stock'")
                database.execSQL("ALTER TABLE `pick_events` ADD COLUMN `lotId` TEXT")
                database.execSQL("ALTER TABLE `pick_events` ADD COLUMN `lotTag` TEXT")
            }
        }
    }
}
