package net.stewart.h2toolkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.extension.RegisterExtension

class H2TestDatabaseTest {

    private val locations = listOf("classpath:db/tfx")

    @Test
    fun `runs consumer migrations and toolkit internal migration`() {
        H2TestDatabase(flywayLocations = locations).use { db ->
            val ds = db.init()
            ds.connection.use { conn ->
                conn.createStatement().execute("INSERT INTO widget(name) VALUES('a')")
                val rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM widget")
                rs.next()
                assertEquals(1, rs.getInt(1))

                // Toolkit's internal migration created the schema_updater table.
                val tables = conn.metaData.getTables(null, null, "SCHEMA_UPDATER", null)
                assertTrue(tables.next(), "schema_updater table should exist")
            }
        }
    }

    @Test
    fun `instances are isolated from each other`() {
        H2TestDatabase(flywayLocations = locations).use { a ->
            H2TestDatabase(flywayLocations = locations).use { b ->
                a.init().connection.use { conn ->
                    conn.createStatement().execute("INSERT INTO widget(name) VALUES('only-in-a')")
                }
                b.init().connection.use { conn ->
                    val rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM widget")
                    rs.next()
                    assertEquals(0, rs.getInt(1), "database b must not see a's rows")
                }
            }
        }
    }

    @Test
    fun `close shuts the database down so the name is reusable fresh`() {
        val db = H2TestDatabase(flywayLocations = locations, name = "reuse-check")
        db.init().connection.use { conn ->
            conn.createStatement().execute("INSERT INTO widget(name) VALUES('x')")
        }
        db.close()

        H2TestDatabase(flywayLocations = locations, name = "reuse-check").use { fresh ->
            fresh.init().connection.use { conn ->
                val rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM widget")
                rs.next()
                assertEquals(0, rs.getInt(1), "reused name must start empty after SHUTDOWN")
            }
        }
    }
}

class H2TestDatabaseExtensionTest {

    companion object {
        @JvmField
        @RegisterExtension
        val db = H2TestDatabaseExtension(flywayLocations = listOf("classpath:db/tfx"))
    }

    @Test
    fun `extension provides a migrated data source`() {
        db.dataSource.connection.use { conn ->
            conn.createStatement().execute("INSERT INTO widget(name) VALUES('via-extension')")
            val rs = conn.createStatement().executeQuery("SELECT name FROM widget")
            rs.next()
            assertEquals("via-extension", rs.getString(1))
        }
    }
}
