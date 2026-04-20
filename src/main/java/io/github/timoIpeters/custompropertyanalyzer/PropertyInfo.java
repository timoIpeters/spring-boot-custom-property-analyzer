package io.github.timoIpeters.custompropertyanalyzer;

/**
 * Represents a single custom property discovered during analysis of a Spring Boot project.
 * <p>
 * Each instance captures where the property was found, how it is sourced, and what default
 * value it carries. Equality and hashing are based solely on {@code fullPath}, so that the
 * same property key discovered in multiple passes is deduplicated in the result set.
 *
 * @param fullPath     The fully qualified property key in canonical kebab-case
 *                     (e.g. {@code app.database.max-connections}).
 * @param defaultValue The resolved default value for this property, or {@code null} if none
 *                     could be determined. Values from configuration files take precedence
 *                     over defaults declared inline in {@code @Value} annotations.
 * @param source       How this property was discovered — see {@link PropertySource}.
 * @param location     The simple file name of the Java source file where this property
 *                     was found (e.g. {@code DatabaseProperties.java}).
 */
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
