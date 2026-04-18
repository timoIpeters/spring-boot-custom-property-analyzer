package com.tpeters.custompropertyanalyzer;

import com.google.gson.Gson;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.tpeters.custompropertyanalyzer.TestUtils.writeFile;
import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractPluginTest {

    /** Path to the test project directory */
    @TempDir
    protected Path testProjectDir;

    /** Build file of the test project */
    protected File buildFile;

    /** Settings file of the test project */
    protected File settingsFile;

    private final Gson gson = new Gson();

    @BeforeEach
    void setup() throws IOException {
        settingsFile = testProjectDir.resolve("settings.gradle").toFile();
        buildFile = testProjectDir.resolve("build.gradle").toFile();
        setupBasicProject();
    }

    /**
     * Sets up the basic test project by setting a rootProject.name and applying the necessary plugins
     * @throws IOException  If an I/O error occurs while writing the file.
     */
    private void setupBasicProject() throws IOException {
        writeFile(settingsFile, "rootProject.name = 'test-project'");
        writeFile(buildFile, """
            plugins {
              id 'java'
              id 'com.tpeters.custom-property-analyzer'
            }
            """);
    }

    /**
     * Runs the analyzeCustomProperties task within the test project
     * @return The Gradle build result
     */
    protected BuildResult runAnalyze(String... extraArgs) {
        List<String> args = new ArrayList<>();
        args.add("analyzeCustomProperties");
        args.addAll(Arrays.asList(extraArgs));

        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments(args)
            .withPluginClasspath()
            .build();

        System.out.println(result.getOutput());
        return result;
    }

    /**
     * Runs the checkUnusedProperties task within the test project.
     * Uses forwardOutput() so failures print to the test console.
     */
    protected BuildResult runCheckUnused(String... extraArgs) {
        List<String> args = new ArrayList<>();
        args.add("checkUnusedProperties");
        args.addAll(Arrays.asList(extraArgs));
        return GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(args)
                .withPluginClasspath()
                .forwardOutput()
                .buildAndFail(); // default: expect failure when unused props exist
    }

    /**
     * Same as runCheckUnused but expects the build to succeed (no unused props,
     * or --ignoreUnused was passed).
     */
    protected BuildResult runCheckUnusedExpectSuccess(String... extraArgs) {
        List<String> args = new ArrayList<>();
        args.add("checkUnusedProperties");
        args.addAll(Arrays.asList(extraArgs));
        return GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments(args)
                .withPluginClasspath()
                .forwardOutput()
                .build();
    }

    /**
     * Gets the content from the custom-properties-analysis.json as DTO, which is an output of the plugin's
     * analyzeCustomProperties task.
     * @return Content of the custom-properties-analysis.json as simple Java DTO.
     * @throws IOException  If an I/O error occurs while parsing the .json file.
     */
    protected AnalysisReport getReport() throws IOException {
        File reportFile = testProjectDir.resolve("build/reports/custom-properties-analysis.json").toFile();
        if (!reportFile.exists()) {
            throw new RuntimeException("Report file not found!");
        }
        String content = Files.readString(reportFile.toPath());
        return gson.fromJson(content, AnalysisReport.class);
    }

    protected UnusedPropertiesReport getUnusedReport() throws IOException {
        File reportFile = testProjectDir.resolve("build/reports/unused-properties-report.json").toFile();
        if (!reportFile.exists()) {
            throw new RuntimeException("Unused properties report file not found!");
        }
        String content = Files.readString(reportFile.toPath());
        return gson.fromJson(content, UnusedPropertiesReport.class);
    }

    /**
     * Asserts that a property with a given key is set (not null) and has the expected default and source.
     *
     * @param key The property key
     * @param expectedDefault The expected property default value
     * @param expectedSource The expected {@link PropertySource}
     * @throws IOException If an I/O error occurs while reading the analysis report
     */
    protected void assertHasProperty(String key, String expectedDefault, String expectedSource) throws IOException {
        AnalysisReport report = getReport();
        AnalysisReport.PropertyEntry entry = report.findProperty(key);
        assertNotNull(entry, "Property not found: " + key);
        if (expectedDefault != null) {
            assertEquals(expectedDefault, entry.defaultValue, "Wrong default for " + key);
        }
        if (expectedSource != null) {
            assertEquals(expectedSource, entry.source, "Wrong source for " + key);
        }
    }

    protected void assertIsUnused(String key) throws IOException {
        UnusedPropertiesReport report = getUnusedReport();
        assertNotNull(report.findProperty(key), "Expected property to be flagged as unused: " + key);
    }

    protected void assertIsNotUnused(String key) throws IOException {
        UnusedPropertiesReport report = getUnusedReport();
        assertNull(report.findProperty(key), "Expected property NOT to be flagged as unused: " + key);
    }
}
