package no.nav.tms.kafka.dashboard.api

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.record.TimestampType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object DTOMappers {

    fun toTopicWithOffset(topicPartition: TopicPartition, offsetAndMetadata: OffsetAndMetadata): TopicWithOffset {
        return TopicWithOffset(
            topicName = topicPartition.topic(),
            topicPartition = topicPartition.partition(),
            offset = offsetAndMetadata.offset()
        )
    }

    fun toKafkaRecordHeader(consumerRecord: ConsumerRecord<String, String>): KafkaRecord {
        return KafkaRecord(
            partition = consumerRecord.partition(),
            key = consumerRecord.key(),
            value = consumerRecord.value(),
            timestamp = consumerRecord.timestampZ(),
            headers = consumerRecord.headers().map { toRecordHeader(it) },
            offset = consumerRecord.offset()
        )
    }

    private fun toRecordHeader(header: Header): KafkaRecordHeader {
        return KafkaRecordHeader(
            name = header.key(),
            value = String(header.value())
        )
    }



    private fun ConsumerRecord<*, *>.timestampZ(): ZonedDateTime = when (timestampType()) {
        TimestampType.LOG_APPEND_TIME, TimestampType.CREATE_TIME -> {
            Instant.ofEpochMilli(timestamp()).let { ZonedDateTime.ofInstant(it, ZoneId.of("Z")) }
        }
        else -> throw IllegalArgumentException("Fant ikke timestamp for record")
    }
}
