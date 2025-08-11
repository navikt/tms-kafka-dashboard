package no.nav.tms.kafka.dashboard.api

import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.producer.internals.BuiltInPartitioner
import org.junit.jupiter.api.Test

class HashTest {
    @Test
    fun partitiontest() {
        "5161161e-eac9-4dd0-ba8d-c63cae77437e".toByteArray().let { BuiltInPartitioner.partitionForKey(it, 4) } % 4 shouldBe 0
        "04a36356-a171-469d-96f5-c4204438face".toByteArray().let { BuiltInPartitioner.partitionForKey(it, 4) } % 4 shouldBe 1
        "68ca858d-f3fa-45be-9fa7-a93f286b943f".toByteArray().let { BuiltInPartitioner.partitionForKey(it, 4) } % 4 shouldBe 2
        "8e87bf52-05ca-409e-a588-a17f859a08a6".toByteArray().let { BuiltInPartitioner.partitionForKey(it, 4) } % 4 shouldBe 3
    }
}
