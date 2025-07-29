package no.nav.tms.kafka.dashboard.web.card

import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.id
import no.nav.tms.kafka.dashboard.web.component.*
import no.nav.tms.kafka.dashboard.web.embedScript
import org.intellij.lang.annotations.Language

fun FlowContent.consumerOffsetsCard(topics: List<String>) = card(
    cardId = "consumer-offsets-card",
    title = "Get consumer offsets",
) {
    embedScript(component)

    BodyShort {
        +"Henter siste commitet offset for alle partisjoner tilhørende en consumer gruppe for en gitt topic"
    }

    TextField(label = "Consumer group id", inputId = "groupId")
    topicSelect(topics, "kafkaTopic")

    Button(onClick = "getConsumerOffsets(this);") {
        +"Fetch"
    }

    div {
        id = "output"
    }
}

@Language("JavaScript")
private val component = """
    function getConsumerOffsets(e) {
        var card = e.parentNode
        var outputSlot = card.querySelector("#output")
    
        fetch('/api/kafka/get-consumer-offsets', {
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            method: 'POST',
            body: JSON.stringify({
                topicName: card.querySelector("#kafkaTopic").value,
                groupId: card.querySelector("#groupId").value
            })
        })
            .then(response => response.json())
            .then(data => displayData(outputSlot, data))
    }
    
    function displayData(slot, data) {
        slot.replaceChildren()

        const list = document.createElement("ul")

        data.forEach((tpo, idx) => {
            const entry =  document.createElement("li")
            entry.setAttribute("key", idx)
            entry.textContent = "Partition=" + tpo.topicPartition + " Offset=" + tpo.offset
            list.appendChild(entry)
        })

        slot.appendChild(list)
    }
"""


