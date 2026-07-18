package net.stewart.h2toolkit

import javax.sql.DataSource
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit 5 extension owning one [H2TestDatabase] per test class: a
 * fresh uniquely-named in-memory database, migrated before the first
 * test and shut down after the last.
 *
 * ```kotlin
 * class AccountDaoTest {
 *     companion object {
 *         @JvmField
 *         @RegisterExtension
 *         val db = H2TestDatabaseExtension()
 *     }
 *
 *     @Test
 *     fun `stores and loads`() {
 *         val ds = db.dataSource
 *         // ...
 *     }
 * }
 * ```
 */
class H2TestDatabaseExtension(
    private val flywayLocations: List<String> = listOf("classpath:db/migration"),
    private val runSchemaUpdaters: Boolean = false,
) : BeforeAllCallback, AfterAllCallback {

    private var db: H2TestDatabase? = null

    val dataSource: DataSource
        get() = (db ?: error("extension not started")).dataSource

    override fun beforeAll(context: ExtensionContext) {
        db = H2TestDatabase(flywayLocations, runSchemaUpdaters).also { it.init() }
    }

    override fun afterAll(context: ExtensionContext) {
        db?.close()
        db = null
    }
}
