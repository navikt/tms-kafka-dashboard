package no.nav.tms.kafka.dashboard.api.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.hsqldb.Server

class HsqlDatabase() : Database {

    private val dbServer = startServer()

    private val envDataSource = createConnectionForLocalDbWithDbUser()

    override val dataSource: HikariDataSource
        get() = envDataSource

    private fun createConnectionForLocalDbWithDbUser(): HikariDataSource {
        return hikariFromLocalDb()
    }

    fun runFlywayMigrations() {
        val configBuilder = Flyway.configure()
        val dataSource = createConnectionForLocalDbWithDbUser()

        configBuilder.dataSource(dataSource)
            .load()
            .migrate()
    }

    companion object {

        fun hikariFromLocalDb(): HikariDataSource {
            val config = hikariCommonConfig()
            config.validate()
            return HikariDataSource(config)
        }

        private fun startServer() = Server().apply {
                logWriter = null
                isSilent = true

                setDatabaseName(0, "offset_cache")
                setDatabasePath(0, "mem:mem_cache")

                start()
            }

        private fun hikariCommonConfig(): HikariConfig {
            val config = HikariConfig().apply {
                driverClassName = "org.hsqldb.jdbc.JDBCDriver"
                jdbcUrl = "jdbc:hsqldb:hsql://localhost/offset_cache"
                minimumIdle = 1
                maxLifetime = 1800000
                maximumPoolSize = 5
                connectionTimeout = 4000
                validationTimeout = 1000
                idleTimeout = 30000
                isAutoCommit = true
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                username = "sa"
                password = ""
            }
            return config
        }
    }
}
