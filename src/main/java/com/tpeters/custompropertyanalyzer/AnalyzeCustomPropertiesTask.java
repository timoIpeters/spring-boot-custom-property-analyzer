package com.tpeters.custompropertyanalyzer;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzeCustomPropertiesTask extends DefaultTask {

    private static final Pattern VALUE_PATTERN = Pattern.compile(
            "@Value\\(\\s*[\"']\\$\\{([^}]+)(?::([^}]*))?\\}[\"']\\s*\\)"
    );

    private static final Pattern CONFIG_PROPS_CLASS_PATTERN = Pattern.compile(
            "@ConfigurationProperties\\(\\s*(?:prefix\\s*=\\s*)?[\"']([^\"']+)[\"']\\s*\\)"
    );

    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "private\\s+\\S+\\s+(\\w+)\\s*;"
    );

    @TaskAction
    public void analyze() {
        Set<PropertyInfo> properties = new TreeSet<>(Comparator.comparing(PropertyInfo::getFullPath));

        SourceSetContainer sourceSets = getProject().getExtensions()
                .getByType(SourceSetContainer.class);

        SourceSet mainSourceSet = sourceSets.getByName("main");
        FileTree javaFiles = mainSourceSet.getAllJava();

        for (File file : javaFiles.getFiles()) {
            if (file.getName().endsWith(".java")) {
                analyzeJavaFile(file, properties);
            }
        }

        printResults(properties);
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
