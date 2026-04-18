package com.tpeters.custompropertyanalyzer;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Entry point for the Custom Property Analyzer Gradle plugin.
 * <p>
 * Applying this plugin registers the following tasks:
 * <ul>
 *   <li>{@code analyzeCustomProperties} — scans the project's Java source files for
 *       {@code @Value} and {@code @ConfigurationProperties} annotations and produces a
 *       JSON report of all discovered custom properties and their default values.</li>
 *   <li>{@code checkUnusedProperties} — compares the properties defined in configuration
 *       files against those discovered by {@code analyzeCustomProperties} and flags any
 *       that are never referenced in code. Depends on {@code analyzeCustomProperties} and
 *       fails the build by default if unused properties are found.</li>
 * </ul>
 * <p>
 * The plugin also applies the {@code java} plugin if not already present, as it relies
 * on the main source set to locate Java files for analysis.
 */
public class CustomPropertyAnalyzerPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java");

        project.getTasks().register("analyzeCustomProperties", AnalyzeCustomPropertiesTask.class);

        project.getTasks().register("checkUnusedProperties", CheckUnusedPropertiesTask.class, task -> {
            task.dependsOn("analyzeCustomProperties");
        });
    }

}
