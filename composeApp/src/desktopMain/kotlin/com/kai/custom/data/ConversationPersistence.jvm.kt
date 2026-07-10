package com.kai.custom.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.kai.custom.db.KaiDatabase
import com.kai.custom.getAppFilesDirectory
import java.io.File

actual fun createConversationSqlDriver(): SqlDriver? {
    val file = File(getAppFilesDirectory(), "conversations.db")
    file.parentFile?.mkdirs()
    return JdbcSqliteDriver(url = "jdbc:sqlite:${file.absolutePath}", schema = KaiDatabase.Schema)
}
