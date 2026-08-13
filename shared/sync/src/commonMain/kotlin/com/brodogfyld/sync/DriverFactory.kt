package com.brodogfyld.sync

import app.cash.sqldelight.db.SqlDriver

/**
 * Platform-specific SQLite driver factory. The desktop/JVM target uses the
 * JDBC SQLite driver; Android/iOS targets add their own `actual` when those
 * targets land (PLAN.md).
 */
expect fun createSqlDriver(): SqlDriver
