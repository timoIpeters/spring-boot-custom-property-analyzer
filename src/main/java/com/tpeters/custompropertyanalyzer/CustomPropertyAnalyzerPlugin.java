package com.tpeters.custompropertyanalyzer;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

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
