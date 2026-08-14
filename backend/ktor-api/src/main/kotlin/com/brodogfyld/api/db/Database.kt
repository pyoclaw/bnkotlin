package com.brodogfyld.api.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database as ExposedDatabase
import javax.sql.DataSource

/**
 * Owns the PostgreSQL connection pool, applies Flyway migrations on startup,
 * and exposes an Exposed handle over the same pool (Ktor -> repository ->
 * Exposed -> PostgreSQL). Migrations live in database/migrations and are
 * exposed on the classpath at db/migration by the backend build script.
 *
 * Connection management: a single bounded HikariCP pool is shared by Flyway,
 * Exposed and any direct JDBC access. Pool size is deliberately small
 * (maximumPoolSize = 5); each application instance keeps its total connection
 * footprint modest so many instances stay within PostgreSQL's capacity.
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

    /** Exposed connection handle over [pool]; passed explicitly to transactions. */
    val exposed: ExposedDatabase = ExposedDatabase.connect(pool)

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
