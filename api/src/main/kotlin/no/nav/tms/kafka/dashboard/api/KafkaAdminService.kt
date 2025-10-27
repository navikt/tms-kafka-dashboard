package no.nav.tms.kafka.dashboard.api

import no.nav.tms.kafka.dashboard.KafkaAppConfig
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.time.ZonedDateTime

interface KafkaAdminService {

    fun getAvailableTopics(): List<String>

    fun getApplications(): List<String>

    fun readTopic(request: ReadTopicRequest): List<KafkaRecord>

    fun getLastRecordOffset(request: GetLastRecordOffsetRequest): Long

    fun getConsumerOffsets(request: GetConsumerOffsetsRequest): Map<TopicPartition, OffsetAndMetadata>

    fun setConsumerOffset(request: SetConsumerOffsetRequest)

    fun initCache(resetData: Boolean)
}

class KafkaAdminServiceMock(
    private val appConfig: KafkaAppConfig
) : KafkaAdminService {

    override fun getAvailableTopics(): List<String> {
        return appConfig.topics.map { it.topicName }
    }

    override fun getApplications(): List<String> {
        return appConfig.applications.map { it.name }
    }

    override fun readTopic(request: ReadTopicRequest): List<KafkaRecord> {
        val partitionRange = if (request.topicPartition == null) {
            0..3
        } else {
            request.topicPartition..request.topicPartition
        }

        val fromOffset = request.fromOffset ?: 0

        val offsetRange = fromOffset until (fromOffset + request.maxRecords)

        return partitionRange.flatMap {  partition ->
            offsetRange.map { offset ->
                KafkaRecord(
                    partition,
                    "$partition-$offset",
                    """{ "partition": $partition, "offset": $offset }""",
                    emptyList(),
                    ZonedDateTime.now(),
                    offset
                )
            }
        }.let { records ->
            request.filter?.let {
                CachingKafkaAdminService.filterRecords(request.filter, records)
            } ?: records
        }
    }

    override fun getLastRecordOffset(request: GetLastRecordOffsetRequest): Long {
        return request.topicPartition * 1000L
    }

    override fun getConsumerOffsets(request: GetConsumerOffsetsRequest): Map<TopicPartition, OffsetAndMetadata> {
        return (0..3).map { partition->
            TopicPartition(request.topicName, partition) to OffsetAndMetadata(partition * 1000L)
        }.toMap()
    }

    override fun setConsumerOffset(request: SetConsumerOffsetRequest) {
    }

    override fun initCache(resetData: Boolean) {

    }
}
