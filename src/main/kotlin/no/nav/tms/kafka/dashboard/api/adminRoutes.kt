package no.nav.tms.kafka.dashboard.api

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.tms.kafka.dashboard.api.DTOMappers.toTopicWithOffset

fun Route.adminRoutes(
    kafkaAdminService: KafkaAdminService,
) {
    route("/api/kafka") {
        get("/available-topics") {
            call.respond(kafkaAdminService.getAvailableTopics())
        }

        get("/applications") {
            call.respond(kafkaAdminService.getApplications())
        }

        post("/read-topic") {
            val request = call.receive<ReadTopicRequest>().validate()

            kafkaAdminService.readTopic(request).let {
                call.respond(it)
            }
        }

        post("/get-consumer-offsets") {
            val request = call.receive<GetConsumerOffsetsRequest>()

            kafkaAdminService.getConsumerOffsets(request).entries.map {
                toTopicWithOffset(it.key, it.value)
            }.let {
                call.respond(it)
            }
        }

        post("/get-last-record-offset") {
            val request = call.receive<GetLastRecordOffsetRequest>()

            GetLastRecordOffsetResponse(kafkaAdminService.getLastRecordOffset(request)).let {
                call.respond(it)
            }
        }

        post("/set-consumer-offset") {
            val request = call.receive<SetConsumerOffsetRequest>()

            kafkaAdminService.setConsumerOffset(request)
        }
    }
}


const val MAX_KAFKA_RECORDS = 100

data class ReadTopicRequest(
    val topicName: String,
    val topicAllPartitions: Boolean,
    val topicPartition: Int,
    val maxRecords: Int,
    val fromOffset: Long,
    val filter: RecordFilter?
) {
    fun validate(): ReadTopicRequest {
        if (maxRecords < 0 || maxRecords > MAX_KAFKA_RECORDS) {
            throw IllegalArgumentException("maxRecords must be between 0 and $MAX_KAFKA_RECORDS")
        } else {
            return this
        }
    }
}

data class RecordFilter(
    val text: String?
)

data class GetLastRecordOffsetRequest(
    val topicName: String,
    val topicPartition: Int
)

data class GetLastRecordOffsetResponse(
    val latestRecordOffset: Long
)

data class GetConsumerOffsetsRequest(
    val groupId: String,
    val topicName: String
)

data class SetConsumerOffsetRequest(
    val groupId: String,
    val topicName: String,
    val topicPartition: Int,
    val offset: Long
)

data class TopicWithOffset(
    val topicName: String,
    val topicPartition: Int,
    val offset: Long
)
