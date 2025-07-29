package no.nav.tms.kafka.dashboard.web.card

import kotlinx.html.FlowContent
import kotlinx.html.InputType
import no.nav.tms.kafka.dashboard.web.component.*

fun FlowContent.setConsumerOffsetCard(topics: List<String>) {
    card(title = "Set consumer offset", cardId = "set-consumer-offset-card") {
        BodyShort(spacing = true) {
            +"""
                Setter offset til en consumer for en topic+partisjon. Det er viktig å vite at selv om offsetet blir
                endret, så vil ikke consumere plukke opp endringen i offset før de er startet på nytt. Hvis en consumer
                committer et nytt offset før den har blitt startet på nytt og fått hentet inn endringen, så vil den
                overskrive offsetet fra kafka-manager.
            """.trimIndent()
        }

        topicSelect(topics, inputId = "kafkaTopic")

        TextField("Topic partition (first partition starts at 0)", inputId = "partition", type = InputType.number, initialValue = "0")
        TextField("Consumer group id", inputId = "groupId")
        TextField("Offset", inputId = "offset", type = InputType.number)

        Button {
            +"Set offset"
        }
    }
}
