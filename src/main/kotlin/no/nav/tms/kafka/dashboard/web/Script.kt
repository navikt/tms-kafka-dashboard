package no.nav.tms.kafka.dashboard.web

object Script {
    private var isRendered = false
    private lateinit var scriptFile: String

    private val builder = StringBuilder()

    fun append(script: String) {
        builder.appendLine(script)
        builder.appendLine()
    }

    fun get(): String {
        return if (isRendered) {
            scriptFile
        } else {
            render()
        }
    }

    private fun render(): String {
        scriptFile = builder.toString()
        isRendered = true
        return scriptFile
    }
}
