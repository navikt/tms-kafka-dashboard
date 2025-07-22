package no.nav.tms.kafka.dashboard.service

import no.nav.tms.kafka.dashboard.controller.*
import no.nav.tms.kafka.dashboard.domain.KafkaAppConfig
import no.nav.tms.kafka.dashboard.domain.KafkaRecord
import no.nav.tms.kafka.dashboard.domain.TopicConfig
import no.nav.tms.kafka.dashboard.utils.ConsumerRecordMapper
import no.nav.tms.kafka.dashboard.utils.DTOMappers.toKafkaRecordHeader
import no.nav.tms.kafka.dashboard.utils.KafkaPropertiesFactory.createAivenConsumerProperties
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.util.*
import java.util.Collections.singleton
import kotlin.math.max

class KafkaAdminService(
    private val appConfig: KafkaAppConfig,
) {

    fun getAvailableTopics(): List<String> {
        return appConfig.topics.map { it.name }
    }

    fun getApplications(): List<String> {
        return appConfig.applications.map { it.name }
    }

    fun readTopic(request: ReadTopicRequest): List<KafkaRecord> {
        val kafkaConsumer = createKafkaConsumerForTopic(null, request.topicName)
        val kafkaRecords = ArrayList<KafkaRecord>()

        kafkaConsumer.use { consumer ->
            val topicPartitions: List<TopicPartition> = if (request.topicAllPartitions)
                consumer.partitionsFor(request.topicName).map { TopicPartition(it.topic(), it.partition()) }
            else
                listOf(TopicPartition(request.topicName, request.topicPartition))
            val fromOffset = max(0, request.fromOffset)
            val maxRecords = request.maxRecords

            consumer.assign(topicPartitions)
            topicPartitions.forEach { consumer.seek(it, fromOffset) }

            while (kafkaRecords.size < maxRecords) {
                val consumerRecords = consumer.poll(Duration.ofSeconds(1))

                // No more records to consume right now
                if (consumerRecords.isEmpty) {
                    break
                }

                val kafkaRecordsBatch = consumerRecords.records(request.topicName).map {
                    val stringRecord = ConsumerRecordMapper.mapConsumerRecord(it)
                    toKafkaRecordHeader(stringRecord)
                }

                val filteredRecords = filterRecords(request.filter, kafkaRecordsBatch)

                kafkaRecords.addAll(filteredRecords)
            }

            return kafkaRecords.take(request.maxRecords)
        }
    }

    fun getLastRecordOffset(request: GetLastRecordOffsetRequest): Long {
        val topicPartition = TopicPartition(request.topicName, request.topicPartition)
        val kafkaConsumer = createKafkaConsumerForTopic(null, request.topicName)

        kafkaConsumer.use { consumer ->
            consumer.assign(singleton(topicPartition))
            consumer.seekToEnd(singleton(topicPartition))

            return consumer.position(topicPartition)
        }
    }

    fun getConsumerOffsets(request: GetConsumerOffsetsRequest): Map<TopicPartition, OffsetAndMetadata> {
        val kafkaConsumer = createKafkaConsumerForTopic(request.groupId, request.topicName)

        kafkaConsumer.use { consumer ->
            val topicPartitions = consumer.partitionsFor(request.topicName)
                .map { TopicPartition(it.topic(), it.partition()) }
                .toSet()

            val committed = consumer.committed(topicPartitions)

            committed.values.removeIf { it == null }

            return committed
        }
    }

    fun setConsumerOffset(request: SetConsumerOffsetRequest) {
        val topicPartition = TopicPartition(request.topicName, request.topicPartition)
        val kafkaConsumer = createKafkaConsumerForTopic(request.groupId, request.topicName)

        kafkaConsumer.use { consumer ->
            consumer.assign(listOf(topicPartition))
            consumer.seek(topicPartition, request.offset)
            consumer.commitSync()
        }
    }

    private fun createKafkaConsumerForTopic(
        consumerGroupId: String?,
        topicName: String
    ): KafkaConsumer<Any?, Any?> {
        val topicConfig = findTopicConfigOrThrow(topicName)
        val properties = createPropertiesForTopic(consumerGroupId, topicConfig)

        return KafkaConsumer(properties)
    }

    private fun findTopicConfigOrThrow(topicName: String): TopicConfig {
        return appConfig.topics.find { it.name == topicName }
            ?: throw IllegalArgumentException("Could not find config for topic")
    }

    private fun createPropertiesForTopic(consumerGroupId: String?, topicConfig: TopicConfig): Properties {
        val keyDesType = topicConfig.keyDeserializerType
        val valueDesType = topicConfig.valueDeserializerType

        val properties = createAivenConsumerProperties(keyDesType, valueDesType)

        if (consumerGroupId != null) {
            properties[ConsumerConfig.GROUP_ID_CONFIG] = consumerGroupId
        }

        return properties
    }

    companion object {

        fun filterRecords(
            filter: RecordFilter?,
            records: List<KafkaRecord>
        ): List<KafkaRecord> {
            if (filter == null || filter.text.isNullOrBlank()) {
                return records
            }

            return records.filter {
                val filterText = insensitiveText(filter.text)
                val keyMatches = it.key != null && insensitiveText(it.key).contains(filterText)
                val valueMatches = it.value != null && insensitiveText(it.value).contains(filterText)

                return@filter keyMatches || valueMatches
            }
        }

        private fun insensitiveText(str: String): String {
            return str.lowercase(Locale.getDefault())
                .replace(" ", "")
                .replace("\n", "")
        }

    }

}
