package no.nav.tms.kafka.dashboard.api

import com.fasterxml.jackson.annotation.JsonAlias
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.tms.kafka.dashboard.api.DTOMappers.toTopicWithOffset
import java.time.*

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
    val readFromPosition: ReadFrom,
    val topicPartition: Int?,
    val maxRecords: Int,
    val fromOffset: Long?,
    @JsonAlias("filter") private val _filter: RecordFilter?,
    @JsonAlias("fromTime") private val _fromTime: String?,
    @JsonAlias("toTime") private val _toTime: String?,
    val timezoneOffsetMinutes: Int
) {
    val filter = if (_filter == null || (_filter.key.isNullOrBlank() && _filter.value.isNullOrBlank())) {
        null
    } else {
        RecordFilter(
            key = _filter.key?.takeUnless { it.isBlank() },
            value = _filter.value?.takeUnless { it.isBlank() }
        )
    }

    val fromTime: ZonedDateTime? = _fromTime?.takeUnless { it.isBlank() }?.let { parseTime(it) }
    val toTime: ZonedDateTime? = _toTime?.takeUnless { it.isBlank() }?.let { parseTime(it) }

    fun validate(): ReadTopicRequest {
        if (maxRecords < 0 || maxRecords > MAX_KAFKA_RECORDS) {
            throw IllegalArgumentException("maxRecords must be between 0 and $MAX_KAFKA_RECORDS")
        } else {
            return this
        }
    }

    private fun parseTime(timeString: String): ZonedDateTime {
        return runCatching {
            ZonedDateTime.parse(timeString)
        }.getOrNull()
            ?: runCatching {
            LocalDateTime.parse(timeString)
                .atOffset(zoneOffset())
                .toZonedDateTime()
        }.getOrNull()
            ?: runCatching {
            LocalDate.parse(timeString)
                .atTime(0, 0)
                .atOffset(zoneOffset())
                .toZonedDateTime()
        }.getOrNull()
            ?: throw IllegalArgumentException("Could")
    }

    private fun zoneOffset(): ZoneOffset {
        val hours = timezoneOffsetMinutes / 60

        val minutes = timezoneOffsetMinutes - (hours * 60)

        return ZoneOffset.ofHoursMinutes(hours, minutes)
    }
}

enum class ReadFrom {
    Beginning, Offset, End, TimeWindow
}

data class RecordFilter(
    val key: String?,
    val value: String?
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
