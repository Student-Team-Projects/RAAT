/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.tcs.raat.model.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tcs.raat.model.ServerProfile

@Database(entities = [ServerProfile::class], version = 4, exportSchema = true)
abstract class MainDb : RoomDatabase() {
    abstract val serverProfileDao: ServerProfileDao

    companion object {
        /**
         * Database singleton.
         */
        private var instance: MainDb? = null

        /**
         * Returns database singleton.
         * If database is not yet created then it will be created on first call.
         */
        @Synchronized
        fun getInstance(context: Context): MainDb {
            if (instance == null) {
                instance = Room.databaseBuilder(context, MainDb::class.java, "main")
                        .addMigrations(Migration_1_2, Migration_2_3, Migration_3_4)
                        .build()
            }
            return instance!!
        }

        // Added in v2.0.0
        private val Migration_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN useRawEncoding INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE profiles ADD COLUMN zoom1 REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE profiles ADD COLUMN zoom2 REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE profiles ADD COLUMN gestureStyle TEXT NOT NULL DEFAULT 'auto'")
                db.execSQL("UPDATE profiles SET imageQuality = 5")
            }
        }

        // Added in v3.0.0
        private val Migration_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN desktopEnv TEXT NOT NULL DEFAULT 'Lxde'")
            }
        }

        // Added in v3.0.0
        private val Migration_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN isSessionAlive INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}