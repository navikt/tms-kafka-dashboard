package no.nav.tms.kafka.dashboard.api

import com.squareup.wire.internal.boxedOneOfKeysFieldName
import no.nav.tms.kafka.dashboard.api.search.GuidHelper
import no.nav.tms.kafka.dashboard.api.search.OffsetCache
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.util.*

class CachingKafkaAdminService(
    private val kafkaReader: KafkaReader,
    private val offsetCache: OffsetCache
) : KafkaAdminService {

    override fun getAvailableTopics(): List<String> {
        return kafkaReader.appConfig.topics.map { it.topicName }
    }

    override fun getApplications(): List<String> {
        return kafkaReader.appConfig.applications.map { it.name }
    }

    override fun readTopic(request: ReadTopicRequest): List<KafkaRecord> {
        val keySearch = request.filter?.text?.lowercase()?.trim()

        val keyIsGuid = keySearch?.let { GuidHelper.isGuid(it) } ?: false

        val offsetPartitionOverride = if(keyIsGuid) {
            offsetCache.findFirstPartitionOffset(request.topicName, keySearch!!)
        } else {
            null
        }

        val records = when {
            offsetPartitionOverride != null -> kafkaReader.readFromPartition(
                    topicName = request.topicName,
                    partition = offsetPartitionOverride.partition,
                    offset = offsetPartitionOverride.offset,
                    maxRecords = request.maxRecords,
                )
            request.topicAllPartitions -> kafkaReader.readFromAllPartitions(
                    topicName = request.topicName,
                    maxRecords = request.maxRecords,
                    offsets = emptyMap()
                )
            else -> kafkaReader.readFromPartition(
                topicName = request.topicName,
                partition = request.topicPartition,
                offset = request.fromOffset,
                maxRecords = request.maxRecords,
            )
        }

        return if(request.filter != null) {
            filterRecords(request.filter, records)
        } else {
            records
        }
    }

    override fun getLastRecordOffset(request: GetLastRecordOffsetRequest): Long {
        return kafkaReader.lastRecordOffset(request.topicName, request.topicPartition)
    }

    override fun getConsumerOffsets(request: GetConsumerOffsetsRequest): Map<TopicPartition, OffsetAndMetadata> {
        return kafkaReader.consumerOffsets(request.topicName, request.groupId)
    }

    override fun setConsumerOffset(request: SetConsumerOffsetRequest) {
        kafkaReader.setConsumerOffset(
            groupId = request.groupId,
            topicName = request.topicName,
            partition = request.topicPartition,
            offset = request.offset,
        )
    }

    override fun initCache() {
        offsetCache.start()
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

                keyMatches || valueMatches
            }
        }

        private fun insensitiveText(str: String): String {
            return str.lowercase(Locale.getDefault())
                .replace(" ", "")
                .replace("\n", "")
        }
    }
}
