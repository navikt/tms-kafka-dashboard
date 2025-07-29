package no.nav.tms.kafka.dashboard.web

import kotlinx.html.FlowContent
import kotlinx.html.script
import kotlinx.html.unsafe

fun FlowContent.embedScript(script: String) {
    script {
        unsafe {
            +script
        }
    }
}

fun FlowContent.embedScript(scriptBlock: () -> String) {
    script {
        unsafe {
            +scriptBlock()
        }
    }
}
