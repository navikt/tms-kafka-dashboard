package no.nav.tms.kafka.dashboard

data class KafkaAppConfig (
    val topics: List<TopicConfig> = emptyList(),
    val applications: List<ApplicationConfig> = emptyList()
) {
    init {
        val appsWithUnknownTopics = applications.filter { app ->
            app.topics.any { topic ->
                topics.map { it.name }.contains(topic).not()
            }
        }

        require(appsWithUnknownTopics.isEmpty()) {
            val invalidApps = appsWithUnknownTopics.joinToString { it.name }
            "Én eller flere applikasjoner peker på topics som ikke er definert: [$invalidApps]"
        }
    }
}

data class TopicConfig(
    val name: String,
    val keyDeserializerType: DeserializerType = DeserializerType.STRING,
    val valueDeserializerType: DeserializerType = DeserializerType.STRING
)

data class ApplicationConfig(
    val name: String,
    val groupId: String,
    val topics: List<String>
)

enum class DeserializerType {
    STRING,
    DOUBLE,
    FLOAT,
    INTEGER,
    LONG,
    SHORT,
    UUID,
    AVRO
}

