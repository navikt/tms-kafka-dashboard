import default.DependencyGroup
import default.FlywayDefaults

object Ulid: DependencyGroup {
    override val groupId: String = "com.github.f4b6a3"
    override val version: String = "5.2.3"

    val creator = dependency("ulid-creator")
}

object TokenSupport6: DependencyGroup {
    override val groupId = "no.nav.tms.token.support"
    override val version = "6.0.0-alpha-1"


    val entraIdTokenVerification get() = dependency("entra-id-token-verification")
    val entraIdTokenVerificationMock get() = dependency("entra-id-token-verification-mock")
    val entraIdTokenFetcher get() = dependency("entra-id-token-fetcher")
    val userLoginRoutes get() = dependency("user-login-routes")
    val userTokenVerification get() = dependency("user-token-verification")
    val userTokenVerificationMock get() = dependency("user-token-verification-mock")
    val userTokenExchange get() = dependency("user-token-exchange")
}
