package no.nav.tms.kafka.dashboard.api.search

import io.kotest.matchers.longs.shouldBeInRange
import org.junit.jupiter.api.Test
import java.util.*
import java.time.Instant

class GuidHelperTest {
    @Test
    fun `henter riktig tid fra UUID`() {
        val uuid = UUID.randomUUID().toString()

        val epoch = GuidHelper.extractEpoch(uuid)

        val now = Instant.now().toEpochMilli()

        epoch!!.shouldBeInRange((now - 10)..(now))
    }
}
