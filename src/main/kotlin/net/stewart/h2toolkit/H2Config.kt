package net.stewart.h2toolkit

/**
 * Configuration for [H2Database] initialization.
 *
 * @param basePath Path to the H2 database file without extension (e.g., "./data/myapp")
 * @param password The H2 database user password (SA user)
 * @param filePassword The AES file encryption password
 * @param priorPassword Previous password for rotation (set when changing password, empty otherwise)
 * @param maxPoolSize HikariCP maximum pool size
 * @param poolName HikariCP pool name (for metrics/logging)
 * @param connectionTimeoutMs HikariCP connection timeout in milliseconds
 * @param leakDetectionThresholdMs HikariCP leak detection threshold in milliseconds (0 = disabled)
 * @param metricsRegistry Optional metrics registry for HikariCP pool stats (e.g., Micrometer)
 * @param flywayLocations Flyway migration locations (default: classpath:db/migration)
 */
data class H2Config(
    val basePath: String,
    val password: String,
    val filePassword: String,
    val priorPassword: String = "",
    val maxPoolSize: Int = 25,
    val poolName: String = "h2-pool",
    val connectionTimeoutMs: Long = 5000,
    val leakDetectionThresholdMs: Long = 10000,
    val metricsRegistry: Any? = null,
    val flywayLocations: List<String> = listOf("classpath:db/migration"),
) {
    /** Redacts passwords to prevent accidental logging of credentials. */
    override fun toString(): String =
        "H2Config(basePath=$basePath, poolName=$poolName, maxPoolSize=$maxPoolSize)"
}
