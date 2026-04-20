package io.github.timoIpeters.custompropertyanalyzer;

import java.util.List;

/**
 * Test DTO mirroring the JSON output for robust assertions.
 */
public class AnalysisReport {
    public String projectName;
    public String analysisDate;
    public int totalProperties;
    public List<PropertyEntry> properties;

    public static class PropertyEntry {
        public String key;
        public String defaultValue;
        public String source;
        public String location;
    }

    public PropertyEntry findProperty(String key) {
        return properties.stream()
                .filter(p -> p.key.equals(key))
                .findFirst()
                .orElse(null);
    }
}
