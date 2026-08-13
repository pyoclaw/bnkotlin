package com.brodogfyld.api.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

/**
 * Owns the PostgreSQL connection pool and applies Flyway migrations on
 * startup. Migrations live in database/migrations and are exposed on the
 * classpath at db/migration by the backend build script.
 */
class Database(jdbcUrl: String) : AutoCloseable {

    private val pool = HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            maximumPoolSize = 5
            poolName = "brodogfyld"
        }
    )

    val dataSource: DataSource get() = pool

    init {
        Flyway.configure()
            .dataSource(pool)
            .load()
            .migrate()
    }

    override fun close() {
        pool.close()
    }
}
