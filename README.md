# h2-kotlin-toolkit

A Kotlin library for managing H2 file-mode databases with AES encryption at rest, automatic schema evolution, connection pool management, and rotating backups.

## Features

- **Encryption at rest** — AES-encrypted H2 database files. Automatically migrates an unencrypted database to encrypted on first startup with a file password.
- **Password rotation** — Change the database user password without data loss by setting a prior password during rotation.
- **Schema evolution** — Flyway for DDL migrations, plus a `SchemaUpdater` framework for programmatic data updates that need Kotlin code (API calls, computation, data backfill).
- **Connection pooling** — HikariCP with configurable pool size, timeouts, leak detection, and optional metrics registry.
- **Rotating backups** — Daily and weekly backups via H2's `SCRIPT TO`, encrypted when the database is encrypted.
- **Backup restore** — Drop a backup file as a sentinel and restart to restore.

## Quick Start

### 1. Add the dependency

**Option A: Composite build (recommended for development)**

Clone this repo as a sibling directory and add to your `settings.gradle.kts`:

```kotlin
includeBuild("../h2-kotlin-toolkit")
```

Then in `build.gradle.kts`:

```kotlin
dependencies {
    implementation("net.stewart:h2-kotlin-toolkit:0.1.0")
}
```

**Option B: Maven Local**

Build and publish to your local Maven repository:

```bash
cd h2-kotlin-toolkit
./gradlew publishToMavenLocal
```

Then in your consuming project's `build.gradle.kts`:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("net.stewart:h2-kotlin-toolkit:0.1.0")
}
```

### 2. Initialize the database

```kotlin
import net.stewart.h2toolkit.H2Config
import net.stewart.h2toolkit.H2Database

fun main() {
    val db = H2Database(H2Config(
        basePath = "./data/myapp",                       // path without .mv.db extension
        password = System.getenv("H2_PASSWORD")!!,       // database user password
        filePassword = System.getenv("H2_FILE_PASSWORD")!!, // AES file encryption key
    ))
    db.init()

    // Use db.dataSource for your ORM, JDBI, or raw JDBC
    val ds = db.dataSource

    // On shutdown
    db.destroy()
}
```

### 3. Add Flyway migrations

Place SQL migration files in `src/main/resources/db/migration/` (the default location):

```sql
-- V001__create_users.sql
CREATE TABLE app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL
);
```

The toolkit runs its own internal migration (for the `schema_updater` table) separately, so your migration numbering starts at V001 with no conflicts.

### 4. Add schema updaters (optional)

For data updates that need Kotlin code:

```kotlin
import net.stewart.h2toolkit.SchemaUpdater
import net.stewart.h2toolkit.SchemaUpdaterRunner

class PopulateDefaultsUpdater : SchemaUpdater {
    override val name = "populate_defaults"
    override val version = 1  // bump to re-run
    override fun run() {
        // Your data population logic here
    }
}

// Register before calling db.init()
SchemaUpdaterRunner.register(PopulateDefaultsUpdater())
db.init()
```

### 5. Set up backups (optional)

```kotlin
import net.stewart.h2toolkit.H2Backup
import java.io.File

val backup = H2Backup(
    dataSource = db.dataSource,
    backupDir = File("./data/backups"),
    filePassword = System.getenv("H2_FILE_PASSWORD") ?: "",
)

// Call from a scheduled task (e.g., every 24 hours)
backup.runBackup()
```

## Configuration Reference

`H2Config` parameters:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `basePath` | (required) | Path to H2 database file without extension |
| `password` | (required) | H2 database user password (SA) |
| `filePassword` | (required) | AES file encryption password |
| `priorPassword` | `""` | Previous password for rotation |
| `maxPoolSize` | `25` | HikariCP maximum connections |
| `poolName` | `"h2-pool"` | Pool name for metrics/logging |
| `connectionTimeoutMs` | `5000` | Connection acquisition timeout |
| `leakDetectionThresholdMs` | `10000` | Leak warning threshold (0 = off) |
| `metricsRegistry` | `null` | Micrometer registry for pool metrics |
| `flywayLocations` | `["classpath:db/migration"]` | Flyway migration scan paths |

## Password Rotation

To rotate the database user password:

1. Set `H2_PRIOR_PASSWORD` to the current password
2. Set `H2_PASSWORD` to the new password
3. Restart the application
4. After successful startup, remove `H2_PRIOR_PASSWORD`

The toolkit connects with the prior password, runs `ALTER USER sa SET PASSWORD`, and all subsequent connections use the new password.

## Encryption Migration

On first startup with `filePassword` set, if the database file exists but is unencrypted, the toolkit automatically:

1. Exports the database to a SQL script
2. Backs up the original file (`.mv.db.pre-encryption`)
3. Creates a new AES-encrypted database and imports the data
4. Deletes the plaintext export

If any step fails, the original database is restored automatically.

## Backup Restore

To restore from a backup:

1. Copy a backup file to `{dataDir}/restore.sql` (same directory as the database)
2. Restart the application
3. The toolkit restores the backup and deletes the sentinel file

For encrypted databases, the backup must be a `CIPHER AES` backup created with the same `filePassword`.

## Requirements

- JDK 21+
- Kotlin 2.x

## License

MIT
