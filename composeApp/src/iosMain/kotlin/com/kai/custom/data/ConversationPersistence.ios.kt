package com.kai.custom.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.kai.custom.db.KaiDatabase

actual fun createConversationSqlDriver(): SqlDriver? = NativeSqliteDriver(KaiDatabase.Schema, "conversations.db")
