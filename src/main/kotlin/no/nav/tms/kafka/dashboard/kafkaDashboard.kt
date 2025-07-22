package no.nav.tms.kafka.dashboard

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.nav.tms.common.metrics.installTmsApiMetrics
import no.nav.tms.kafka.dashboard.controller.adminRoutes
import no.nav.tms.kafka.dashboard.service.KafkaAdminService
import no.nav.tms.token.support.azure.validation.azure
import java.text.DateFormat

fun Application.kafkaDashboard(
    adminService: KafkaAdminService,
    installAuthenticatorsFunction: Application.() -> Unit = {
        authentication {
            azure {
                setAsDefault = true
            }
        }
    }
) {

    val log = KotlinLogging.logger {}

    installAuthenticatorsFunction()

    installTmsApiMetrics {
        setupMetricsRoute = false
    }

    install(ContentNegotiation) {
        jackson {
            configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            registerModule(JavaTimeModule())
            dateFormat = DateFormat.getDateTimeInstance()
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is IllegalArgumentException -> {
                    call.respondText(
                        status = HttpStatusCode.BadRequest,
                        text = cause.message ?: "Feil i parametre"
                    )
                    log.warn(cause) { "Feil i parametre" }
                }

                else -> {
                    call.respond(HttpStatusCode.InternalServerError)
                    log.warn(cause) { "Apikall feiler" }
                }
            }

        }
    }

    routing {
        authenticate {
            adminRoutes(adminService)
        }
        get("/internal/isAlive") {
            call.respondText(text = "ALIVE", contentType = ContentType.Text.Plain)
        }

        get("/internal/isReady") {
            call.respondText(text = "READY", contentType = ContentType.Text.Plain)
        }
    }
}
