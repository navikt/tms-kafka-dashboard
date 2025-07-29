package no.nav.tms.kafka.dashboard.web.card

import kotlinx.html.FlowContent
import kotlinx.html.InputType
import no.nav.tms.kafka.dashboard.web.component.*

fun FlowContent.lastRecordOffsetCard(topics: List<String>) {

    card(
        cardId = "last-record-offset-card",
        title = "Last record offset"
    ) {
        BodyShort(spacing = true) {
            +"Henter offset til siste record(melding på kafka) som ligger på en topic+partisjon"
        }
        topicSelect(topics, inputId = "kafkaTopic")
        TextField("Topic partition (first partition starts at 0)", inputId = "partition", type = InputType.number, initialValue = "0")
        Button {
            +"Fetch"
        }
    }
}
