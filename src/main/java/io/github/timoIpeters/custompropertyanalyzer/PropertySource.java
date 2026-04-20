package io.github.timoIpeters.custompropertyanalyzer;

/**
 * Describes how a custom property was discovered during source analysis.
 * <p>
 * This value is included in the JSON report under the {@code "source"} field for each
 * property entry, allowing consumers of the report to distinguish between the two
 * Spring mechanisms for externalizing configuration.
 */
public enum PropertySource {
    /**
     * The property was discovered via a {@code @Value("${my.property}")} annotation
     * on a field or constructor parameter.
     */
    VALUE_ANNOTATION("@Value Annotations"),

    /**
     * The property was discovered by expanding the fields of a class annotated with
     * {@code @ConfigurationProperties}.
     */
    CONFIGURATION_PROPERTIES("@ConfigurationProperties Classes");

    private final String displayName;

    PropertySource(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns a human-readable label for this source, used in console output when
     * running the analysis task with {@code --verbose}.
     *
     * @return the display name (e.g. {@code "@Value Annotations"})
     */
    public String getDisplayName() {
        return displayName;
    }
}
