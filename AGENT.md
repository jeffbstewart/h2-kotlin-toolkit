# Agent Work Guide

## Project Overview

This is the **h2-kotlin-toolkit** project, a Kotlin library for managing H2 file-mode databases with:
- AES encryption at rest
- Automatic schema evolution with Flyway
- Connection pooling with HikariCP
- Rotating backups
- Password rotation capabilities

## Project Layout

```
/grange/tree/
├── README.md               # Project documentation and usage guide
├── build.gradle.kts        # Gradle build configuration
├── settings.gradle.kts     # Gradle settings file
├── AGENT.md                # This file
├── src/
│   ├── main/
│   │   └── kotlin/
│   │       └── net/stewart/h2toolkit/
│   │           ├── H2Database.kt    # Main database class
│   │           ├── H2Config.kt      # Configuration class
│   │           ├── H2Encryption.kt  # Encryption helpers
│   │           ├── SchemaUpdater.kt # Schema update framework
│   │           └── H2Backup.kt      # Backup/restore functionality
│   ├── test/
│   │   └── kotlin/
│   │       └── net/stewart/h2toolkit/
│   │           ├── H2DatabaseTest.kt
│   │           └── H2TestDatabaseTest.kt
│   └── testFixtures/
│       └── kotlin/
│           └── net/stewart/h2toolkit/
│               ├── H2TestDatabase.kt
│               └── H2TestDatabaseExtension.kt
└── gradle.properties       # Gradle properties
```

## Key Files and Components

### Main Classes
- `H2Database`: The primary entry point for database operations
- `H2Config`: Configuration class for database parameters
- `H2Encryption`: Handles encryption at rest and migration
- `SchemaUpdater`: Framework for programmatic schema updates
- `H2Backup`: Backup and restore functionality

### Testing
- `H2TestDatabaseExtension`: Test fixture for in-memory database tests
- `H2TestDatabase`: Test database instances

## How to Work with This Project

### Development Setup
1. Clone this repository as a sibling directory for composite builds:
   ```bash
   git clone git@github.com:stewart/h2-kotlin-toolkit.git
   ```

2. Add to your `settings.gradle.kts`:
   ```kotlin
   includeBuild("../h2-kotlin-toolkit")
   ```

3. Add dependency to your project's `build.gradle.kts`:
   ```kotlin
   dependencies {
       implementation("net.stewart:h2-kotlin-toolkit:0.1.0")
   }
   ```

### Development Workflow
1. Make changes in `src/main/kotlin/net/stewart/h2toolkit/`
2. Run tests with:
   ```bash
   ./gradlew test
   ```
3. Build the project with:
   ```bash
   ./gradlew build
   ```

### Testing Strategy
- Tests are located in `src/test/kotlin/`
- Test fixtures are in `src/testFixtures/kotlin/`
- The `H2TestDatabaseExtension` provides in-memory databases for unit testing
- Use `@RegisterExtension` for test fixtures in your tests

### Release Process
1. Update version in `build.gradle.kts`
2. Commit changes to a new branch
3. Push the branch and propose a PR using the archivist MCP skills:
   - `start_work` to create a new branch
   - `publish` to push the branch
   - `propose` to create the PR

## Build & Test Commands
- `./gradlew build` - Build the project
- `./gradlew test` - Run all tests
- `./gradlew check` - Run code quality checks and tests