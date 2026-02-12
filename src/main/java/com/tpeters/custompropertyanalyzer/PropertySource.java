package com.tpeters.custompropertyanalyzer;

public enum PropertySource {
    VALUE_ANNOTATION("@Value Annotations"),
    CONFIGURATION_PROPERTIES("@ConfigurationProperties Classes");

    private final String displayName;

    PropertySource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
