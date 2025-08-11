import default.DependencyGroup
import default.FlywayDefaults

object Avro: DependencyGroup {
    override val groupId get() = "io.confluent"
    override val version get() = "7.6.0"

    val avroSerializer get() = dependency("kafka-avro-serializer")
    val schemaRegistry get() = dependency("kafka-schema-registry")
}

object Hsql: DependencyGroup {
    override val groupId get() = "org.hsqldb"
    override val version get() = "2.7.4"

    val hsqldb = dependency("hsqldb")
}

object FlywayHsql: FlywayDefaults {
    val hsqldb get() = dependency("flyway-database-hsqldb")
}
