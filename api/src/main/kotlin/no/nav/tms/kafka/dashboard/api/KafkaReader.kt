package no.nav.tms.kafka.dashboard.api

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.kafka.dashboard.KafkaAppConfig
import no.nav.tms.kafka.dashboard.TopicConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*
import kotlin.math.max
import kotlin.math.min

class KafkaReader(val appConfig: KafkaAppConfig) {

    private val log = KotlinLogging.logger { }

    fun readFromPartition(
        topicName: String,
        partition: Int,
        offset: Long,
        maxRecords: Int
    ): List<KafkaRecord> {
        if (maxRecords < 1) {
            return emptyList()
        }

        val maxPollRecords = min(500, maxRecords)

        val kafkaConsumer = createKafkaConsumerForTopic(null, topicName, maxPollRecords)
        val kafkaRecords = mutableListOf<KafkaRecord>()

        kafkaConsumer.use { consumer ->
            val topicPartition = TopicPartition(topicName, partition)

            consumer.assign(listOf(topicPartition))
            consumer.seek(topicPartition, offset)

            while (kafkaRecords.size < maxRecords) {
                val consumerRecords = consumer.poll(Duration.ofMillis(500))

                log.info { "Polled ${consumerRecords.count()} records." }

                // No more records to consume right now
                if (consumerRecords.isEmpty) {
                    break
                }

                consumerRecords.records(topicName).map {
                    val stringRecord = ConsumerRecordMapper.mapConsumerRecord(it)
                    DTOMappers.toKafkaRecordHeader(stringRecord)
                }.let {
                    kafkaRecords.addAll(it)
                }
            }

            log.info { "Read ${kafkaRecords.size} records for request {topicName: $topicName, partition: $partition, offset: $offset, maxRecords: $maxRecords}" }

            return kafkaRecords.take(maxRecords)
        }
    }

    fun lastRecordOffset(topicName: String, partition: Int): Long {
        val topicPartition = TopicPartition(topicName, partition)
        val kafkaConsumer = createKafkaConsumerForTopic(null, topicName)

        kafkaConsumer.use { consumer ->
            consumer.assign(Collections.singleton(topicPartition))
            consumer.seekToEnd(Collections.singleton(topicPartition))

            return consumer.position(topicPartition)
        }
    }

    fun consumerOffsets(topicName: String, groupId: String): Map<TopicPartition, OffsetAndMetadata> {
        val kafkaConsumer = createKafkaConsumerForTopic(groupId, topicName)

        return kafkaConsumer.use { consumer ->
            val topicPartitions = consumer.partitionsFor(topicName)
                .map { TopicPartition(it.topic(), it.partition()) }
                .toSet()

            consumer.committed(topicPartitions)
                .apply { values.removeIf { it == null } }
        }
    }

    fun getPartitions(topicName: String): List<TopicPartition> {

        return createKafkaConsumerForTopic(null, topicName).use { consumer ->
            consumer.partitionsFor(topicName)
                .map { TopicPartition(it.topic(), it.partition()) }
        }
    }

    fun setConsumerOffset(topicName: String, partition: Int, groupId: String, offset: Long) {
        val topicPartition = TopicPartition(topicName, partition)
        val kafkaConsumer = createKafkaConsumerForTopic(groupId, topicName)

        kafkaConsumer.use { consumer ->
            consumer.assign(listOf(topicPartition))
            consumer.seek(topicPartition, offset)
            consumer.commitSync()
        }
    }

    private fun createKafkaConsumerForTopic(
        consumerGroupId: String?,
        topicName: String,
        maxPollRecords: Int = 100
    ): KafkaConsumer<Any?, Any?> {
        val topicConfig = findTopicConfigOrThrow(topicName)
        val properties = createPropertiesForTopic(consumerGroupId, topicConfig, maxPollRecords)

        return KafkaConsumer(properties)
    }

    private fun findTopicConfigOrThrow(topicName: String): TopicConfig {
        return appConfig.topics.find { it.topicName == topicName }
            ?: throw IllegalArgumentException("Could not find config for topic")
    }

    private fun createPropertiesForTopic(consumerGroupId: String?, topicConfig: TopicConfig, maxPollRecords: Int): Properties {
        val keyDesType = topicConfig.keyDeserializerType
        val valueDesType = topicConfig.valueDeserializerType

        val properties = KafkaPropertiesFactory.createKafkaConsumerProperties(keyDesType, valueDesType, maxPollRecords)

        if (consumerGroupId != null) {
            properties[ConsumerConfig.GROUP_ID_CONFIG] = consumerGroupId
        }

        return properties
    }
}

data class KafkaRecord(
    val partition: Int,
    val key: String?,
    val value: String?,
    val headers: List<KafkaRecordHeader>,
    val timestamp: ZonedDateTime,
    val offset: Long
)

data class KafkaRecordHeader(
    val name: String,
    val value: String
)
