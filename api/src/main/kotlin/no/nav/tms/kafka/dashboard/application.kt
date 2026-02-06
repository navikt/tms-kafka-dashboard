package no.nav.tms.kafka.dashboard

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.tms.common.postgres.Postgres
import no.nav.tms.common.util.config.BooleanEnvVar.getEnvVarAsBoolean
import no.nav.tms.common.util.config.IntEnvVar
import no.nav.tms.common.util.config.StringEnvVar
import no.nav.tms.common.util.config.StringEnvVar.getEnvVar
import no.nav.tms.kafka.dashboard.api.CachingKafkaAdminService
import no.nav.tms.kafka.dashboard.api.KafkaAdminService
import no.nav.tms.kafka.dashboard.api.KafkaAdminServiceMock
import no.nav.tms.kafka.dashboard.api.KafkaReader
import no.nav.tms.kafka.dashboard.api.cache.OffsetCache
import no.nav.tms.token.support.azure.validation.azure
import no.nav.tms.token.support.azure.validation.mock.azureMock
import org.flywaydb.core.Flyway

fun main() {

    val adminService: KafkaAdminService
    val webAppLocation: String
    val authFunction: Application.() -> Unit

    val cacheInit: () -> Unit

    if(getEnvVarAsBoolean("LOCAL_DEV_MODE", false)) {
        adminService = KafkaAdminServiceMock(getKafkaConfig())
        webAppLocation = "web-app/dist"
        authFunction = {
            authentication {
                azureMock {
                    alwaysAuthenticated = true
                    setAsDefault = true
                }
            }
        }
        cacheInit = {}
    } else {
        val database = Postgres.connectToJdbcUrl(getEnvVar("DB_JDBC_URL"))

        val kafkaReader = KafkaReader(getKafkaConfig())
        val offsetCache = OffsetCache(
            database = database,
            kafkaReader = kafkaReader,
            cacheWriteBatchSize = IntEnvVar.getEnvVarAsInt("CACHE_WRITE_BATCH_SIZE", 500)
        )

        adminService = CachingKafkaAdminService(
            kafkaReader = kafkaReader,
            offsetCache = offsetCache,
            kafkaReadBatchSize = IntEnvVar.getEnvVarAsInt("KAFKA_READ_BATCH_SIZE", 1000)
        )
        webAppLocation = "app/public"
        authFunction = {
            authentication {
                azure {
                    setAsDefault = true
                }
            }
        }
        cacheInit = {
            Flyway.configure()
                .dataSource(database.dataSource)
                .load()
                .migrate()

            adminService.initCache(getEnvVarAsBoolean("RESET_CACHE", false))
        }
    }

    embeddedServer(
        factory = Netty,
        module = {
            kafkaDashboard(adminService, webAppLocation, authFunction)

            monitor.subscribe(ApplicationStarted) {
                cacheInit()
            }
        },
        configure = {
            connector {
                port = 8080
            }
        }
    ).start(wait = true)
}

private fun getKafkaConfig(): KafkaAppConfig {
    val objectMapper = jacksonObjectMapper()

    val kafkaAppConfigJson = StringEnvVar.getEnvVar("KAFKA_APP_CONFIG", "{}")

    return objectMapper.readValue(kafkaAppConfigJson)
}
