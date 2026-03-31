package net.stewart.h2toolkit

import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.DriverManager
import java.time.DayOfWeek
import java.time.LocalDate
import javax.sql.DataSource

/**
 * Database backup service using H2's SCRIPT TO command.
 *
 * Supports daily rotating backups (configurable slots) and weekly backups on Sundays.
 * Uses direct JDBC connections (bypassing HikariCP) to avoid pool starvation
 * during long-running backup operations.
 *
 * For encrypted databases, backups are encrypted with CIPHER AES and named `.sql.enc`.
 * For unencrypted databases, backups use GZIP compression and are named `.sql.gz`.
 */
class H2Backup(
    private val dataSource: DataSource,
    private val backupDir: File,
    private val filePassword: String = "",
    private val dailySlots: Int = 6,
    private val weeklySlots: Int = 4,
) {
    private val log = LoggerFactory.getLogger(H2Backup::class.java)
    private val encrypted = filePassword.isNotBlank()

    /**
     * Runs a daily backup (and a weekly backup if today is Sunday).
     * Call this from a scheduled task (e.g., every 24 hours).
     */
    fun runBackup() {
        try {
            if (!backupDir.exists()) {
                backupDir.mkdirs()
                log.info("Created backup directory: {}", backupDir.absolutePath)
            }

            val today = LocalDate.now()
            val ext = if (encrypted) "sql.enc" else "sql.gz"

            // Daily backup
            val dailySlot = today.dayOfYear % dailySlots
            val dailyFile = File(backupDir, "daily-$dailySlot.$ext")
            writeBackup(dailyFile)
            log.info("Daily backup: {} ({} bytes, slot {}/{}{})",
                dailyFile.name, dailyFile.length(), dailySlot, dailySlots,
                if (encrypted) ", AES-encrypted" else "")

            // Weekly backup on Sundays
            if (today.dayOfWeek == DayOfWeek.SUNDAY) {
                val weeklySlot = (today.dayOfYear / 7) % weeklySlots
                val weeklyFile = File(backupDir, "weekly-$weeklySlot.$ext")
                writeBackup(weeklyFile)
                log.info("Weekly backup: {} ({} bytes, slot {}/{}{})",
                    weeklyFile.name, weeklyFile.length(), weeklySlot, weeklySlots,
                    if (encrypted) ", AES-encrypted" else "")
            }
        } catch (e: Exception) {
            log.warn("Database backup failed: {}", e.message, e)
        }
    }

    private fun writeBackup(file: File) {
        // Use a direct JDBC connection instead of the pool to avoid starvation
        val hikari = dataSource as HikariDataSource
        DriverManager.getConnection(hikari.jdbcUrl, hikari.username, hikari.password).use { conn ->
            val path = file.absolutePath.replace("\\", "/").replace("'", "''")
            val sql = if (encrypted) {
                val escapedPw = filePassword.replace("'", "''")
                "SCRIPT TO '$path' CIPHER AES PASSWORD '$escapedPw'"
            } else {
                "SCRIPT TO '$path' COMPRESSION GZIP"
            }
            conn.createStatement().use { stmt -> stmt.execute(sql) }
        }
    }
}
