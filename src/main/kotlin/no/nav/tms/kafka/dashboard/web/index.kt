package no.nav.tms.kafka.dashboard.web

import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import no.nav.tms.kafka.dashboard.api.KafkaAdminService
import no.nav.tms.kafka.dashboard.web.card.consumerOffsetsCard
import no.nav.tms.kafka.dashboard.web.card.lastRecordOffsetCard
import no.nav.tms.kafka.dashboard.web.card.readFromTopicCard
import no.nav.tms.kafka.dashboard.web.card.setConsumerOffsetCard

fun Route.index(kafkaAdminService: KafkaAdminService) {
    get("/index.html") {
        call.respondHtml {
            head {
                lang = "nb"
                title("Kafka Dashboard")
                link {
                    rel = "stylesheet"
                    href = "/static/style.css"
                }
                script {
                    src = "/script.js"
                }
            }
            body {
                indexBody(kafkaAdminService.getAvailableTopics())
            }
        }
    }

    get("/script.js") {
        call.respondText(contentType = ContentType.Text.JavaScript, text = Script.get())
    }
}

private fun BODY.indexBody(topics: List<String>) {

    div("app kafka-manager") {
        header("header") {
            h1("header__title") {
                +"Kafka Manager"
            }
        }
        main {
            div("view kafka-dashboard") {
                div("kafka-dashboard__col") {
                    consumerOffsetsCard(topics)
                    lastRecordOffsetCard(topics)
                    setConsumerOffsetCard(topics)
                }
                div {
                    readFromTopicCard(topics)
                }
            }
        }
    }
}
