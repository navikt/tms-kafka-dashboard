package no.nav.tms.kafka.dashboard.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.util.*

object ConsumerRecordMapper {

    private val objectmapper = jacksonObjectMapper()

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
        return when (keyOrValue) {
            is String -> keyOrValue
            is Double -> keyOrValue.toString()
            is Float -> keyOrValue.toString()
            is Int -> keyOrValue.toString()
            is Long -> keyOrValue.toString()
            is Short -> keyOrValue.toString()
            is UUID -> keyOrValue.toString()
            is GenericRecord -> objectmapper.writeValueAsString(getRecordKeyValues(keyOrValue))
            else -> keyOrValue.toString()
        }
    }

    private fun getRecordKeyValues(genericRecord: GenericRecord): Map<String, String> {
        return genericRecord.schema.fields.fold(emptyMap()) { m, field ->
            val v = genericRecord[field.name()]?.toString() ?: ""
            m + (field.name() to v)
        }
    }

}
