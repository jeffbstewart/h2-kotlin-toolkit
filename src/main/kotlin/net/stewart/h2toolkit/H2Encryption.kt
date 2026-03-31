package net.stewart.h2toolkit

import org.slf4j.LoggerFactory
import java.io.File
import java.sql.DriverManager

/**
 * Handles H2 encryption-at-rest operations:
 * - Migrating an unencrypted database to AES-encrypted
 * - Rotating the database user password
 * - Restoring from encrypted backups
 */
object H2Encryption {

    private val log = LoggerFactory.getLogger(H2Encryption::class.java)

    /**
     * Migrates the H2 database user password from [priorPassword] to [targetPassword].
     * Uses the compound password format required by CIPHER=AES databases.
     *
     * To rotate: set priorPassword to the current password, targetPassword to the new one.
     * After one successful startup, priorPassword can be cleared.
     */
    fun migratePassword(dbUrl: String, targetPassword: String, priorPassword: String, filePassword: String) {
        val dbFile = File(dbUrl.removePrefix("jdbc:h2:file:").substringBefore(';') + ".mv.db")
        if (!dbFile.exists()) return

        // Try target password first — if it works, no migration needed
        try {
            DriverManager.getConnection(dbUrl, "sa", "$filePassword $targetPassword").use {
                it.createStatement().execute("SELECT 1")
            }
            return
        } catch (_: Exception) { }

        log.info("Database password does not match — migrating from prior password")
        try {
            DriverManager.getConnection(dbUrl, "sa", "$filePassword $priorPassword").use { conn ->
                val escaped = targetPassword.replace("'", "''")
                conn.createStatement().execute("ALTER USER sa SET PASSWORD '$escaped'")
                log.info("Database password changed successfully")
            }
        } catch (e: Exception) {
            log.error("Password migration failed — neither target nor prior password can connect", e)
            throw RuntimeException("H2 password migration failed", e)
        }
    }

    /**
     * Migrates an unencrypted H2 database to AES-encrypted format.
     *
     * Steps:
     * 1. Connect to unencrypted DB (tries target password, then prior)
     * 2. Export via SCRIPT TO
     * 3. Back up original .mv.db to .mv.db.pre-encryption
     * 4. Create encrypted DB and import via RUNSCRIPT FROM
     * 5. Delete plaintext export
     *
     * Rolls back automatically if import fails.
     */
    fun migrateToEncrypted(basePath: String, password: String, priorPassword: String, filePassword: String) {
        val dbFile = File("${basePath}.mv.db")
        val encryptedUrl = "jdbc:h2:file:$basePath;CIPHER=AES"
        val compoundPassword = "$filePassword $password"

        // Check if already encrypted
        try {
            DriverManager.getConnection(encryptedUrl, "sa", compoundPassword).use {
                it.createStatement().execute("SELECT 1")
            }
            log.info("Database is already encrypted")
            return
        } catch (_: Exception) { }

        log.info("=== ENCRYPTION MIGRATION STARTING ===")

        // Step 1: Resolve unencrypted password
        val unencryptedUrl = "jdbc:h2:file:$basePath"
        val workingPassword = resolveWorkingPassword(unencryptedUrl, password, priorPassword)
            ?: throw RuntimeException("Cannot connect to unencrypted database — fix credentials before enabling encryption")
        log.info("Step 1/5: Connected to unencrypted database")

        // Step 2: Export
        val scriptFile = File("${basePath}-export.sql")
        try {
            DriverManager.getConnection(unencryptedUrl, "sa", workingPassword).use { conn ->
                val escaped = scriptFile.absolutePath.replace("'", "''")
                conn.createStatement().execute("SCRIPT TO '$escaped'")
            }
            log.info("Step 2/5: Exported database ({} bytes)", scriptFile.length())
        } catch (e: Exception) {
            scriptFile.delete()
            throw RuntimeException("Failed to export database for encryption migration", e)
        }

        // Step 3: Back up original
        val backupFile = File("${basePath}.mv.db.pre-encryption")
        if (backupFile.exists()) backupFile.delete()
        if (!dbFile.renameTo(backupFile)) {
            scriptFile.delete()
            throw RuntimeException("Failed to rename ${dbFile.name} for backup")
        }
        log.info("Step 3/5: Backed up to {}", backupFile.name)

        // Step 4: Create encrypted DB and import
        try {
            DriverManager.getConnection(encryptedUrl, "sa", compoundPassword).use { conn ->
                val escaped = scriptFile.absolutePath.replace("'", "''")
                conn.createStatement().execute("RUNSCRIPT FROM '$escaped'")
            }
            log.info("Step 4/5: Imported into encrypted database")
        } catch (e: Exception) {
            // Rollback
            val newDbFile = File("${basePath}.mv.db")
            if (newDbFile.exists()) newDbFile.delete()
            backupFile.renameTo(dbFile)
            scriptFile.delete()
            throw RuntimeException("Encryption migration failed — original database restored", e)
        }

        // Step 5: Clean up
        scriptFile.delete()
        log.info("Step 5/5: Deleted plaintext export")
        log.info("=== ENCRYPTION MIGRATION COMPLETE ===")
    }

    /**
     * Restores the database from a backup sentinel file.
     *
     * To restore: copy a backup file to `{dataDir}/restore.sql` and restart.
     * The sentinel must be a CIPHER AES backup matching the current filePassword.
     */
    fun restoreFromBackup(basePath: String, sentinelFile: File, password: String, filePassword: String) {
        log.warn("=== DATABASE RESTORE DETECTED ===")
        val dbFile = File("${basePath}.mv.db")

        // Back up current database
        if (dbFile.exists()) {
            val backupFile = File("${basePath}.mv.db.pre-restore")
            if (backupFile.exists()) backupFile.delete()
            if (!dbFile.renameTo(backupFile)) {
                throw RuntimeException("Failed to back up current database — restore aborted")
            }
            log.warn("Step 1/3: Backed up current database")
        }

        // Import backup
        val dbUrl = "jdbc:h2:file:$basePath;CIPHER=AES"
        val compoundPassword = "$filePassword $password"
        try {
            DriverManager.getConnection(dbUrl, "sa", compoundPassword).use { conn ->
                val path = sentinelFile.absolutePath.replace("\\", "/").replace("'", "''")
                val escapedPw = filePassword.replace("'", "''")
                conn.createStatement().execute("RUNSCRIPT FROM '$path' CIPHER AES PASSWORD '$escapedPw'")
            }
            log.warn("Step 2/3: Imported backup successfully")
        } catch (e: Exception) {
            val newDbFile = File("${basePath}.mv.db")
            if (newDbFile.exists()) newDbFile.delete()
            val backupFile = File("${basePath}.mv.db.pre-restore")
            if (backupFile.exists()) backupFile.renameTo(dbFile)
            sentinelFile.delete()
            throw RuntimeException("Database restore failed — previous database restored", e)
        }

        sentinelFile.delete()
        log.warn("Step 3/3: Deleted sentinel file")
        log.warn("=== DATABASE RESTORE COMPLETE ===")
    }

    private fun resolveWorkingPassword(dbUrl: String, targetPassword: String, priorPassword: String): String? {
        try {
            DriverManager.getConnection(dbUrl, "sa", targetPassword).use {
                it.createStatement().execute("SELECT 1")
            }
            return targetPassword
        } catch (_: Exception) { }

        if (priorPassword.isNotBlank()) {
            try {
                DriverManager.getConnection(dbUrl, "sa", priorPassword).use {
                    it.createStatement().execute("SELECT 1")
                }
                return priorPassword
            } catch (_: Exception) { }
        }
        return null
    }
}
