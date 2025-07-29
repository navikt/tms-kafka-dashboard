package no.nav.tms.kafka.dashboard.web.component

import kotlinx.html.FlowContent
import kotlinx.html.*
import no.nav.tms.kafka.dashboard.web.component.ClassNames.Companion.cls

@DslMarker
annotation class AkselComponent

@AkselComponent
fun FlowContent.BodyShort(
    spacing: Boolean = false,
    contentClass: String? = null,
    content: FlowContent.() -> Unit
) {
    val classNames = cls("navds-body-short")
        .addOptional("navds-typo--spacing", spacing)
        .add(contentClass)

    p("$classNames") {
        content()
    }
}

@AkselComponent
fun FlowContent.TextField(
    label: String,
    inputId: String,
    initialValue: String = "",
    type: InputType = InputType.text
) {
    div("navds-form-field navds-form-field--medium") {
        label("navds-form-field__label navds-label") {
            htmlFor = inputId
            +label
        }
        input(type = type, classes = "navds-text-field__input navds-body-short navds-body-medium") {
            id = inputId
            value = initialValue
        }
    }
}

@AkselComponent
fun FlowContent.Button(
    onClick: String? = null,
    variant: String = "tertiary",
    content: FlowContent.() -> Unit
) {
    button(classes = "navds-button navds-button--medium navds-button--$variant") {
        onClick?.let {
            this.onClick = it
        }
        span("navds-label") {
            content()
        }
    }
}

@AkselComponent
fun FlowContent.Select(
    label: String,
    inputId: String,
    content: SELECT.() -> Unit
) {
    div("navds-form-field navds-form-field--medium") {
        label("navds-form-field__label navds-label") {
            htmlFor = inputId
            +label
        }
        div("navds-select__container") {
            select("navds-select__input navds-body-short navds-body--medium") {
                id = inputId
                content()
            }
        }
    }
}

class ClassNames(
    private val classNames: MutableSet<String>
) {
    fun add(vararg className: String?): ClassNames {
        className.filterNotNull()
            .forEach { classNames.add(it) }

        return this
    }

    fun addOptional(className: String, selector: Boolean): ClassNames {
        if (selector) {
            classNames.add(className)
        }

        return this
    }

    override fun toString(): String {
        return classNames.joinToString(" ")
    }

    companion object {
        fun cls(vararg names: String): ClassNames {
            return ClassNames(names.toMutableSet())
        }
    }
}
