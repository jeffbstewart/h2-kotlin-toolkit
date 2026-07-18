package net.stewart.h2toolkit

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory

/**
 * In-memory H2 database for tests, running the same Flyway migrations
 * production uses (the toolkit's internal migration plus the
 * consumer's [flywayLocations]). Each instance gets a unique database
 * name, so parallel test classes are fully isolated.
 *
 * Encryption, backups, password rotation, and restore sentinels do not
 * apply in memory — this mirrors the production [H2Database] schema,
 * not its at-rest properties.
 *
 * ORM wiring stays with the consumer (e.g.
 * `JdbiOrm.setDataSource(db.init())` for jdbi-orm users).
 *
 * ```kotlin
 * val db = H2TestDatabase()          // classpath:db/migration by default
 * val ds = db.init()
 * // ... run the test against ds ...
 * db.close()
 * ```
 *
 * For JUnit 5, see [H2TestDatabaseExtension].
 */
class H2TestDatabase(
    val flywayLocations: List<String> = listOf("classpath:db/migration"),
    /**
     * Also run registered [SchemaUpdater]s after migrations, as
     * production init does. Off by default: updaters may call external
     * services, and most tests only need the schema.
     */
    val runSchemaUpdaters: Boolean = false,
    name: String? = null,
) : AutoCloseable {

    private val log = LoggerFactory.getLogger(H2TestDatabase::class.java)

    /** Unique per instance unless explicitly provided. */
    val name: String = name ?: "h2test-${COUNTER.incrementAndGet()}"

    /** DB_CLOSE_DELAY=-1 keeps the database alive between pool connections. */
    val jdbcUrl: String = "jdbc:h2:mem:${this.name};DB_CLOSE_DELAY=-1"

    private var hikariDataSource: HikariDataSource? = null

    /** The pooled DataSource, available after [init]. */
    val dataSource: DataSource
        get() = hikariDataSource
            ?: throw IllegalStateException("H2TestDatabase not initialized — call init() first")

    fun init(): DataSource {
        check(hikariDataSource == null) { "H2TestDatabase already initialized" }
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = this@H2TestDatabase.jdbcUrl
            username = "sa"
            password = ""
            maximumPoolSize = 4
            poolName = name
        })
        hikariDataSource = ds

        // Same two-phase migration as production H2Database.init():
        // toolkit-internal migrations in their own history table, then
        // the consumer's.
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/h2toolkit")
            .table("flyway_schema_history_h2toolkit")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate()
        Flyway.configure()
            .dataSource(ds)
            .locations(*flywayLocations.toTypedArray())
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate()

        if (runSchemaUpdaters && SchemaUpdaterRunner.hasUpdaters()) {
            SchemaUpdaterRunner.runAll(ds)
        }
        log.debug("H2TestDatabase '{}' ready", name)
        return ds
    }

    /** Shuts the in-memory database down and closes the pool. */
    override fun close() {
        hikariDataSource?.let { ds ->
            try {
                ds.connection.use { it.createStatement().execute("SHUTDOWN") }
            } catch (e: Exception) {
                log.debug("SHUTDOWN of '{}' failed (already gone?): {}", name, e.message)
            }
            ds.close()
        }
        hikariDataSource = null
    }

    private companion object {
        val COUNTER = AtomicLong()
    }
}
