package no.nav.tms.kafka.dashboard.api

import org.apache.kafka.clients.consumer.ConsumerRecord

object ConsumerRecordMapper {

    fun mapConsumerRecord(consumerRecord: ConsumerRecord<Any?, Any?>): ConsumerRecord<String, String> {
        return ConsumerRecord(
            consumerRecord.topic(),
            consumerRecord.partition(),
            consumerRecord.offset(),
            consumerRecord.timestamp(),
            consumerRecord.timestampType(),
            consumerRecord.serializedKeySize(),
            consumerRecord.serializedValueSize(),
            convertToString(consumerRecord.key()),
            convertToString(consumerRecord.value()),
            consumerRecord.headers(),
            null
        )
    }

    private fun convertToString(keyOrValue: Any?): String {
        return keyOrValue.toString()
    }
}
