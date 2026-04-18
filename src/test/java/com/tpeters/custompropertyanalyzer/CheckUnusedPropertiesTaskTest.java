package com.tpeters.custompropertyanalyzer;

import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.tpeters.custompropertyanalyzer.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class CheckUnusedPropertiesTaskTest extends AbstractPluginTest {

    @Test
    void buildFails_whenUnusedPropertiesExist() throws IOException {
        writeResourcesFile(testProjectDir, "application.properties",
                "app.name=my-app\napp.unused=orphan");
        writeJavaSource(testProjectDir, "com.example", "MyService", """
                package com.example;
                import org.springframework.beans.factory.annotation.Value;
                public class MyService {
                    @Value("${app.name}") private String name;
                }
                """);

        runAnalyze();
        BuildResult result = runCheckUnused();

        assertTrue(result.getOutput().contains("unused property") || result.getOutput().contains("unused-properties-report"),
                "Build output should mention unused properties");
    }

    @Test
    void buildSucceeds_whenNoUnusedPropertiesExist() throws IOException {
        writeResourcesFile(testProjectDir, "application.properties", "app.name=my-app");
        writeJavaSource(testProjectDir, "com.example", "MyService", """
                package com.example;
                import org.springframework.beans.factory.annotation.Value;
                public class MyService {
                    @Value("${app.name}") private String name;
                }
                """);

        runAnalyze();
        runCheckUnusedExpectSuccess();
    }

    @Test
    void buildSucceeds_whenNoPropertiesFilesExist() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "MyService", """
                package com.example;
                import org.springframework.beans.factory.annotation.Value;
                public class MyService {
                    @Value("${app.name}") private String name;
                }
                """);

        runAnalyze();
        runCheckUnusedExpectSuccess();
    }

    @Test
    void buildSucceeds_withIgnoreUnused_evenWhenUnusedPropertiesExist() throws IOException {
        writeResourcesFile(testProjectDir, "application.properties",
                "app.name=my-app\napp.unused=orphan");
        writeJavaSource(testProjectDir, "com.example", "MyService", """
                package com.example;
                import org.springframework.beans.factory.annotation.Value;
                public class MyService {
                    @Value("${app.name}") private String name;
                }
                """);

        runAnalyze();
        runCheckUnusedExpectSuccess("--ignoreUnused");
    }

    @Test
    void reportsUnusedProperty_fromPropertiesFile() throws IOException {
        writeResourcesFile(testProjectDir, "application.properties",
                "app.name=my-app\napp.orphan=unused-value");
        writeJavaSource(testProjectDir, "com.example", "MyService", """
                package com.example;
                import org.springframework.beans.factory.annotation.Value;
                public class MyService {
                    @Value("${app.name}") private String name;
                }
                """);

        runAnalyze();
        runCheckUnused();

        UnusedPropertiesReport report = getUnusedReport();
        assertEquals(1, report.totalUnusedProperties);
        assertIsUnused("app.orphan");
        assertIsNotUnused("app.name");
    }

    @Test
    void reportsUnusedProperty_fromYamlFile() throws IOException {
        writeResourcesFile(testProjectDir, "application.yml", """
                app:
                  name: my-app
                  orphan: unused-value
                """);
        writeJavaSource(testProjectDir, "com.example", "MyService", """
                package com.example;
                import org.springframework.beans.factory.annotation.Value;
                public class MyService {
                    @Value("${app.name}") private String name;
                }
                """);

        runAnalyze();
        runCheckUnused();

        assertIsUnused("app.orphan");
        assertIsNotUnused("app.name");
    }

    @Test
    void noUnusedProperties_whenAllPropertiesAreReferenced() throws IOException {
        writeResourcesFile(testProjectDir, "application.properties",
                "app.name=my-app\napp.timeout=30");
        writeJavaSource(testProjectDir, "com.example", "MyService", """
                package com.example;
                import org.springframework.beans.factory.annotation.Value;
                public class MyService {
                    @Value("${app.name}") private String name;
                    @Value("${app.timeout}") private int timeout;
                }
                """);

        runAnalyze();
        runCheckUnusedExpectSuccess();

        UnusedPropertiesReport report = getUnusedReport();
        assertEquals(0, report.totalUnusedProperties);
    }

    @Test
    void reportsUnusedProperty_valuePreservedInReport() throws IOException {
        writeResourcesFile(testProjectDir, "application.properties",
                "app.orphan=some-specific-value");
        writeJavaSource(testProjectDir, "com.example", "MyService", """
                package com.example;
                public class MyService {}
                """);

        runAnalyze();
        runCheckUnused();

        UnusedPropertyEntry entry = getUnusedReport().findProperty("app.orphan");
        assertNotNull(entry);
        assertEquals("some-specific-value", entry.value);
    }

    @Test
    void unusedPropertiesAreSortedAlphabetically() throws IOException {
        writeResourcesFile(testProjectDir, "application.properties",
                "zoo.prop=1\nalpha.prop=2\nmiddle.prop=3");
        writeJavaSource(testProjectDir, "com.example", "MyService", """
                package com.example;
                public class MyService {}
                """);

        runAnalyze();
        runCheckUnused();

        List<UnusedPropertyEntry> props = getUnusedReport().unusedProperties;
        assertEquals("alpha.prop", props.get(0).key);
        assertEquals("middle.prop", props.get(1).key);
        assertEquals("zoo.prop", props.get(2).key);
    }

    @Test
    void camelCasePropertyInFile_matchesKebabCaseReference() throws IOException {
        // application.properties uses camelCase, @Value uses kebab-case — should match
        writeResourcesFile(testProjectDir, "application.properties", "app.myProperty=value");
        writeJavaSource(testProjectDir, "com.example", "MyService", """
                package com.example;
                import org.springframework.beans.factory.annotation.Value;
                public class MyService {
                    @Value("${app.my-property}") private String prop;
                }
                """);

        runAnalyze();
        runCheckUnusedExpectSuccess();

        assertIsNotUnused("app.my-property");
    }

    @Test
    void configurationProperties_unusedNestedProperty_isFlagged() throws IOException {
        writeResourcesFile(testProjectDir, "application.properties",
                "app.database.host=localhost\napp.database.orphan-setting=unused");
        writeJavaSource(testProjectDir, "com.example", "DatabaseProperties", """
                package com.example;
                import org.springframework.boot.context.properties.ConfigurationProperties;
                @ConfigurationProperties(prefix = "app.database")
                public class DatabaseProperties {
                    private String host;
                    public String getHost() { return host; }
                    public void setHost(String host) { this.host = host; }
                }
                """);

        runAnalyze();
        runCheckUnused();

        assertIsUnused("app.database.orphan-setting");
        assertIsNotUnused("app.database.host");
    }

    @Test
    void additionalPatternFile_unusedProperty_isFlagged() throws IOException {
        writeResourcesFile(testProjectDir, "application.properties", "app.name=base");
        writeResourcesFile(testProjectDir, "application-dev.properties",
                "app.name=dev\napp.dev-only=unused");
        writeJavaSource(testProjectDir, "com.example", "MyService", """
                package com.example;
                import org.springframework.beans.factory.annotation.Value;
                public class MyService {
                    @Value("${app.name}") private String name;
                }
                """);

        runAnalyze("--additionalPropertiesPattern=application-dev.properties");
        runCheckUnused("--additionalPropertiesPattern=application-dev.properties");

        assertIsUnused("app.dev-only");
        assertIsNotUnused("app.name");
    }

    @Test
    void additionalPatternFile_usedProperty_isNotFlagged() throws IOException {
        writeResourcesFile(testProjectDir, "application.properties", "app.name=base");
        writeResourcesFile(testProjectDir, "application-dev.properties", "app.dev-feature=enabled");
        writeJavaSource(testProjectDir, "com.example", "MyService", """
                package com.example;
                import org.springframework.beans.factory.annotation.Value;
                public class MyService {
                    @Value("${app.name}") private String name;
                    @Value("${app.dev-feature}") private String devFeature;
                }
                """);

        runAnalyze("--additionalPropertiesPattern=application-dev.properties");
        runCheckUnusedExpectSuccess("--additionalPropertiesPattern=application-dev.properties");

        assertIsNotUnused("app.dev-feature");
    }

    @Test
    void report_containsCorrectProjectName() throws IOException {
        writeResourcesFile(testProjectDir, "application.properties", "app.orphan=value");
        writeJavaSource(testProjectDir, "com.example", "MyService", """
                package com.example;
                public class MyService {}
                """);

        runAnalyze();
        runCheckUnused();

        assertEquals("test-project", getUnusedReport().projectName);
    }

    @Test
    void report_containsAnalysisDate() throws IOException {
        writeResourcesFile(testProjectDir, "application.properties", "app.orphan=value");
        writeJavaSource(testProjectDir, "com.example", "MyService", """
                package com.example;
                public class MyService {}
                """);

        runAnalyze();
        runCheckUnused();

        assertNotNull(getUnusedReport().analysisDate);
        assertFalse(getUnusedReport().analysisDate.isBlank());
    }
}
