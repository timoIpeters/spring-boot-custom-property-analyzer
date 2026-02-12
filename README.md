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

## Example Output

When running the `analyzeCustomProperties` task, you will see an analysis result output like this:

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
  ...

@ConfigurationProperties Classes:
──────────────────────────────────────────────────
  • app.database.max-connections
    Location: DatabaseProperties.java

  • app.database.password
    Location: DatabaseProperties.java
  ...
```
