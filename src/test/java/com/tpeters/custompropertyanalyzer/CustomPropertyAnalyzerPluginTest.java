package com.tpeters.custompropertyanalyzer;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.tpeters.custompropertyanalyzer.TestUtils.*;

class CustomPropertyAnalyzerPluginTest extends AbstractPluginTest {
    @Test
    void pluginRegistersTask() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("com.tpeters.custom-property-analyzer");

        assertNotNull(project.getTasks().findByName("analyzeCustomProperties"));
    }

    @Test
    void testCustomOutputFile() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "SimpleService", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class SimpleService {
              @Value("${app.test}") private String test;
            }
            """);

        GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("analyzeCustomProperties", "--outputFile=my-report.json")
            .withPluginClasspath()
            .build();

        File reportFile = testProjectDir.resolve("build/reports/my-report.json").toFile();
        assertTrue(reportFile.exists(), "Custom report file should exist");
    }

    @Test
    void testMultiModuleAggregation() throws IOException {
        // Setup root with a subproject
        writeFile(settingsFile, """
            rootProject.name = 'root-project'
            include 'sub-app'
            """);

        writeFile(buildFile, """
            plugins {
              id 'com.tpeters.custom-property-analyzer'
            }
            subprojects {
              apply plugin: 'java'
            }
            """);

        // Create Java file in subproject
        writeJavaSource(testProjectDir.resolve("sub-app"), "com.example", "SubService", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class SubService {
              @Value("${sub.property}") private String val;
            }
            """);

        runAnalyze();
        
        String report = Files.readString(testProjectDir.resolve("build/reports/custom-properties-analysis.json"));
        assertTrue(report.contains("sub.property"), "Report should aggregate properties from subprojects");
    }
}
