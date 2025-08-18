import default.DependencyGroup
import default.FlywayDefaults

object Hsql: DependencyGroup {
    override val groupId get() = "org.hsqldb"
    override val version get() = "2.7.4"

    val hsqldb = dependency("hsqldb")
}

object FlywayHsql: FlywayDefaults {
    val hsqldb get() = dependency("flyway-database-hsqldb")
}

object Ulid: DependencyGroup {
    override val groupId: String = "com.github.f4b6a3"
    override val version: String = "5.2.3"

    val creator = dependency("ulid-creator")
}
