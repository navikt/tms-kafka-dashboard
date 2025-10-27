package no.nav.tms.kafka.dashboard.api.cache

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.tms.common.util.config.StringEnvVar.getEnvVar
import org.flywaydb.core.Flyway

class PostgresDatabase : Database {

    override val dataSource = hikariFromLocalDb()

    fun runFlywayMigrations() {
        val configBuilder = Flyway.configure()

        configBuilder.dataSource(dataSource)
            .load()
            .migrate()
    }

    companion object {

        fun hikariFromLocalDb(): HikariDataSource {
            val config = hikariCommonConfig(PgEnv())
            config.validate()
            return HikariDataSource(config)
        }

        private fun hikariCommonConfig(env: PgEnv): HikariConfig {
            val config = HikariConfig().apply {
                driverClassName = "org.postgresql.Driver"
                jdbcUrl = "${env.jdbcUrl}&prepareThreshold=0"
                minimumIdle = 1
                maxLifetime = 1800000
                maximumPoolSize = 5
                connectionTimeout = 4000
                validationTimeout = 1000
                idleTimeout = 30000
                isAutoCommit = true
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            }
            return config
        }
    }

    private data class PgEnv(
        val jdbcUrl: String = getEnvVar("PGJDBCURL"),
    )

}
