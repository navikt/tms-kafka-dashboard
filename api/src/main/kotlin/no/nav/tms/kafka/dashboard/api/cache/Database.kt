package no.nav.tms.kafka.dashboard.api.cache

import com.zaxxer.hikari.HikariDataSource
import kotliquery.Query
import kotliquery.Session
import kotliquery.action.ListResultQueryAction
import kotliquery.action.NullableResultQueryAction
import kotliquery.sessionOf
import kotliquery.using

interface Database {

    val dataSource: HikariDataSource

    fun insert(queryBuilder: () -> Query) {
        using(sessionOf(dataSource)) {
            it.run(queryBuilder.invoke().asUpdate)
        }
    }

    fun update(queryBuilder: (Session) -> Query) {
        using(sessionOf(dataSource)) {
            it.run(queryBuilder.invoke(it).asUpdate)
        }
    }

    fun <T> list(action: () -> ListResultQueryAction<T>): List<T> =
        using(sessionOf(dataSource)) {
            it.run(action.invoke())
        }

    fun batch(statement: String, params: List<Map<String, Any?>>) =
        using(sessionOf(dataSource)) {
            it.batchPreparedNamedStatement(statement, params)
        }

    fun <T> single(action: () -> NullableResultQueryAction<T>): T =
        using(sessionOf(dataSource)) {
            it.run(action.invoke())
        } ?: throw NoResultException("Krever minst ett resultat fra query")

    fun <T> singleOrNull(action: () -> NullableResultQueryAction<T>): T? =
        using(sessionOf(dataSource)) {
            it.run(action.invoke())
        }

    class NoResultException(msg: String): IllegalStateException(msg)
}
