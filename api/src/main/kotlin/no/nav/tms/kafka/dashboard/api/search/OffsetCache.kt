package no.nav.tms.kafka.dashboard.api.search

import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.queryOf
import no.nav.tms.common.util.scheduling.PeriodicJob
import no.nav.tms.kafka.dashboard.api.KafkaReader
import no.nav.tms.kafka.dashboard.api.KafkaRecord
import no.nav.tms.kafka.dashboard.api.database.Database
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean

class OffsetCache(
    private val database: Database,
    private val kafkaReader: KafkaReader,
    interval: Duration = Duration.ofMinutes(1),
    batchSize: Int = 1000,
    retention: Duration = Duration.ofDays(14)
): PeriodicJob(interval) {

    private val log = KotlinLogging.logger {}

    private lateinit var topics: List<TopicInfo>
    private lateinit var topicIds: Map<String, Int>

    private val isUpdating = AtomicBoolean(false)
    private val isReady = AtomicBoolean(false)

    fun findFirstPartitionOffset(topicName: String, recordKey: String): PartitionOffset? {
        if (!isReady.get()) {
            return null
        }

        while (isUpdating.get()) {
            Thread.sleep(20)
        }

        return database.singleOrNull {
            queryOf(
                "select recordPartition, recordOffset from offset_cache where recordKey = :recordKey and topicId = :topicId order by createdAt limit 1",
                mapOf(
                    "topicId" to topicId(topicName),
                    "recordKey" to recordKey
                )
            ).map {
                PartitionOffset(
                    partition = it.int("recordPartition"),
                    offset = it.long("recordOffset")
                )
            }.asSingle
        }
    }

    fun initTopicInfo() {
        topics = kafkaReader.appConfig.topics.map { config ->
            val partitions = kafkaReader.getPartitions(config.topicName)
                .map { it.partition() }

            TopicInfo(config.topicName, partitions)
        }

         topicIds = topics.associate { topic ->
            topic.topicName to insertTopic(topic.topicName)
        }

        log.info { "Initialized ${topics.size} topics" }
    }

    override val job = initializeJob {
        isUpdating.set(true)

        if (isReady.get()) {
            log.info { "Updating offset cache" }
        } else {
            log.info { "Initializing offset cache" }
        }

        ejectEntriesBefore(ZonedDateTime.now() - retention)

        val topicNames = kafkaReader.appConfig.topics.map { it.topicName }

        topicNames.forEach { topicName ->

            log.info { "Cache - Updating topic [$topicName]" }

            val partitions = kafkaReader.getPartitions(topicName).map { it.partition() }

            partitions.forEach { partition ->

                log.info { "Cache - Updating partition [$partition]" }

                var partitionComplete = false

                var totalFilled = 0

                while (!partitionComplete) {
                    val filledThisIter = fillNext(topicName, partition, batchSize)

                    totalFilled += filledThisIter

                    partitionComplete = filledThisIter < batchSize
                }

                log.info { "Filled $totalFilled offsets for topic/partition [$topicName, $partition]" }
            }
        }

        log.info { "Cache fill complete." }

        isUpdating.set(false)
        isReady.set(true)
    }

    private fun fillNext(
        topicName: String,
        partition: Int,
        batchSize: Int
    ): Int {
        val start = lastCachedOffset(topicName, partition) ?: 0L

        val records = kafkaReader.readFromPartition(topicName, partition, start, batchSize)

        fill(topicName, records)

        return records.size
    }

    private fun fill(topic: String, records: List<KafkaRecord>) {

        if (records.isEmpty()) {
            return
        }

        require(records.map { it.partition }.distinct().size == 1) { "Må fylle opp én partisjon om gangen." }

        val topicId = topicId(topic)

        val entries = records.map { record ->

            CacheEntry(
                topicId = topicId,
                key = record.key,
                partition = record.partition,
                offset = record.offset,
                createdAt = record.timestamp
            )
        }

        entries.forEach(::insertEntry)

        entries.maxBy { it.offset }.let {
            updateLastCachedOffset(topicId, it.partition, it.offset)
        }
    }

    private fun lastCachedOffset(topicName: String, partition: Int): Long? {
        return database.singleOrNull {
            queryOf(
                "select lastOffset from last_cached_offset where topicId = :topicId and partition = :partition",
                mapOf(
                    "topicId" to topicId(topicName),
                    "partition" to partition
                )
            ).map {
                it.long("lastOffset")
            }.asSingle
        }
    }

    private fun ejectEntriesBefore(cutoff: ZonedDateTime) {
        queryOf(
            "delete from offset_cache where createdAt < :cutoff",
            mapOf("cutoff" to cutoff)
        )
    }

    private fun updateLastCachedOffset(topicId: Int, partition: Int, offset: Long) {
        database.update {
            queryOf(
                """
                      merge into last_cached_offset as lco
                        using (values :topicId, :partition, :lastOffset) tmp (topicId, partition, lastOffset) 
                      on (lco.topicId = tmp.topicId and lco.partition = tmp.partition)
                        when matched then update set lco.lastOffset = tmp.lastOffset
                        when not matched then insert (topicId, partition, lastOffset) values (tmp.topicId, tmp.partition, tmp.lastOffset)
                """,
                mapOf(
                    "topicId" to topicId,
                    "partition" to partition,
                    "lastOffset" to offset
                )
            )
        }
    }

    private fun insertTopic(topic: String): Int {
        database.insert {
            queryOf(
                "insert into topic(name) values (:name)",
                mapOf("name" to topic)
            )
        }

        return database.single {
            queryOf(
                "select id from topic where name = :name",
                mapOf("name" to topic)
            ).map {
                it.int("id")
            }.asSingle
        }
    }

    private fun insertEntry(entry: CacheEntry) {
        database.insert { queryOf("""
            insert into offset_cache(
                topicId,
                recordKey, 
                recordPartition,
                recordOffset,
                createdAt
            ) values (
                :topicId,
                :recordKey,
                :partition,
                :offset,
                :createdAt
            )
        """,
            mapOf(
                "topicId" to entry.topicId,
                "recordKey" to entry.key,
                "partition" to entry.partition,
                "offset" to entry.offset,
                "createdAt" to entry.createdAt
            )
        ) }
    }

    private fun topicId(topicName: String): Int {
        return topicIds[topicName] ?: throw IllegalStateException("Fant ikke topicId for topic $topicName")
    }

    private data class CacheEntry(
        val topicId: Int,
        val key: String?,
        val partition: Int,
        val offset: Long,
        val createdAt: ZonedDateTime
    )

    private data class TopicInfo(
        val topicName: String,
        val partitions: List<Int>
    )

    data class PartitionOffset(
        val partition: Int,
        val offset: Long
    )
}


