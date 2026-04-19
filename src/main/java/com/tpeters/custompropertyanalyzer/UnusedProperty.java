package com.tpeters.custompropertyanalyzer;

/**
 * Represents a property, which has not been defined in the project properties but is not used in the code
 *
 * @param key The property key
 * @param value The property value
 */
public record UnusedProperty(String key, String value) {}
