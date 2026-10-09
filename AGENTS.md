# h2-kotlin-toolkit Development Guide

This document provides guidance on how to work with the h2-kotlin-toolkit project, which is a Kotlin library for managing H2 file-mode databases with encryption, schema evolution, connection pooling, and backup features.

## Project Structure

The project follows standard Kotlin/Gradle conventions:

```
src/
├── main/
│   └── kotlin/
│       └── net/stewart/h2toolkit/
│           ├── H2Database.kt          # Main database interface
│           ├── H2Config.kt            # Configuration class
│           ├── H2Encryption.kt        # Encryption utilities
│           ├── SchemaUpdater.kt       # Schema update framework
│           └── H2Backup.kt            # Backup and restore functionality
├── test/
│   └── kotlin/
│       └── net/stewart/h2toolkit/
│           ├── H2DatabaseTest.kt      # Database tests
│           ├── H2EncryptionTest.kt    # Encryption migration tests
│           └── H2TestDatabaseTest.kt  # Test-fixture tests
└── testFixtures/
    └── kotlin/
        └── net/stewart/h2toolkit/
            └── H2TestDatabaseExtension.kt  # Test fixtures for integration tests
```

## Key Components

1. **H2Database** - The primary entry point for database operations with connection pooling and encryption support
2. **H2Config** - Configuration class for database parameters including path, passwords, and connection pool settings  
3. **H2Encryption** - Handles encryption at rest and migration from unencrypted to encrypted databases
4. **SchemaUpdater** - Framework for programmatic schema updates requiring Kotlin code
5. **H2Backup** - Backup and restore functionality for H2 databases

## Development Workflow

1. **Clone and Setup**: Clone this repo as a sibling directory to your project
2. **Add to Build**: Use composite build in your `settings.gradle.kts`:
   ```kotlin
   includeBuild("../h2-kotlin-toolkit")
   ```
3. **Add Dependency**: Add the dependency to your `build.gradle.kts`:
   ```kotlin
   implementation("net.stewart:h2-kotlin-toolkit:0.1.0")
   ```
4. **Make Changes**: Edit files in `src/main/kotlin/net/stewart/h2toolkit/`
5. **Run Tests**: Execute tests with `./gradlew test`
6. **Build**: Build the project with `./gradlew build`

## Testing Strategy

The project uses both unit and integration tests:

1. **Unit Tests**: Standard JUnit 5 tests in `src/test/kotlin/net/stewart/h2toolkit/`
2. **Integration Tests**: Using test fixtures via `testFixtures("net.stewart:h2-kotlin-toolkit:0.1.0")`:
   - Provides in-memory databases that run the same Flyway migrations as production
   - Each test extension gets a unique in-memory database for isolation
   - Supports parallel test execution without interference

Tests for core functionality include:
- Database initialization with encryption
- Schema upgrade and migration
- Connection pool behavior
- Backup and restore operations

## Release Process

1. Create a new branch off `main`
2. Make your changes
3. Commit them
4. Push the branch
5. Open a pull request for review

## Contribution Guidelines

1. Follow existing code conventions (Kotlin style, naming)
2. Add comprehensive tests for new functionality
3. Update documentation if needed
4. Verify tests pass before submitting
5. Make sure changes don't break existing functionality