package com.tpeters.custompropertyanalyzer;

public class PropertyInfo {
    private final String fullPath;
    private final String defaultValue;
    private final PropertySource source;
    private final String location;

    public PropertyInfo(String fullPath, String defaultValue, PropertySource source, String location) {
        this.fullPath = fullPath;
        this.defaultValue = defaultValue;
        this.source = source;
        this.location = location;
    }

    public String getFullPath() {
        return fullPath;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public PropertySource getSource() {
        return source;
    }

    public String getLocation() {
        return location;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyInfo that = (PropertyInfo) o;
        return fullPath.equals(that.fullPath);
    }

    @Override
    public int hashCode() {
        return fullPath.hashCode();
    }
}
