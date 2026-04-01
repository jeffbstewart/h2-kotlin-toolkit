package net.stewart.h2toolkit

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.DriverManager
import javax.sql.DataSource

/**
 * Initializes and manages an H2 file-mode database with:
 * - AES encryption at rest (automatic migration from unencrypted)
 * - HikariCP connection pool
 * - Flyway schema migrations
 * - Schema updater framework for programmatic data updates
 * - Connection password rotation
 * - Backup restore from sentinel file
 *
 * Usage:
 * ```kotlin
 * val db = H2Database(H2Config(
 *     basePath = "./data/myapp",
 *     password = System.getenv("H2_PASSWORD")!!,
 *     filePassword = System.getenv("H2_FILE_PASSWORD")!!,
 * ))
 * db.init()
 * // ... use db.dataSource for queries ...
 * db.destroy()
 * ```
 */
class H2Database(private val config: H2Config) {

    private val log = LoggerFactory.getLogger(H2Database::class.java)
    private var hikariDataSource: HikariDataSource? = null

    /** The HikariCP DataSource, available after [init]. */
    val dataSource: DataSource
        get() = hikariDataSource ?: throw IllegalStateException("H2Database not initialized — call init() first")

    /** The JDBC URL (with CIPHER=AES), available after [init]. */
    val jdbcUrl: String
        get() = "jdbc:h2:file:${config.basePath};CIPHER=AES"

    /**
     * Initializes the database:
     * 1. Checks for restore sentinel file
     * 2. Migrates unencrypted DB to encrypted if needed
     * 3. Rotates password if priorPassword is set
     * 4. Creates HikariCP connection pool
     * 5. Runs Flyway migrations (consumer's + toolkit's internal)
     * 6. Runs registered schema updaters
     */
    fun init() {
        require(config.password.isNotBlank()) { "H2 password must not be blank" }
        require(config.filePassword.isNotBlank()) { "H2 file password must not be blank" }

        val basePath = config.basePath
        log.info("Initializing H2 database at {}", basePath)

        // Clean up stale plaintext export from a crashed encryption migration
        val staleExport = File("${basePath}-export.sql")
        if (staleExport.exists()) {
            log.warn("Deleting stale plaintext export from interrupted encryption migration: {}", staleExport.name)
            staleExport.delete()
        }

        // Warn if pre-encryption backup still exists (plaintext, should be deleted by operator)
        val preEncryptionBackup = File("${basePath}.mv.db.pre-encryption")
        if (preEncryptionBackup.exists()) {
            log.warn("Unencrypted pre-migration backup still on disk: {}. Delete it after verifying the encrypted database works.",
                preEncryptionBackup.absolutePath)
        }

        // Check for restore sentinel before any DB operations
        val restoreSentinel = File("${File(basePath).parent}/restore.sql")
        if (restoreSentinel.exists()) {
            H2Encryption.restoreFromBackup(basePath, restoreSentinel, config.password, config.filePassword)
        }

        // If DB exists unencrypted, migrate it to encrypted format
        if (File("${basePath}.mv.db").exists()) {
            H2Encryption.migrateToEncrypted(basePath, config.password, config.priorPassword, config.filePassword)
        }

        val dbUrl = jdbcUrl
        val compoundPassword = "${config.filePassword} ${config.password}"

        // Rotate password if priorPassword is set
        if (config.priorPassword.isNotBlank()) {
            H2Encryption.migratePassword(dbUrl, config.password, config.priorPassword, config.filePassword)
        }

        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = dbUrl
            username = "sa"
            password = compoundPassword
            maximumPoolSize = config.maxPoolSize
            poolName = config.poolName
            connectionTimeout = config.connectionTimeoutMs
            leakDetectionThreshold = config.leakDetectionThresholdMs
            config.metricsRegistry?.let { metricRegistry = it }
        })
        hikariDataSource = ds

        log.info("HikariCP pool '{}' created (max {})", config.poolName, config.maxPoolSize)

        // Run Flyway migrations — toolkit's internal + consumer's
        val flywayBuilder = Flyway.configure().dataSource(ds)

        val locations = mutableListOf<String>()
        // Toolkit's own migration (schema_updater table)
        locations.add("classpath:db/h2toolkit")
        // Consumer's migrations
        locations.addAll(config.flywayLocations)

        flywayBuilder.locations(*locations.toTypedArray())
        flywayBuilder.load().migrate()
        log.info("Flyway migrations applied")

        // Run schema updaters
        if (SchemaUpdaterRunner.hasUpdaters()) {
            SchemaUpdaterRunner.runAll(ds)
        }
    }

    /**
     * Creates a correctly-configured [H2Backup] using this database's connection pool
     * and file password. Ensures encrypted databases produce encrypted backups.
     */
    fun createBackup(backupDir: File, dailySlots: Int = 6, weeklySlots: Int = 4): H2Backup =
        H2Backup(dataSource, backupDir, config.filePassword, dailySlots, weeklySlots)

    /** Closes the connection pool. */
    fun destroy() {
        hikariDataSource?.close()
        hikariDataSource = null
        log.info("H2Database destroyed")
    }
}
