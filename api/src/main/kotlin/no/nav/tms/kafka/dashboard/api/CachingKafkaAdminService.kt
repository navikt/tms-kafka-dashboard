package no.nav.tms.kafka.dashboard.api

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.kafka.dashboard.api.search.GuidHelper
import no.nav.tms.kafka.dashboard.api.search.OffsetCache
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.util.*
import kotlin.math.max
import kotlin.math.min

class CachingKafkaAdminService(
    private val kafkaReader: KafkaReader,
    private val offsetCache: OffsetCache
) : KafkaAdminService {

    private val log = KotlinLogging.logger { }

    override fun getAvailableTopics(): List<String> {
        return kafkaReader.appConfig.topics.map { it.topicName }
    }

    override fun getApplications(): List<String> {
        return kafkaReader.appConfig.applications.map { it.name }
    }

    override fun readTopic(request: ReadTopicRequest): List<KafkaRecord> {
        val keySearch = request.filter?.text?.lowercase()?.trim()

        val keyIsGuid = keySearch?.let { GuidHelper.isGuid(it) } ?: false

        val offsetPartitionRange = if(keyIsGuid) {
            offsetCache.findPartitionOffetRangeForKey(request.topicName, keySearch!!)
        } else {
            null
        }

        return if (offsetPartitionRange != null) {
            getWithinRange(
                topicName = request.topicName,
                range = offsetPartitionRange,
                maxRecords = request.maxRecords,
                filter = request.filter
            )
        } else when(request.readFromPosition) {
            ReadFrom.Beginning -> getFromBeginning(
                topicName = request.topicName,
                partition = request.topicPartition,
                maxRecords = request.maxRecords,
                filter = request.filter
            )
            ReadFrom.End -> getFromEnd(
                topicName = request.topicName,
                partition = request.topicPartition,
                maxRecords = request.maxRecords,
                filter = request.filter
            )
            ReadFrom.Offset -> getFromOffset(
                topicName = request.topicName,
                partition = request.topicPartition,
                offset = request.fromOffset!!,
                maxRecords = request.maxRecords,
                filter = request.filter
            )
        }
    }

    private fun getWithinRange(topicName: String, range: OffsetCache.PartitionOffsetRange, maxRecords: Int, filter: RecordFilter?): List<KafkaRecord> {

        val records = mutableListOf<KafkaRecord>()
        val batchSize = max(1000, range.length)

        var currentOffset = range.offsetStart

        while (currentOffset <= range.offsetEnd && records.size < maxRecords) {
            kafkaReader.readFromPartition(
                topicName = topicName,
                partition = range.partition,
                offset = currentOffset,
                maxRecords = batchSize,
            ).let {
                if (filter != null) {
                    filterRecords(filter, it)
                } else {
                    it
                }
            }.let {
                records.addAll(it)
            }

            currentOffset += batchSize
        }

        return records.take(maxRecords)
    }

    private fun getFromBeginning(topicName: String, partition: Int?, maxRecords: Int, filter: RecordFilter?) =
        getFromOffset(topicName, partition, 0L, maxRecords, filter)

    private fun getFromOffset(topicName: String, partition: Int?, offset: Long, maxRecords: Int, filter: RecordFilter?): List<KafkaRecord> {
        val records = mutableListOf<KafkaRecord>()

        val batchSize = if (filter == null) {
            min(maxRecords, 1000)
        } else {
            1000
        }

        val partitions = partition?.let { listOf(it) }
            ?: kafkaReader.getPartitions(topicName).map { it.partition() }

        var currentOffset = offset
        var recordsReadLastIter = Int.MAX_VALUE

        while (records.size < maxRecords && recordsReadLastIter > 0) {
            recordsReadLastIter = 0

            partitions.forEach { currentPartition ->
                kafkaReader.readFromPartition(
                    topicName = topicName,
                    partition = currentPartition,
                    offset = currentOffset,
                    maxRecords = batchSize,
                ).let {
                    recordsReadLastIter += it.size
                    if (filter != null) {
                        filterRecords(filter, it)
                    } else {
                        it
                    }
                }.let {
                    records.addAll(it)
                }
            }

            currentOffset += batchSize
        }

        return records
            .sortedBy { it.timestamp }
            .take(maxRecords)
    }

    private fun getFromEnd(topicName: String, partition: Int?, maxRecords: Int, filter: RecordFilter?): List<KafkaRecord> {

        val records = mutableListOf<KafkaRecord>()

        val batchSize = if (filter == null) {
            min(maxRecords, 1000)
        } else {
            1000
        }

        val partitions = partition?.let { listOf(it)}
            ?: kafkaReader.getPartitions(topicName).map { it.partition() }

        val currentOffsets = partitions
            .associateWith { kafkaReader.lastRecordOffset(topicName, it) - batchSize }
            .toMutableMap()

        var recordsReadLastIter = Int.MAX_VALUE

        while (records.size < maxRecords && recordsReadLastIter > 0) {
            recordsReadLastIter = 0

            partitions.forEach { currentPartition ->

                val currentOffsetForPartition = currentOffsets[currentPartition]!!

                val (readFromOffset, adjustedBatchSize) = if(currentOffsetForPartition < 0) {
                    0L to (batchSize + currentOffsetForPartition).toInt()
                } else {
                    currentOffsetForPartition to batchSize
                }

                val currentOffsetRangeEnd = readFromOffset + adjustedBatchSize

                kafkaReader.readFromPartition(
                    topicName = topicName,
                    partition = currentPartition,
                    offset = readFromOffset,
                    maxRecords = adjustedBatchSize,
                )
                .filter { it.offset < currentOffsetRangeEnd }
                .let {
                    recordsReadLastIter += it.size
                    if (filter != null) {
                        filterRecords(filter, it)
                    } else {
                        it
                    }
                }.let {
                    records.addAll(it)
                }

                log.info { "Iteration: { records: ${records.size}, readThisIter: $recordsReadLastIter, batchSize: $batchSize }" }

                currentOffsets.computeIfPresent(currentPartition) { _, currentOffset -> currentOffset - batchSize }
            }
        }

        return records
            .sortedByDescending { it.timestamp }
            .take(maxRecords)
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
        offsetCache.initTopicInfo()
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
