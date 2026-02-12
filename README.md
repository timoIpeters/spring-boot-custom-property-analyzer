# Custom Property Analyzer for Spring Boot

This gradle plugin can be used to analyze any Spring Boot project and fetch all the custom properties that were
defined within the project. Just add it to your project (see [section below](#add-to-your-spring-boot-project)) and run
`gradle analyzeCustomProperties`. This will trigger the property analysis and return a report with all custom properties
that where defined in the project (see  [Example Output](#example-output)):

## Prerequisites

| Dependency | Version |
|------------|---------|
| Java       | 17      |
| Gradle     | 7.5     |

## Add to your Spring Boot project

```groovy
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    dependencies {
        classpath 'com.tpeters:custom-property-analyzer-plugin:1.0.0-SNAPSHOT'
    }
}

apply plugin: 'com.tpeters.custom-property-analyzer'
```

## Output

By default, running the `analyzeCustomProperties` task will create a `.json` file with all properties that have been found
in the analysis. It will be stored in `build/reports/custom-properties-analysis.json` in the following format:

```json
{
  "projectName": "my-project",
  "analysisDate": "Fri Feb 13 00:22:25 CET 2026",
  "totalProperties": 4,
  "properties": [
    {
      "key": "app.api.key",
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
      "key": "app.email.enabled:true",
      "source": "VALUE_ANNOTATION",
      "location": "EmailService.java"
    }
  ]
}
```

You can also run the task with `--verbose` to directly get an output of the analysis results in the console:

```text
> Task :analyzeCustomProperties

========================================
Custom Property Analysis Results
========================================

@Value Annotations:
──────────────────────────────────────────────────
  • app.api.key
    Location: EmailService.java
    
  • app.email.enabled:true
    Location: EmailService.java

@ConfigurationProperties Classes:
──────────────────────────────────────────────────
  • app.database.max-connections
    Location: DatabaseProperties.java

  • app.database.password
    Location: DatabaseProperties.java
```
