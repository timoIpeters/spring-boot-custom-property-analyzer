package com.tpeters.custompropertyanalyzer;

import java.util.List;

public class UnusedPropertiesReport {
    String projectName;
    String analysisDate;
    int totalUnusedProperties;
    List<UnusedPropertyEntry> unusedProperties;

    UnusedPropertyEntry findProperty(String key) {
        if (unusedProperties == null) return null;
        return unusedProperties.stream()
                .filter(p -> key.equals(p.key))
                .findFirst()
                .orElse(null);
    }
}
