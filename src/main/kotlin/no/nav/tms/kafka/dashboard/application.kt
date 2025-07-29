package no.nav.tms.kafka.dashboard

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.tms.common.util.config.BooleanEnvVar
import no.nav.tms.common.util.config.StringEnvVar
import no.nav.tms.kafka.dashboard.api.KafkaAdminServiceImpl
import no.nav.tms.kafka.dashboard.api.KafkaAdminServiceMock
import no.nav.tms.token.support.azure.validation.azure
import no.nav.tms.token.support.azure.validation.mock.azureMock

fun main() {

    val localDevMode = BooleanEnvVar.getEnvVarAsBoolean("LOCAL_DEV_MODE", false)

    val adminService = if(localDevMode) {
        KafkaAdminServiceMock(
            appConfig = getKafkaConfig()
        )
    } else {
        KafkaAdminServiceImpl(
            appConfig = getKafkaConfig()
        )
    }

    val authFunction: Application.() -> Unit = {
        if (localDevMode) {
            authentication {
                azureMock {
                    alwaysAuthenticated = true
                    setAsDefault = true
                }
            }
        } else {
            authentication {
                azure {
                    setAsDefault = true
                }
            }
        }
    }

    embeddedServer(
        factory = Netty,
        module = {
            kafkaDashboard(adminService, authFunction)
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
