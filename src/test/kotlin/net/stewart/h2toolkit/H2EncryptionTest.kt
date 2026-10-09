package net.stewart.h2toolkit

import java.io.File
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class H2EncryptionTest {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "h2-enc-test-${System.nanoTime()}").apply { mkdirs() }
    private val basePath = File(tempDir, "plaindb").absolutePath
    private val backupFile = File("$basePath.mv.db.pre-encryption")

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    /** Creates an unencrypted file-mode database holding a few rows. */
    private fun createPlaintextDb() {
        DriverManager.getConnection("jdbc:h2:file:$basePath", "sa", PASSWORD).use { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE secret_note (id INT PRIMARY KEY, body VARCHAR(100))")
                st.execute("INSERT INTO secret_note VALUES (1, 'alpha'), (2, 'beta'), (3, 'gamma')")
                st.execute("SHUTDOWN")
            }
        }
    }

    private fun config(retain: Boolean = false) = H2Config(
        basePath = basePath,
        password = PASSWORD,
        filePassword = FILE_PASSWORD,
        flywayLocations = listOf("classpath:db/test-migrations"),
        retainPreEncryptionBackup = retain,
    )

    private fun noteCount(db: H2Database): Int = db.dataSource.connection.use { conn ->
        conn.createStatement().executeQuery("SELECT COUNT(*) FROM secret_note").use { rs -> rs.next(); rs.getInt(1) }
    }

    @Test
    fun `migration removes the plaintext backup after verifying the encrypted copy`() {
        createPlaintextDb()
        val db = H2Database(config())
        db.init()
        try {
            assertEquals(3, noteCount(db))
            assertFalse(backupFile.exists(), "plaintext pre-encryption backup must not be left on disk")
            assertFalse(File("$basePath-export.sql").exists(), "plaintext export must not be left on disk")
        } finally {
            db.destroy()
        }
    }

    @Test
    fun `plaintext backup is kept only when retention is explicitly enabled`() {
        createPlaintextDb()
        val db = H2Database(config(retain = true))
        db.init()
        try {
            assertEquals(3, noteCount(db))
            assertTrue(backupFile.exists(), "opt-in retention keeps the backup")
        } finally {
            db.destroy()
        }
    }

    @Test
    fun `leftover plaintext backup from an earlier migration is removed on startup`() {
        createPlaintextDb()
        H2Database(config(retain = true)).also { it.init() }.destroy()
        assertTrue(backupFile.exists())

        val db = H2Database(config())
        db.init()
        try {
            assertFalse(backupFile.exists(), "a retained backup is removed once retention is off")
            assertEquals(3, noteCount(db))
        } finally {
            db.destroy()
        }
    }

    @Test
    fun `secure delete removes the file`() {
        val f = File(tempDir, "plain.bin").apply { writeBytes(ByteArray(200_000) { 7 }) }
        H2Encryption.secureDelete(f)
        assertFalse(f.exists())
    }

    private companion object {
        const val PASSWORD = "test-password"
        const val FILE_PASSWORD = "test-file-key"
    }
}
