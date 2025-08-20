package no.nav.tms.kafka.dashboard.api.cache

object GuidHelper {
    private const val BASE_16 = "[0-9a-fA-F]"
    private const val BASE_32_ULID = "[0-9ABCDEFGHJKMNPQRSTVWXYZabcdefghjkmnpqrstvwxyz]"

    private val UUID_PATTERN = "^$BASE_16{8}-$BASE_16{4}-$BASE_16{4}-$BASE_16{4}-$BASE_16{12}$".toRegex()
    private val ULID_PATTERN = "^[0-7]$BASE_32_ULID{25}$".toRegex()

    fun isUlid(key: String): Boolean {
        return ULID_PATTERN.matches(key)
    }

    fun isUuid(key: String): Boolean {
        return UUID_PATTERN.matches(key)
    }

    fun isGuid(string: String): Boolean {
        return isUuid(string) || isUlid(string)
    }
}
