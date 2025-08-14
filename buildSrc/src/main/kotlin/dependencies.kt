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
