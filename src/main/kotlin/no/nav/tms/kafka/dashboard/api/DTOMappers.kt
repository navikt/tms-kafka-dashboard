package no.nav.tms.kafka.dashboard.api

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.header.Header

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
            timestamp = consumerRecord.timestamp(),
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
}
