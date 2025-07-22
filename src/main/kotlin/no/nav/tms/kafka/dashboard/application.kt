package no.nav.tms.kafka.dashboard

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.tms.common.util.config.StringEnvVar
import no.nav.tms.kafka.dashboard.domain.KafkaAppConfig
import no.nav.tms.kafka.dashboard.service.KafkaAdminService

fun main() {

    val adminService = KafkaAdminService(
        appConfig = getKafkaConfig()
    )

    embeddedServer(
        factory = Netty,
        module = {
            rootPath = "tms-kafka-dashboard"
            kafkaDashboard(adminService)
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

    val kafkaAppConfigJson = StringEnvVar.getEnvVar("KAFKA_APP_CONFIG")

    return objectMapper.readValue(kafkaAppConfigJson)
}
