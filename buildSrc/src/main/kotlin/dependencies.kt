import default.DependencyGroup

object Avro: DependencyGroup {
    override val groupId get() = "io.confluent"
    override val version get() = "7.6.0"

    val avroSerializer get() = dependency("kafka-avro-serializer")
    val schemaRegistry get() = dependency("kafka-schema-registry")
}
