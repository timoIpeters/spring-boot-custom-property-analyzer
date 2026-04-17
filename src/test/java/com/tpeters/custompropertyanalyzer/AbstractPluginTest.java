package com.tpeters.custompropertyanalyzer;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.tpeters.custompropertyanalyzer.TestUtils.writeFile;

public abstract class AbstractPluginTest {

    /** Path to the test project directory */
    @TempDir
    protected Path testProjectDir;

    /** Build file of the test project */
    protected File buildFile;

    /** Settings file of the test project */
    protected File settingsFile;

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
    protected BuildResult runAnalyze() {
        return GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("analyzeCustomProperties")
            .withPluginClasspath()
            .build();
    }

    /**
     * Gets the content from the custom-properties-analysis.json, which is an output of the plugins
     * analyzeCustomProperties task.
     * @return String content of the custom-properties-analysis.json.
     * @throws IOException  If an I/O error occurs while parsing the .json file.
     */
    protected String getReportContent() throws IOException {
        File reportFile = testProjectDir.resolve("build/reports/custom-properties-analysis.json").toFile();
        if (!reportFile.exists()) {
            return "";
        }
        return Files.readString(reportFile.toPath());
    }
}
