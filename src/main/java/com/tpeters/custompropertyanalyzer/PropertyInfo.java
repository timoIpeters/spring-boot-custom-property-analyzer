package com.tpeters.custompropertyanalyzer;

public record PropertyInfo(String fullPath, String defaultValue, PropertySource source, String location) {

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
