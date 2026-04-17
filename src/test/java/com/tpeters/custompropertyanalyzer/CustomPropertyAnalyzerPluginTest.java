package com.tpeters.custompropertyanalyzer;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CustomPropertyAnalyzerPluginTest {
    @Test
    void pluginRegistersTask() {
        Project project = ProjectBuilder.builder().build();
        project.getPlugins().apply("com.tpeters.custom-property-analyzer");

        assertNotNull(project.getTasks().findByName("analyzeCustomProperties"));
    }
}
