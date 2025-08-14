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
import io.ktor.server.http.content.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import no.nav.tms.common.metrics.installTmsApiMetrics
import no.nav.tms.kafka.dashboard.api.adminRoutes
import no.nav.tms.kafka.dashboard.api.KafkaAdminService
import java.io.File
import java.text.DateFormat

fun Application.kafkaDashboard(
    adminService: KafkaAdminService,
    webAppLocation: String,
    installAuthenticatorsFunction: Application.() -> Unit
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

    install(CORS) {
        allowOrigins {
            "https://tms-kafka-dashboard\\.intern(\\.dev)?\\.nav\\.no".toRegex().matches(it)
        }
        allowCredentials = true
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

        staticFiles("/", File(webAppLocation)) {
            preCompressed(CompressedFileType.GZIP)
        }
    }
}
