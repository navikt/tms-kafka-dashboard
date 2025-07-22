package no.nav.tms.kafka.dashboard.utils

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import no.nav.tms.common.util.config.StringEnvVar.getEnvVar
import no.nav.tms.kafka.dashboard.controller.MAX_KAFKA_RECORDS
import no.nav.tms.kafka.dashboard.domain.DeserializerType
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig.*
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

        if (keyDeserializerType == DeserializerType.AVRO || valueDeserializerType == DeserializerType.AVRO) {
            configureAvro(environment)
        }

        put(MAX_POLL_RECORDS_CONFIG, MAX_KAFKA_RECORDS)
        put(ENABLE_AUTO_COMMIT_CONFIG, "false")
        put(AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(MAX_POLL_INTERVAL_MS_CONFIG, 300_000)

        put(KEY_DESERIALIZER_CLASS_CONFIG, findDeserializer(keyDeserializerType).name)
        put(VALUE_DESERIALIZER_CLASS_CONFIG, findDeserializer(valueDeserializerType).name)
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

    private fun Properties.configureAvro(env: Map<String, String>) {
        val schemaRegistryUrl = getEnvVar(KAFKA_SCHEMA_REGISTRY)
        val schemaRegistryUsername = getEnvVar(KAFKA_SCHEMA_REGISTRY_USER)
        val schemaRegistryPassword = getEnvVar(KAFKA_SCHEMA_REGISTRY_PASSWORD)

        put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl)
        put(KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO")
        put(KafkaAvroSerializerConfig.USER_INFO_CONFIG, "$schemaRegistryUsername:$schemaRegistryPassword")
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
            DeserializerType.AVRO -> KafkaAvroDeserializer::class.java
        }
    }

    object EnvWrapper {
        fun getEnv() = System.getenv()
    }

}
