package no.nav.tms.kafka.dashboard.web.component

import kotlinx.html.FlowContent
import kotlinx.html.*
import no.nav.tms.kafka.dashboard.web.component.ClassNames.Companion.cls

fun FlowContent.card(
    cardId: String,
    title: String? = null,
    content: FlowContent.() -> Unit
) {
    div("card") {
        id = cardId
        if (title != null) {
            h1("card__title navds-heading navdsheading--large") {
                +title
            }
        }
        div("card__content") {
            content()
        }
    }
}
