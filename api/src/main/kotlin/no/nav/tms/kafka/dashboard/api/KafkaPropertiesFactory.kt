package no.nav.tms.kafka.dashboard.api

import no.nav.tms.common.util.config.StringEnvVar
import no.nav.tms.kafka.dashboard.DeserializerType
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.*
import java.util.*

object KafkaPropertiesFactory {

    private val environment = EnvWrapper.getEnv()

    private const val KAFKA_SCHEMA_REGISTRY: String = "KAFKA_SCHEMA_REGISTRY"
    private const val KAFKA_SCHEMA_REGISTRY_USER: String = "KAFKA_SCHEMA_REGISTRY_USER"
    private const val KAFKA_SCHEMA_REGISTRY_PASSWORD: String = "KAFKA_SCHEMA_REGISTRY_PASSWORD"

    fun createAivenConsumerProperties(
        keyDeserializerType: DeserializerType,
        valueDeserializerType: DeserializerType
    ) = Properties().apply {

        configureBrokers(environment)
        configureSecurity(environment)

        put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, MAX_KAFKA_RECORDS)
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300_000)

        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, findDeserializer(keyDeserializerType).name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, findDeserializer(valueDeserializerType).name)
    }

    private fun Properties.configureBrokers(env: Map<String, String>) {
        val brokers = env.getValue("KAFKA_BROKERS")
            .split(',')
            .map(String::trim)

        require(brokers.isNotEmpty()) { "Kafka brokers must not be empty" }

        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, brokers)
    }

    private fun Properties.configureSecurity(env: Map<String, String>) {
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name)
        put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
        put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
        put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, env.getValue("KAFKA_TRUSTSTORE_PATH"))
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, env.getValue("KAFKA_CREDSTORE_PASSWORD"))
        put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, env.getValue("KAFKA_KEYSTORE_PATH"))
        put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, env.getValue("KAFKA_CREDSTORE_PASSWORD"))
    }

    private fun findDeserializer(deserializerType: DeserializerType): Class<*> {
        return when (deserializerType) {
            DeserializerType.STRING -> StringDeserializer::class.java
            DeserializerType.DOUBLE -> DoubleDeserializer::class.java
            DeserializerType.FLOAT -> FloatDeserializer::class.java
            DeserializerType.INTEGER -> IntegerDeserializer::class.java
            DeserializerType.LONG -> LongDeserializer::class.java
            DeserializerType.SHORT -> ShortDeserializer::class.java
            DeserializerType.UUID -> UUIDDeserializer::class.java
        }
    }

    object EnvWrapper {
        fun getEnv() = System.getenv()
    }

}
