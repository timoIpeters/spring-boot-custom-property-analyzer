package com.tpeters.custompropertyanalyzer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.options.Option;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalyzeCustomPropertiesTask extends DefaultTask {

    /**
     * Matches Spring @Value annotations with property placeholders.
     * <p>
     * Pattern breakdown:
     * <ul>
     *   <li>{@code @Value} - Literal annotation name</li>
     *   <li>{@code \\(\\s*} - Opening parenthesis with optional whitespace</li>
     *   <li>{@code ["']} - Single or double quote</li>
     *   <li>{@code \\$\\}{@code {}} - Property placeholder start</li>
     *   <li>{@code ([^}]+)} - <b>Group 1:</b> Property key (any characters except</li>
     *   <li>{@code (?::([^}]*))?} - <b>Group 2:</b> Optional default value after colon</li>
     *   <li>{@code \\}} - Closing brace of placeholder</li>
     *   <li>{@code ["']} - Closing quote</li>
     * </ul>
     * <p>
     * Examples matched:
     * <ul>
     *   <li>{@code @Value("${app.name}")} → key: "app.name", default: null</li>
     *   <li>{@code @Value("${server.port:8080}")} → key: "server.port", default: "8080"</li>
     *   <li>{@code @Value('${db.host:localhost}')} → key: "db.host", default: "localhost"</li>
     * </ul>
     */
    private static final Pattern VALUE_PATTERN = Pattern.compile(
            "@Value\\(\\s*[\"']\\$\\{([^}:]+)(?::([^}]*))?\\}[\"']\\s*\\)"
    );

    /**
     * Matches Spring @ConfigurationProperties annotations to extract the prefix.
     * <p>
     * Pattern breakdown:
     * <ul>
     *   <li>{@code @ConfigurationProperties} - Literal annotation name</li>
     *   <li>{@code \\(\\s*} - Opening parenthesis with optional whitespace</li>
     *   <li>{@code (?:prefix\\s*=\\s*)?} - Optional "prefix = " (non-capturing group)</li>
     *   <li>{@code ["']} - Single or double quote</li>
     *   <li>{@code ([^"']+)} - <b>Group 1:</b> The prefix value (any characters except quotes)</li>
     *   <li>{@code ["']} - Closing quote</li>
     * </ul>
     * <p>
     * Examples matched:
     * <ul>
     *   <li>{@code @ConfigurationProperties("app.database")} → prefix: "app.database"</li>
     *   <li>{@code @ConfigurationProperties(prefix = "app.mail")} → prefix: "app.mail"</li>
     *   <li>{@code @ConfigurationProperties(  prefix="app.cache"  )} → prefix: "app.cache"</li>
     * </ul>
     */
    private static final Pattern CONFIG_PROPS_CLASS_PATTERN = Pattern.compile(
            "@ConfigurationProperties\\(\\s*(?:prefix\\s*=\\s*)?[\"']([^\"']+)[\"']\\s*\\)"
    );

    /**
     * Matches private field declarations to extract both the field type and field name.
     * <p>
     * Pattern breakdown:
     * <ul>
     *   <li>{@code private} - Literal 'private' keyword</li>
     *   <li>{@code \\s+} - One or more whitespace characters</li>
     *   <li>{@code (\\S+(?:<[^>]+>)?)} - <b>Group 1:</b> The field type, including optional generic
     *       type parameters (e.g., {@code String}, {@code List<String>}, {@code Map<String, Foo>})</li>
     *   <li>{@code \\s+} - One or more whitespace characters</li>
     *   <li>{@code (\\w+)} - <b>Group 2:</b> The field name (word characters: letters, digits, underscore)</li>
     *   <li>{@code \\s*;} - Optional whitespace and semicolon</li>
     * </ul>
     * <p>
     * Examples matched:
     * <ul>
     *   <li>{@code private String username;} → type: "String", fieldName: "username"</li>
     *   <li>{@code private int maxConnections;} → type: "int", fieldName: "maxConnections"</li>
     *   <li>{@code private boolean sslEnabled;} → type: "boolean", fieldName: "sslEnabled"</li>
     *   <li>{@code private List<String> queries;} → type: "List<String>", fieldName: "queries"</li>
     *   <li>{@code private Map<String, DatabaseConfig> dbConfigs;} → type: "Map<String, DatabaseConfig>", fieldName: "dbConfigs"</li>
     *   <li>{@code private Author author;} → type: "Author", fieldName: "author"</li>
     * </ul>
     * <p>
     * The captured type (Group 1) is used to determine whether a field holds a complex user-defined
     * object that should be recursively expanded, a Map whose value type should be expanded under a
     * {@code [*]} wildcard key, or a simple/collection type that is recorded as a leaf property.
     * <p>
     * Note: Field names are converted to kebab-case (e.g., maxConnections → max-connections)
     * when combined with the @ConfigurationProperties prefix.
     */
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "private\\s+(\\S+(?:<[^>]+>)?)\\s+(\\w+)\\s*;"
    );

    private static final Set<String> SIMPLE_TYPES = Set.of(
            "String", "int", "long", "double", "float", "boolean",
            "Integer", "Long", "Double", "Float", "Boolean",
            "BigDecimal", "BigInteger", "Duration", "LocalDate", "LocalDateTime"
    );

    @Input
    @org.gradle.api.tasks.Optional
    private String outputFile = "custom-properties-analysis.json";

    @Input
    @org.gradle.api.tasks.Optional
    private String additionalPropertiesPattern = null;

    @Input
    private boolean verboseMode = false;

    @Option(option = "additionalPropertiesPattern", description = "Glob pattern for additional .properties files to include in default value analysis (e.g. 'application-dev*.properties')")
    public void setAdditionalPropertiesPattern(String additionalPropertiesPattern) {
        this.additionalPropertiesPattern = additionalPropertiesPattern;
    }

    @Option(option = "verbose", description = "Enable verbose console output")
    public void setVerboseMode(boolean verboseMode) {
        this.verboseMode = verboseMode;
    }

    public String getAdditionalPropertiesPattern() {
        return additionalPropertiesPattern;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    public boolean getVerboseMode() {
        return verboseMode;
    }

    @OutputFile
    public Provider<RegularFile> getReportFile() {
        return getProject().getLayout().getBuildDirectory().file("reports/" + outputFile);
    }

    @TaskAction
    public void analyze() {
        Set<PropertyInfo> properties = new TreeSet<>(Comparator.comparing(PropertyInfo::fullPath));

        Map<String, String> projectProperties = loadProjectProperties();

        FileTree javaFiles = getJavaFiles(getProject());
        for (File file : javaFiles.getFiles()) {
            if (file.getName().endsWith(".java")) {
                analyzeJavaFile(file, properties, projectProperties);
            }
        }

        exportToJson(properties);
        if (verboseMode) {
            printResults(properties);
        }
    }

    /**
     * Loads property values used for default value resolution during analysis.
     * <p>
     * By default, only {@code application.properties} is read, as it is the only
     * file guaranteed to be active regardless of the active Spring profile.
     * <p>
     * If {@code additionalPropertiesPattern} is set, any {@code .properties} file
     * in {@code src/main/resources} whose name matches that glob pattern is also
     * included. Values from additional files take precedence over
     * {@code application.properties} values for the same key — last file wins.
     * <p>
     * Example pattern values:
     * <ul>
     *   <li>{@code application-dev.properties} - exact file name</li>
     *   <li>{@code application-dev*.properties} - all dev variant files</li>
     *   <li>{@code application-*.properties} - all profile-specific files</li>
     * </ul>
     *
     * @return a map of property key → value
     */
    private Map<String, String> loadProjectProperties() {
        Map<String, String> result = new LinkedHashMap<>();

        Set<Project> projectsToAnalyze = getProject().getSubprojects().isEmpty()
                ? Collections.singleton(getProject())
                : getProject().getAllprojects();

        for (Project p : projectsToAnalyze) {
            File resourcesDir = new File(p.getProjectDir(), "src/main/resources");
            if (!resourcesDir.exists()) continue;

            File[] allPropFiles = resourcesDir.listFiles(
                    f -> f.isFile() && (f.getName().endsWith(".properties") || f.getName().endsWith(".yml") || f.getName().endsWith(".yaml"))
            );
            if (allPropFiles == null) continue;

            // Load baseline files in priority order: .properties first, then .yml, then .yaml
            List<File> filesToLoad = new ArrayList<>();
            String[] baselines = {"application.properties", "application.yml", "application.yaml"};
            for (String baseline : baselines) {
                for (File f : allPropFiles) {
                    if (f.getName().equals(baseline)) {
                        filesToLoad.add(f);
                        break;
                    }
                }
            }

            // If a pattern is specified, also include matching files
            if (additionalPropertiesPattern != null && !additionalPropertiesPattern.isBlank()) {
                PathMatcher matcher = FileSystems.getDefault()
                        .getPathMatcher("glob:" + additionalPropertiesPattern);
                for (File f : allPropFiles) {
                    boolean isBaseline = false;
                    for (String b : baselines) if (f.getName().equals(b)) isBaseline = true;

                    if (!isBaseline && matcher.matches(Path.of(f.getName()))) {
                        filesToLoad.add(f);
                        if (verboseMode) {
                            getLogger().lifecycle("Including additional properties file: " + f.getName());
                        }
                    }
                }
            }

            Yaml yaml = new Yaml();
            for (File propFile : filesToLoad) {
                try (InputStream is = new FileInputStream(propFile)) {
                    if (propFile.getName().endsWith(".properties")) {
                        Properties props = new Properties();
                        props.load(is);
                        for (String key : props.stringPropertyNames()) {
                            result.put(key, props.getProperty(key));
                        }
                    } else {
                        Map<String, Object> yamlMap = yaml.load(is);
                        if (yamlMap != null) {
                            flattenYaml("", yamlMap, result);
                        }
                    }
                    if (verboseMode) {
                        getLogger().lifecycle("Loaded properties from: " + propFile.getName());
                    }
                } catch (IOException e) {
                    getLogger().error("Failed to read properties file: {}", propFile.getAbsolutePath(), e);
                }
            }
        }

        return result;
    }

    /**
     * Recursively flattens a nested YAML map structure into a flat map of dot-notation keys.
     * <p>
     * For example, a YAML structure like:
     * <pre>
     * app:
     *   server:
     *     port: 8080
     * </pre>
     * is converted into a map entry with key {@code "app.server.port"} and value {@code "8080"}.
     *
     * @param prefix  The current key prefix being built (empty for the root level).
     * @param yamlMap The current nested map being processed.
     * @param result  The accumulator map where flattened property key-value pairs are stored.
     */
    @SuppressWarnings("unchecked")
    private void flattenYaml(String prefix, Map<String, Object> yamlMap, Map<String, String> result) {
        for (Map.Entry<String, Object> entry : yamlMap.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                flattenYaml(key, (Map<String, Object>) value, result);
            } else if (value instanceof List) {
                result.put(key, value.toString());
            } else if (value != null) {
                result.put(key, value.toString());
            }
        }
    }

    private void analyzeJavaFile(File file, Set<PropertyInfo> properties, Map<String, String> projectProperties) {
        try {
            String filename = file.getName();
            String content = Files.readString(file.toPath());
            properties.addAll(extractValueProperties(filename, content, projectProperties));
            properties.addAll(extractConfigurationProperties(filename, content, projectProperties));
        } catch (IOException e) {
            getLogger().error("Failed to read file: {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * Finds all @Value annotations in a file and extract their PropertyInfos. Also sets a default value if found.
     * For the default value, a value in .properties files always "wins" against defaults directly added within the
     * \@Value annotation
     * @param filename - The file name
     * @param content - The file content
     * @return Set of all extracted PropertyInfos
     */
    private Set<PropertyInfo> extractValueProperties(String filename, String content, Map<String, String> projectProperties) {
        Set<PropertyInfo> properties = new HashSet<>();
        Matcher valueMatcher = VALUE_PATTERN.matcher(content);

        while (valueMatcher.find()) {
            String propertyKey = valueMatcher.group(1).trim();
            String defaultValue = valueMatcher.group(2);

            if (defaultValue == null) {
                defaultValue = projectProperties.get(propertyKey);
            }

            String propertiesFileValue = projectProperties.get(propertyKey);
            if (propertiesFileValue != null) {
                defaultValue = propertiesFileValue;
            }

            properties.add(new PropertyInfo(
                    propertyKey,
                    defaultValue,
                    PropertySource.VALUE_ANNOTATION,
                    filename
            ));
        }

        return properties;
    }

    /**
     * Finds all @ConfigurationProperties annotations in a file and extract their PropertyInfos based on the property prefix
     * and field names.
     *
     * @param filename - The file name
     * @param content - The file content
     * @return Set of all extracted PropertyInfos
     */
    private Set<PropertyInfo> extractConfigurationProperties(String filename, String content, Map<String, String> projectProperties) {
        Set<PropertyInfo> properties = new HashSet<>();
        Matcher configPropsMatcher = CONFIG_PROPS_CLASS_PATTERN.matcher(content);
        if (configPropsMatcher.find()) {
            String prefix = configPropsMatcher.group(1);
            extractFieldProperties(filename, content, prefix, properties, new HashSet<>(), projectProperties);
        }
        return properties;
    }


    /**
     * Recursively extracts properties from a class body, following complex field
     * types into their own source files.
     *
     * @param filename   the file where these fields were found (for location reporting)
     * @param content    the Java source content to scan for fields
     * @param prefix     the current property key prefix (e.g. "app.database.author")
     * @param properties the accumulator set
     * @param visited    tracks already-visited type names to prevent infinite recursion
     */
    private void extractFieldProperties(
            String filename,
            String content,
            String prefix,
            Set<PropertyInfo> properties,
            Set<String> visited,
            Map<String, String> projectProperties   // added
    ) {
        Project project = getProject();
        FileTree javaFiles = getJavaFiles(project);

        Matcher fieldMatcher = FIELD_PATTERN.matcher(content);
        while (fieldMatcher.find()) {
            String rawType  = fieldMatcher.group(1); // e.g. "Map<String,DatabaseConfig>", "Author", "boolean"
            String fieldName = fieldMatcher.group(2);
            String kebabField = camelToKebab(fieldName);
            String fullPath = prefix + "." + kebabField;

            // Map<K, ComplexType> → expand as prefix.[*].subField
            String mapValueType = extractMapValueType(rawType);
            if (mapValueType != null && isComplexType(mapValueType) && !visited.contains(mapValueType)) {
                visited.add(mapValueType);
                File typeFile = resolveTypeFile(mapValueType, javaFiles);
                if (typeFile != null) {
                    try {
                        String typeContent = Files.readString(typeFile.toPath());
                        // [*] indicates a dynamic map key
                        extractFieldProperties(typeFile.getName(), typeContent,
                                fullPath + ".[*]", properties, visited, projectProperties);
                    } catch (IOException e) {
                        getLogger().error("Failed to read file for type: {}", mapValueType, e);
                    }
                }
                // record the map property itself as a node as well
                properties.add(new PropertyInfo(fullPath, null, PropertySource.CONFIGURATION_PROPERTIES, filename));
                continue;
            }

            // Complex user-defined type → recurse into its fields
            String baseType = rawType.replaceAll("<.*>", "").trim();
            if (isComplexType(baseType) && !visited.contains(baseType)) {
                visited.add(baseType);
                File typeFile = resolveTypeFile(baseType, javaFiles);
                if (typeFile != null) {
                    try {
                        String typeContent = Files.readString(typeFile.toPath());
                        extractFieldProperties(typeFile.getName(), typeContent,
                                fullPath, properties, visited, projectProperties);
                    } catch (IOException e) {
                        getLogger().error("Failed to read file for type: {}", baseType, e);
                    }
                } else {
                    // Type not found in sources (maybe external) — record the field as-is
                    properties.add(new PropertyInfo(fullPath, null, PropertySource.CONFIGURATION_PROPERTIES, filename));
                }
                continue;
            }

            // Simple / Collection / Map<K, SimpleV> → record directly
            properties.add(new PropertyInfo(
                    fullPath,
                    projectProperties.get(fullPath),    // look up default from .properties
                    PropertySource.CONFIGURATION_PROPERTIES,
                    filename
            ));
        }
    }

    /**
     * Centralized FileTree retrieval to avoid repeating source set lookups in multiple places.
     * @param project - The gradle project
     * @return the java FileTree
     */
    private FileTree getJavaFiles(Project project) {
        Set<Project> projectsToAnalyze = project.getSubprojects().isEmpty()
                ? Collections.singleton(project)
                : project.getAllprojects();
        FileTree combined = null;
        for (Project p : projectsToAnalyze) {
            SourceSetContainer sourceSets = p.getExtensions().findByType(SourceSetContainer.class);
            if (sourceSets == null) continue;
            SourceSet mainSourceSet = sourceSets.findByName("main");
            if (mainSourceSet == null) continue;
            FileTree ft = mainSourceSet.getAllJava();
            combined = (combined == null) ? ft : combined.plus(ft);
        }
        return combined != null ? combined : getProject().files().getAsFileTree();
    }

    /**
     * Converts camelCase field names e.g. projectName to their kebab-case equivalent e.g. project-name.
     * @param camelCase - The field name in camelCase
     * @return The field name in kebab-case
     */
    private String camelToKebab(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    /**
     * Attempts to find the .java source file for a given simple class name within the files that were collected for analysis.
     * @param simpleTypeName - The class name
     * @param javaFiles - The file tree containing all java files
     * @return The file based on the given class name
     */
    private File resolveTypeFile(String simpleTypeName, FileTree javaFiles) {
        for (File file : javaFiles.getFiles()) {
            if (file.getName().equals(simpleTypeName + ".java")) {
                return file;
            }
        }
        return null;
    }

    /**
     * Checks if a given type name is a complex type that should be recursively expanded
     *
     * @param typeName - The type name
     * @return true if the type name represents a complex user-defined object that should be recursively expanded
     */
    private boolean isComplexType(String typeName) {
        String baseType = typeName.replaceAll("<.*>", "").trim();
        return !SIMPLE_TYPES.contains(baseType)
                && !baseType.equals("List")
                && !baseType.equals("Set")
                && !baseType.equals("Map")
                && !baseType.startsWith("List<")
                && Character.isUpperCase(baseType.charAt(0));
    }

    /**
     * Given a raw field type string like "Map<String, DatabaseConfig>", returns the value type "DatabaseConfig",
     * or null if not a Map or not parseable.
     *
     * @param rawType - The raw map field type
     * @return Map value type or null if not a map or not parseable
     */
    private String extractMapValueType(String rawType) {
        Pattern mapPattern = Pattern.compile("Map<[^,]+,\\s*([^>]+)>");
        Matcher m = mapPattern.matcher(rawType);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    /**
     * Creates a .json file from a set of PropertyInfos in the format:
     *
     * <pre>
     * {
     *   "projectName": "my-project",
     *   "analysisDate": "Fri Feb 13 13:23:49 CET 2026",
     *   "totalProperties": 1,
     *   "properties": [
     *     {
     *       "key": "property.key",
     *       "defaultValue": "",
     *       "source": "VALUE_ANNOTATION|CONFIGURATION_PROPERTIES",
     *       "location": "File.java"
     *     }]
     * }
     * </pre>
     *
     * @param properties - Set of PropertyInfos
     */
    private void exportToJson(Set<PropertyInfo> properties) {
        File jsonFile = getReportFile().get().getAsFile();
        getProject().mkdir(jsonFile.getParentFile());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject root = new JsonObject();
        root.addProperty("projectName", getProject().getName());
        root.addProperty("analysisDate", new Date().toString());
        root.addProperty("totalProperties", properties.size());

        JsonArray propsArray = new JsonArray();
        for (PropertyInfo prop : properties) {
            JsonObject propObj = new JsonObject();
            propObj.addProperty("key", prop.fullPath());
            propObj.addProperty("defaultValue", prop.defaultValue());
            propObj.addProperty("source", prop.source().name());
            propObj.addProperty("location", prop.location());
            propsArray.add(propObj);
        }
        root.add("properties", propsArray);

        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(root, writer);
            getLogger().lifecycle("\n✓ JSON export saved to: " + jsonFile.getAbsolutePath());
        } catch (IOException e) {
            getLogger().error("Failed to export JSON", e);
        }
    }

    private void printResults(Set<PropertyInfo> properties) {
        getLogger().lifecycle("\n========================================");
        getLogger().lifecycle("Custom Property Analysis Results");
        getLogger().lifecycle("========================================\n");

        if (properties.isEmpty()) {
            getLogger().lifecycle("No custom properties found.");
            return;
        }

        Map<PropertySource, List<PropertyInfo>> grouped = new HashMap<>();
        for (PropertyInfo prop : properties) {
            grouped.computeIfAbsent(prop.source(), k -> new ArrayList<>()).add(prop);
        }

        for (PropertySource source : PropertySource.values()) {
            List<PropertyInfo> props = grouped.get(source);
            if (props != null && !props.isEmpty()) {
                getLogger().lifecycle(source.getDisplayName() + ":");
                getLogger().lifecycle("─".repeat(50));
                for (PropertyInfo prop : props) {
                    getLogger().lifecycle("  • " + prop.fullPath());
                    if (prop.defaultValue() != null) {
                        getLogger().lifecycle("    Default: " + prop.defaultValue());
                    }
                    getLogger().lifecycle("    Location: " + prop.location());
                    getLogger().lifecycle("");
                }
            }
        }

        getLogger().lifecycle("Total properties found: " + properties.size());
    }
}
