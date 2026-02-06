import default.DependencyGroup
import default.FlywayDefaults

object Ulid: DependencyGroup {
    override val groupId: String = "com.github.f4b6a3"
    override val version: String = "5.2.3"

    val creator = dependency("ulid-creator")
}
