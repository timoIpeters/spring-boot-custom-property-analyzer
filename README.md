# Custom Property Analyzer for Spring Boot

This gradle plugin can be used to analyze any Spring Boot project and fetch all the custom properties that were
defined within the project. Just add it to your project (see [section below](#add-to-your-spring-boot-project)) and run
`gradle analyzeCustomProperties`. This will trigger the property analysis and return a report with all custom properties
that where defined in the project (see [Example Output](#example-output)):

## Prerequisites

| Dependency | Version |
|------------|---------|
| Java       | 17      |
| Gradle     | 8.X     |

## Options

```text
analyzeCustomProperties [--verbose] [--outputFile=<filename>] [--additionalPropertiesPattern=<pattern>]

Options:
--verbose
    Print analysis results directly to the console in addition to the JSON report.

--outputFile=<filename>
    Name of the generated report file. Default: custom-properties-analysis.json
    Output path: build/reports/<filename>

--additionalPropertiesPattern=<pattern>
    Glob pattern for additional property files (e.g. .properties, .yml, .yaml) to include
    in default value resolution. By default, application.properties/yml/yaml are consulted.
    Example: --additionalPropertiesPattern="application-*.yml"
```

```text
checkUnusedProperties [--ignoreUnused] [--verbose] [--outputFile=<filename>] [--analysisReportFile=<filename>] [--additionalPropertiesPattern=<pattern>]

Options:
--ignoreUnused
    Report unused properties without failing the build. By default the build fails if any
    unused properties are found.

--verbose
    Print check results directly to the console in addition to the JSON report.

--outputFile=<filename>
    Name of the generated report file. Default: unused-properties-report.json
    Output path: build/reports/<filename>

--analysisReportFile=<filename>
    Name of the analysis report produced by analyzeCustomProperties to read from.
    Default: custom-properties-analysis.json

--additionalPropertiesPattern=<pattern>
    Glob pattern for additional property files (e.g. .properties, .yml, .yaml) to include in the unused property check.
    By default, application.properties/yml/yaml are consulted.
    Example: --additionalPropertiesPattern="application-*.yml"
```

## Add to your Spring Boot project

The plugin can be applied to any gradle project using the Plugin DSL.

### Groovy DSL (`build.gradle`)

```groovy
plugins {
    id 'io.github.timoIpeters.custom-property-analyzer' version '2.0.0'
}
```

### Kotlin DSL (`build.gradle.kts`)

```kotlin
plugins {
    id("io.github.timoIpeters.custom-property-analyzer") version "2.0.0"
}
```

## Tasks

### `analyzeCustomProperties`

Scans all Java source files in the project for `@Value` and `@ConfigurationProperties` annotations and produces a JSON
report of all discovered custom properties together with their default values. This is the main discovery task and the
foundation for all other tasks in this plugin.

### `checkUnusedProperties`

Compares the properties defined in your configuration files (`application.properties`, `application.yml`, etc.) against
the properties discovered by `analyzeCustomProperties`, and flags any that are never referenced in code. Runs
`analyzeCustomProperties` automatically as a dependency.

By default the build **fails** if unused properties are found, making it suitable as a CI gate. Use `--ignoreUnused` if
you want reporting without enforcement.

> **Note:** `--additionalPropertiesPattern` controls which configuration files are scanned for unused
> keys. By default only `application.properties`, `application.yml`, and `application.yaml` are checked.
> Use this option if you also want to flag unused properties in profile-specific files such as
> `application-prod.yml`.

## Output

### `analyzeCustomProperties`

By default, running the `analyzeCustomProperties` task will create a `.json` file with all properties that have been
found in the analysis. It will be stored in `build/reports/custom-properties-analysis.json`. The plugin automatically
aggregates properties from all subprojects in multi-module setups.

```json
{
  "projectName": "my-project",
  "analysisDate": "Fri Feb 13 00:22:25 CET 2026",
  "totalProperties": 4,
  "properties": [
    {
      "key": "app.api.key",
      "defaultValue": "testKey",
      "source": "VALUE_ANNOTATION",
      "location": "EmailService.java"
    },
    {
      "key": "app.database.max-connections",
      "source": "CONFIGURATION_PROPERTIES",
      "location": "DatabaseProperties.java"
    },
    {
      "key": "app.database.password",
      "source": "CONFIGURATION_PROPERTIES",
      "location": "DatabaseProperties.java"
    },
    {
      "key": "app.email.enabled",
      "defaultValue": "true",
      "source": "VALUE_ANNOTATION",
      "location": "EmailService.java"
    }
  ]
}
```

### `checkUnusedProperties`

Running `checkUnusedProperties` will create a `.json` file listing all properties that are defined in your configuration
files but never referenced in code. It will be stored in `build/reports/unused-properties-report.json`.

```json
{
  "projectName": "my-project",
  "analysisDate": "Fri Feb 13 00:22:25 CET 2026",
  "totalUnusedProperties": 2,
  "unusedProperties": [
    {
      "key": "app.database.legacy-timeout",
      "value": "5000"
    },
    {
      "key": "app.feature.old-flag",
      "value": "false"
    }
  ]
}
```

### Property Features
- **Relaxed Binding:** Properties are normalized to canonical kebab-case (e.g., `myProperty` in Java matches `my-property` in YAML).
- **Recursive Expansion:** `@ConfigurationProperties` classes are recursively analyzed. Complex nested objects and `Map<String, ComplexType>` (represented with `[*]`) are fully expanded.
- **YAML Support:** Full support for nested YAML structures and list indexing (e.g., `app.servers[0].host`).

## Default Property Analysis

The plugin checks for default property values in these cases:

1. `@Value` annotated properties are checked for a hardcoded default (e.g., `@Value("${my.prop:default}")`).
2. Properties defined in `application.properties`, `application.yml`, or `application.yaml`. These values take precedence over `@Value` defaults.

If you want to check within additional profile-specific files, use the `--additionalPropertiesPattern` option. For
example, `--additionalPropertiesPattern="application-*.yaml"` would match files like `application-dev.yaml` or
`application-prod.yaml`.

## Verbose

You can run either task with `--verbose` to get a detailed output of the results directly in the console.

### `analyzeCustomProperties --verbose`

```text
> Task :analyzeCustomProperties

========================================
Custom Property Analysis Results
========================================

@Value Annotations:
──────────────────────────────────────────────────
  • app.api.key
    Location: EmailService.java

  • app.email.enabled
    Default: true
    Location: EmailService.java

@ConfigurationProperties Classes:
──────────────────────────────────────────────────
  • app.database.max-connections
    Location: DatabaseProperties.java

  • app.database.password
    Location: DatabaseProperties.java
```

### `checkUnusedProperties --verbose`

```text
> Task :checkUnusedProperties

========================================
Unused Properties Check Results
========================================

The following properties are defined in configuration files but never referenced in code:

──────────────────────────────────────────────────
  • app.database.legacy-timeout
    Value: 5000

  • app.feature.old-flag
    Value: false

Total unused properties: 2
```
