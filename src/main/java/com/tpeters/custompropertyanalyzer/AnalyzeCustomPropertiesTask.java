package com.tpeters.custompropertyanalyzer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzeCustomPropertiesTask extends DefaultTask {

    /**
     * Matches Spring @Value annotations with property placeholders.
     * <p>
     * Pattern breakdown:
     * <ul>
     *   <li>{@code @Value} - Literal annotation name</li>
     *   <li>{@code \\(\\s*} - Opening parenthesis with optional whitespace</li>
     *   <li>{@code ["']} - Single or double quote</li>
     *   <li>{@code \\$\\}{@code {}} - Property placeholder start</li>
     *   <li>{@code ([^}]+)} - <b>Group 1:</b> Property key (any characters except</li>
     *   <li>{@code (?::([^}]*))?} - <b>Group 2:</b> Optional default value after colon</li>
     *   <li>{@code \\}} - Closing brace of placeholder</li>
     *   <li>{@code ["']} - Closing quote</li>
     * </ul>
     * <p>
     * Examples matched:
     * <ul>
     *   <li>{@code @Value("${app.name}")} → key: "app.name", default: null</li>
     *   <li>{@code @Value("${server.port:8080}")} → key: "server.port", default: "8080"</li>
     *   <li>{@code @Value('${db.host:localhost}')} → key: "db.host", default: "localhost"</li>
     * </ul>
     */
    private static final Pattern VALUE_PATTERN = Pattern.compile(
            "@Value\\(\\s*[\"']\\$\\{([^}]+)(?::([^}]*))?\\}[\"']\\s*\\)"
    );

    /**
     * Matches Spring @ConfigurationProperties annotations to extract the prefix.
     * <p>
     * Pattern breakdown:
     * <ul>
     *   <li>{@code @ConfigurationProperties} - Literal annotation name</li>
     *   <li>{@code \\(\\s*} - Opening parenthesis with optional whitespace</li>
     *   <li>{@code (?:prefix\\s*=\\s*)?} - Optional "prefix = " (non-capturing group)</li>
     *   <li>{@code ["']} - Single or double quote</li>
     *   <li>{@code ([^"']+)} - <b>Group 1:</b> The prefix value (any characters except quotes)</li>
     *   <li>{@code ["']} - Closing quote</li>
     * </ul>
     * <p>
     * Examples matched:
     * <ul>
     *   <li>{@code @ConfigurationProperties("app.database")} → prefix: "app.database"</li>
     *   <li>{@code @ConfigurationProperties(prefix = "app.mail")} → prefix: "app.mail"</li>
     *   <li>{@code @ConfigurationProperties(  prefix="app.cache"  )} → prefix: "app.cache"</li>
     * </ul>
     */
    private static final Pattern CONFIG_PROPS_CLASS_PATTERN = Pattern.compile(
            "@ConfigurationProperties\\(\\s*(?:prefix\\s*=\\s*)?[\"']([^\"']+)[\"']\\s*\\)"
    );

    /**
     * Matches private field declarations to extract field names.
     * <p>
     * Pattern breakdown:
     * <ul>
     *   <li>{@code private} - Literal 'private' keyword</li>
     *   <li>{@code \\s+} - One or more whitespace characters</li>
     *   <li>{@code \\S+} - The field type (any non-whitespace characters)</li>
     *   <li>{@code \\s+} - One or more whitespace characters</li>
     *   <li>{@code (\\w+)} - <b>Group 1:</b> The field name (word characters: letters, digits, underscore)</li>
     *   <li>{@code \\s*;} - Optional whitespace and semicolon</li>
     * </ul>
     * <p>
     * Examples matched:
     * <ul>
     *   <li>{@code private String username;} → fieldName: "username"</li>
     *   <li>{@code private int maxConnections;} → fieldName: "maxConnections"</li>
     *   <li>{@code private boolean sslEnabled;} → fieldName: "sslEnabled"</li>
     * </ul>
     * <p>
     * Note: Field names are converted to kebab-case (e.g., maxConnections → max-connections)
     * when combined with the @ConfigurationProperties prefix.
     */
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "private\\s+\\S+\\s+(\\w+)\\s*;"
    );

    @Input
    @Optional
    private String outputFile = "custom-properties-analysis.json";

    @Input
    private boolean verboseMode = false;

    public String getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    public boolean getVerboseMode() {
        return verboseMode;
    }

    @Option(option = "verbose", description = "Enable verbose console output")
    public void setVerboseMode(boolean verboseMode) {
        this.verboseMode = verboseMode;
    }

    @TaskAction
    public void analyze() {
        Set<PropertyInfo> properties = new TreeSet<>(Comparator.comparing(PropertyInfo::getFullPath));

        Project project = getProject();

        Set<Project> projectsToAnalyze = project.getSubprojects().isEmpty()
                ? Collections.singleton(project)
                : project.getAllprojects();

        for (Project p : projectsToAnalyze) {
            SourceSetContainer sourceSets = p.getExtensions().findByType(SourceSetContainer.class);
            if (sourceSets == null) continue;

            SourceSet mainSourceSet = sourceSets.findByName("main");
            if (mainSourceSet == null) continue;

            FileTree javaFiles = mainSourceSet.getAllJava();

            for (File file : javaFiles.getFiles()) {
                if (file.getName().endsWith(".java")) {
                    analyzeJavaFile(file, properties);
                }
            }
        }

        exportToJson(properties);

        if (verboseMode) {
            printResults(properties);
        }
    }

    private void analyzeJavaFile(File file, Set<PropertyInfo> properties) {
        try {
            String content = Files.readString(file.toPath());

            // Find @Value annotations
            Matcher valueMatcher = VALUE_PATTERN.matcher(content);
            while (valueMatcher.find()) {
                String propertyKey = valueMatcher.group(1);
                String defaultValue = valueMatcher.group(2);
                properties.add(new PropertyInfo(
                        propertyKey,
                        defaultValue,
                        PropertySource.VALUE_ANNOTATION,
                        file.getName()
                ));
            }

            // Find @ConfigurationProperties
            Matcher configPropsMatcher = CONFIG_PROPS_CLASS_PATTERN.matcher(content);
            if (configPropsMatcher.find()) {
                String prefix = configPropsMatcher.group(1);

                // Extract field names from the class
                Matcher fieldMatcher = FIELD_PATTERN.matcher(content);
                while (fieldMatcher.find()) {
                    String fieldName = fieldMatcher.group(1);
                    String fullPath = prefix + "." + camelToKebab(fieldName);
                    properties.add(new PropertyInfo(
                            fullPath,
                            null,
                            PropertySource.CONFIGURATION_PROPERTIES,
                            file.getName()
                    ));
                }
            }

        } catch (IOException e) {
            getLogger().error("Failed to read file: " + file.getAbsolutePath(), e);
        }
    }

    private String camelToKebab(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    private void exportToJson(Set<PropertyInfo> properties) {
        File outputDir = new File(getProject().getBuildDir(), "reports");
        outputDir.mkdirs();
        File jsonFile = new File(outputDir, outputFile);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject root = new JsonObject();
        root.addProperty("projectName", getProject().getName());
        root.addProperty("analysisDate", new Date().toString());
        root.addProperty("totalProperties", properties.size());

        JsonArray propsArray = new JsonArray();
        for (PropertyInfo prop : properties) {
            JsonObject propObj = new JsonObject();
            propObj.addProperty("key", prop.getFullPath());
            propObj.addProperty("defaultValue", prop.getDefaultValue());
            propObj.addProperty("source", prop.getSource().name());
            propObj.addProperty("location", prop.getLocation());
            propsArray.add(propObj);
        }
        root.add("properties", propsArray);

        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(root, writer);
            getLogger().lifecycle("\n✓ JSON export saved to: " + jsonFile.getAbsolutePath());
        } catch (IOException e) {
            getLogger().error("Failed to export JSON", e);
        }
    }

    private void printResults(Set<PropertyInfo> properties) {
        getLogger().lifecycle("\n========================================");
        getLogger().lifecycle("Custom Property Analysis Results");
        getLogger().lifecycle("========================================\n");

        if (properties.isEmpty()) {
            getLogger().lifecycle("No custom properties found.");
            return;
        }

        Map<PropertySource, List<PropertyInfo>> grouped = new HashMap<>();
        for (PropertyInfo prop : properties) {
            grouped.computeIfAbsent(prop.getSource(), k -> new ArrayList<>()).add(prop);
        }

        for (PropertySource source : PropertySource.values()) {
            List<PropertyInfo> props = grouped.get(source);
            if (props != null && !props.isEmpty()) {
                getLogger().lifecycle(source.getDisplayName() + ":");
                getLogger().lifecycle("─".repeat(50));
                for (PropertyInfo prop : props) {
                    getLogger().lifecycle("  • " + prop.getFullPath());
                    if (prop.getDefaultValue() != null) {
                        getLogger().lifecycle("    Default: " + prop.getDefaultValue());
                    }
                    getLogger().lifecycle("    Location: " + prop.getLocation());
                    getLogger().lifecycle("");
                }
            }
        }

        getLogger().lifecycle("Total properties found: " + properties.size());
    }
}
