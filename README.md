# Custom Property Analyzer for Spring Boot

This gradle plugin can be used to analyze any Spring Boot project and fetch all the custom properties that were
defined within the project. Just add it to your project (see [section below](#add-to-your-spring-boot-project)) and run
`gradle analyzeCustomProperties`. This will trigger the property analysis and return a report with all custom properties
that where defined in the project (see  [Example Output](#example-output)):

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

## Add to your Spring Boot project

```groovy
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    dependencies {
        classpath 'com.tpeters:custom-property-analyzer-plugin:1.0.1-SNAPSHOT'
    }
}

apply plugin: 'com.tpeters.custom-property-analyzer'
```

## Output

By default, running the `analyzeCustomProperties` task will create a `.json` file with all properties that have been found
in the analysis. It will be stored in `build/reports/custom-properties-analysis.json`. The plugin automatically aggregates
properties from all subprojects in multi-module setups.

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

### Property Features
- **Relaxed Binding:** Properties are normalized to canonical kebab-case (e.g., `myProperty` in Java matches `my-property` in YAML).
- **Recursive Expansion:** `@ConfigurationProperties` classes are recursively analyzed. Complex nested objects and `Map<String, ComplexType>` (represented with `[*]`) are fully expanded.
- **YAML Support:** Full support for nested YAML structures and list indexing (e.g., `app.servers[0].host`).

## Default Property Analysis

The plugin checks for default property values in these cases:

1. `@Value` annotated properties are checked for a hardcoded default (e.g., `@Value("${my.prop:default}")`).
2. Properties defined in `application.properties`, `application.yml`, or `application.yaml`. These values take precedence over `@Value` defaults.

If you want to check within additional profile-specific files, use the `--additionalPropertiesPattern` option. For example, `--additionalPropertiesPattern="application-*.yaml"` would match files like `application-dev.yaml` or `application-prod.yaml`.

## Verbose

You can run the task with `--verbose` to directly get an output of the analysis results in the console:

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
