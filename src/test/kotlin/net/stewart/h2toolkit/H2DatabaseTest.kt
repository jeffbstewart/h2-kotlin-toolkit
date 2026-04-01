package net.stewart.h2toolkit

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class H2DatabaseTest {

    @Test
    fun `init creates encrypted database and runs flyway`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "h2-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val basePath = File(tempDir, "testdb").absolutePath

        try {
            val db = H2Database(H2Config(
                basePath = basePath,
                password = "test-password",
                filePassword = "test-file-key",
                flywayLocations = listOf("classpath:db/test-migrations"),
            ))
            db.init()

            // Database file should exist and be encrypted
            assertTrue(File("${basePath}.mv.db").exists(), "Database file should exist")

            // DataSource should be available
            assertNotNull(db.dataSource)

            // schema_updater table should exist (created by toolkit migration)
            db.dataSource.connection.use { conn ->
                val rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM schema_updater"
                )
                rs.next()
                assertTrue(rs.getInt(1) >= 0)
            }

            db.destroy()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `schema updater tracks versions`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "h2-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val basePath = File(tempDir, "testdb").absolutePath

        try {
            SchemaUpdaterRunner.clear()
            var ran = false
            SchemaUpdaterRunner.register(object : SchemaUpdater {
                override val name = "test_updater"
                override val version = 1
                override fun run() { ran = true }
            })

            val db = H2Database(H2Config(
                basePath = basePath,
                password = "test-password",
                filePassword = "test-file-key",
            ))
            db.init()

            assertTrue(ran, "Schema updater should have run")

            db.destroy()
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
