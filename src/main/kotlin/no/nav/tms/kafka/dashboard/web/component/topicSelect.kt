package no.nav.tms.kafka.dashboard.web.component

import kotlinx.html.*

fun FlowContent.topicSelect(topics: List<String>, inputId: String) {

    Select("Topic name", inputId) {
        option {
            value = ""
            +"Choose a topic"
        }
        topics.forEach {
            option {
                value = it
                +it
            }
        }
    }
}
