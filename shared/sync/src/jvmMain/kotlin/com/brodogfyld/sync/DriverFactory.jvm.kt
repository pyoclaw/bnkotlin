package com.brodogfyld.sync

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.brodogfyld.sync.db.KitchenDatabase

actual fun createSqlDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { driver ->
        KitchenDatabase.Schema.create(driver)
    }
