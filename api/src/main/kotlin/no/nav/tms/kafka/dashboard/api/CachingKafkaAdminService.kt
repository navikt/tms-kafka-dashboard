package no.nav.tms.kafka.dashboard.api

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.kafka.dashboard.api.cache.OffsetCache
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.min

class CachingKafkaAdminService(
    private val kafkaReader: KafkaReader,
    private val offsetCache: OffsetCache,
    private val kafkaReadBatchSize: Int
) : KafkaAdminService {

    private val log = KotlinLogging.logger { }

    override fun getAvailableTopics(): List<String> {
        return kafkaReader.appConfig.topics.map { it.topicName }
    }

    override fun getApplications(): List<String> {
        return kafkaReader.appConfig.applications.map { it.name }
    }

    override fun readTopic(request: ReadTopicRequest): List<KafkaRecord> {

        val offsetPartitionRange = if(request.filter?.key != null) {
            offsetCache.findPartitionOffetRangeForKey(request.topicName, request.filter.key)
        } else {
            null
        }

        return if (offsetPartitionRange != null) {
            val records = getWithinOffsetPartitionRange(
                topicName = request.topicName,
                offsetRange = offsetPartitionRange,
                maxRecords = request.maxRecords,
                filter = request.filter,
            )

            when (request.readFromPosition) {
                ReadFrom.End -> records.sortedByDescending { it.timestamp }
                ReadFrom.TimeWindow -> records.filterByTime(request.fromTime, request.toTime)
                else -> records
            }
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
            ReadFrom.TimeWindow -> getWithinTimeRange(
                topicName = request.topicName,
                fromTime = request.fromTime,
                toTime = request.toTime,
                maxRecords = request.maxRecords,
                filter = request.filter
            )
        }
    }

    private fun getWithinOffsetPartitionRange(topicName: String, offsetRange: OffsetCache.PartitionOffsetRange, maxRecords: Int, filter: RecordFilter?): List<KafkaRecord> {

        log.info { "Polling ${offsetRange.length} records from topic [$topicName] { partition: ${offsetRange.partition}, start: ${offsetRange.offsetStart} }." }

        val records = mutableListOf<KafkaRecord>()
        val batchSize = min(kafkaReadBatchSize, offsetRange.length)

        var currentOffset = offsetRange.offsetStart

        while (currentOffset <= offsetRange.offsetEnd && records.size < maxRecords) {
            val batch = kafkaReader.readFromPartition(
                topicName = topicName,
                partition = offsetRange.partition,
                offset = currentOffset,
                maxRecords = batchSize,
            )

            records.addAll(batch.filteredBy(filter))

            currentOffset += batchSize
        }

        return records.take(maxRecords)
    }

    private fun getWithinTimeRange(topicName: String, fromTime: ZonedDateTime?, toTime: ZonedDateTime?, maxRecords: Int, filter: RecordFilter?): List<KafkaRecord> {

        val ranges = offsetCache.findPartitionOffetRangesForTime(
            topicName,
            fromTime = fromTime ?: ZonedDateTime.now().minusDays(30),
            toTime = toTime ?: ZonedDateTime.now()
        )

        return ranges.flatMap {
            getWithinOffsetPartitionRange(topicName, it, maxRecords, filter)
        }
            .sortedBy { it.timestamp }
            .take(maxRecords)
    }

    private fun getFromBeginning(topicName: String, partition: Int?, maxRecords: Int, filter: RecordFilter?) =
        getFromOffset(topicName, partition, 0L, maxRecords, filter)

    private fun getFromOffset(topicName: String, partition: Int?, offset: Long, maxRecords: Int, filter: RecordFilter?): List<KafkaRecord> {
        val records = mutableListOf<KafkaRecord>()
        val batchSize = batchSize(maxRecords, filter)
        val partitions = partitions(topicName, partition)

        var currentOffset = offset
        var recordsReadLastIter = Int.MAX_VALUE

        while (records.size < maxRecords && recordsReadLastIter > 0) {
            recordsReadLastIter = 0

            partitions.forEach { currentPartition ->
                val batch = kafkaReader.readFromPartition(
                    topicName = topicName,
                    partition = currentPartition,
                    offset = currentOffset,
                    maxRecords = batchSize,
                )

                recordsReadLastIter += batch.size
                records.addAll(batch.filteredBy(filter))
            }

            currentOffset += batchSize
        }

        return records
            .sortedBy { it.timestamp }
            .take(maxRecords)
    }

    private fun getFromEnd(topicName: String, partition: Int?, maxRecords: Int, filter: RecordFilter?): List<KafkaRecord> {

        val records = mutableListOf<KafkaRecord>()
        val batchSize = batchSize(maxRecords, filter)
        val partitions = partitions(topicName, partition)

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

                val batch = kafkaReader.readFromPartition(
                    topicName = topicName,
                    partition = currentPartition,
                    offset = readFromOffset,
                    maxRecords = adjustedBatchSize,
                ).filter { it.offset < currentOffsetRangeEnd }

                recordsReadLastIter += batch.size
                records.addAll(batch.filteredBy(filter))

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

    override fun initCache(resetData: Boolean) {
        if (resetData) {
            log.info { "Deleting existing offset data" }
            offsetCache.resetCache()
        }

        offsetCache.initTopicInfo()
        offsetCache.start()
    }

    companion object {

        fun filterRecords(
            filter: RecordFilter,
            records: List<KafkaRecord>
        ): List<KafkaRecord> {

            return records
                .filterByContent(filter.key, exactMatch = true, KafkaRecord::key)
                .filterByContent(filter.value, exactMatch = false, KafkaRecord::value)
        }

        private fun List<KafkaRecord>.filterByTime(
            start: ZonedDateTime?,
            end: ZonedDateTime?
        ): List<KafkaRecord> =
            filter {
                (start == null || start < it.timestamp) &&
                    (end == null || end > it.timestamp)
            }

        private fun List<KafkaRecord>.filterByContent(
            filterText: String?,
            exactMatch: Boolean,
            fieldProvider: (KafkaRecord) -> String?
        ): List<KafkaRecord> {
            val normalizedFilter = filterText?.let(::insensitiveText) ?: return this

            return filter { record ->
                val content = fieldProvider(record)?.let(::insensitiveText)
                    ?: return@filter false

                if (exactMatch) {
                    content == normalizedFilter
                } else {
                    content.contains(normalizedFilter)
                }
            }
        }

        private fun insensitiveText(str: String): String {
            return str.lowercase(Locale.getDefault())
                .replace(" ", "")
                .replace("\n", "")
        }
    }

    private fun batchSize(maxRecords: Int, filter: RecordFilter?): Int =
        if (filter == null) min(maxRecords, kafkaReadBatchSize) else kafkaReadBatchSize

    private fun partitions(topicName: String, partition: Int?): List<Int> =
        partition?.let { listOf(it) }
            ?: kafkaReader.getPartitions(topicName).map { it.partition() }

    private fun List<KafkaRecord>.filteredBy(filter: RecordFilter?): List<KafkaRecord> =
        if (filter == null) this else filterRecords(filter, this)
}
