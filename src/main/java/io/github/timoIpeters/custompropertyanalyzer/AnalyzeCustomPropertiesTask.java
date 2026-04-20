package io.github.timoIpeters.custompropertyanalyzer;

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

/**
 * Analyzes a Spring Boot project's Java source files to discover all custom properties
 * defined via {@code @Value} and {@code @ConfigurationProperties} annotations.
 * <p>
 * The task scans all {@code .java} files in the main source set, extracts property keys
 * and their default values, and writes the results to a JSON report file. Default values
 * are resolved by consulting {@code application.properties}, {@code application.yml}, or
 * {@code application.yaml} in {@code src/main/resources}. Additional profile-specific
 * files can be included via {@link #getAdditionalPropertiesPattern()}.
 * <p>
 * Usage:
 * <pre>
 *   ./gradlew analyzeCustomProperties
 *   ./gradlew analyzeCustomProperties --verbose
 *   ./gradlew analyzeCustomProperties --outputFile=my-report.json
 *   ./gradlew analyzeCustomProperties --additionalPropertiesPattern="application-*.yml"
 * </pre>
 *
 * The report is written to {@code build/reports/custom-properties-analysis.json} by default.
 */
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

    private String outputFile = "custom-properties-analysis.json";
    private String additionalPropertiesPattern = null;
    private boolean verboseMode = false;

    /**
     * The name of the generated JSON report file.
     * <p>
     * The file is written to {@code build/reports/<outputFile>}.
     * Defaults to {@code custom-properties-analysis.json}.
     * @return The output file name
     */
    @Input
    @org.gradle.api.tasks.Optional
    public String getOutputFile() {
        return outputFile;
    }

    /**
     * Glob pattern for additional property files to include in default value resolution,
     * beyond the baseline {@code application.properties}, {@code application.yml}, and
     * {@code application.yaml}.
     * <p>
     * Example values:
     * <ul>
     *   <li>{@code application-dev.properties} - exact file name</li>
     *   <li>{@code application-dev*.properties} - all dev variant files</li>
     *   <li>{@code application-*.yml} - all profile-specific YAML files</li>
     * </ul>
     * If not set, only the baseline files are consulted.
     * @return The additional properties glob pattern
     */
    @Input
    @org.gradle.api.tasks.Optional
    public String getAdditionalPropertiesPattern() {
        return additionalPropertiesPattern;
    }

    /**
     * Whether to print the full analysis results to the console in addition to the JSON report.
     * Defaults to {@code false}.
     * @return True if verbose mode is enabled, otherwise false
     */
    @Input
    public boolean getVerboseMode() {
        return verboseMode;
    }

    /**
     * Option to set a custom output file
     *
     * @param outputFile The name of the generated JSON report file
     */
    @Option(option = "outputFile", description = "The name of the generated JSON report file")
    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * Option to set an additional properties glob pattern
     *
     * @param additionalPropertiesPattern Glob pattern for additional .properties files to include in default value analysis (e.g. 'application-dev*.properties')
     */
    @Option(option = "additionalPropertiesPattern", description = "Glob pattern for additional .properties files to include in default value analysis (e.g. 'application-dev*.properties')")
    public void setAdditionalPropertiesPattern(String additionalPropertiesPattern) {
        this.additionalPropertiesPattern = additionalPropertiesPattern;
    }

    /**
     * Option to enable/disable verbose mode
     * @param verboseMode Enable verbose console output
     */
    @Option(option = "verbose", description = "Enable verbose console output")
    public void setVerboseMode(boolean verboseMode) {
        this.verboseMode = verboseMode;
    }

    /**
     * The output file to which the JSON property report is written.
     * Resolved to {@code build/reports/<outputFile>}.
     * @return The output file provider, the JSON property is written to
     */
    @OutputFile
    public Provider<RegularFile> getReportFile() {
        return getProject().getLayout().getBuildDirectory().file("reports/" + outputFile);
    }

    /**
     * Task action that drives the full property analysis.
     */
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
                    for (String b : baselines) {
                        if (f.getName().equals(b)) {
                            isBaseline = true;
                            break;
                        }
                    }

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
                            result.put(toCanonicalKey(key), props.getProperty(key));
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
            String key = prefix.isEmpty() ? toCanonicalKey(entry.getKey()) : prefix + "." + toCanonicalKey(entry.getKey());
            Object value = entry.getValue();

            if (value instanceof Map) {
                flattenYaml(key, (Map<String, Object>) value, result);
            } else if (value instanceof List) {
                // Support list indexing (e.g. app.list[0])
                List<Object> list = (List<Object>) value;
                for (int i = 0; i < list.size(); i++) {
                    String listKey = key + "[" + i + "]";
                    Object listValue = list.get(i);
                    if (listValue instanceof Map) {
                        flattenYaml(listKey, (Map<String, Object>) listValue, result);
                    } else if (listValue != null) {
                        result.put(listKey, listValue.toString());
                    }
                }
                // store the full list as a string fallback
                result.put(key, value.toString());
            } else if (value != null) {
                result.put(key, value.toString());
            }
        }
    }

    /**
    * Converts a key part or full key to its canonical kebab-case representation.
    * @param key the key to normalize
    * @return the normalized key
    */
    private String toCanonicalKey(String key) {
        if (key == null) return null;

        String[] segments = key.split("\\.");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];

            if (segment.contains("[") && segment.endsWith("]")) {
                int bracketIndex = segment.indexOf("[");
                String baseName = segment.substring(0, bracketIndex);
                String indexPart = segment.substring(bracketIndex);
                sb.append(camelToKebab(baseName)).append(indexPart);
            } else {
                sb.append(camelToKebab(segment));
            }

            if (i < segments.length - 1) sb.append(".");
        }

        return sb.toString();
    }

    /**
     * Analyzes one .java file and collect's every {@code @Value} property or {@code @ConfigurationProperties} found
     *
     * @param file The .java file
     * @param properties A TreeSet of all properties that were found so far
     * @param projectProperties A Map of all project properties found within the application* files
     */
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
                defaultValue = projectProperties.get(toCanonicalKey(propertyKey));
            }

            String propertiesFileValue = projectProperties.get(toCanonicalKey(propertyKey));
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
        
        while (configPropsMatcher.find()) {
            String prefix = configPropsMatcher.group(1);
            int start = configPropsMatcher.end();
            String classBody = extractClassBody(content, start);
            if (classBody != null) {
                extractFieldProperties(filename, classBody, prefix, properties, new HashSet<>(), projectProperties, content);
            }
        }
        return properties;
    }

    /**
     * Extracts the body of a class starting after the class declaration.
     * It looks for the first '{' and then finds the matching '}'.
     */
    private String extractClassBody(String content, int startIndex) {
        int braceStart = content.indexOf('{', startIndex);
        if (braceStart == -1) return null;

        int braceCount = 1;
        int i = braceStart + 1;
        while (i < content.length() && braceCount > 0) {
            char c = content.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;
            i++;
        }

        if (braceCount == 0) {
            return content.substring(braceStart + 1, i - 1);
        }
        return null;
    }


    /**
     * Recursively extracts properties from a class body, following complex field
     * types into their own source files or nested class definitions.
     *
     * @param filename     the file where these fields were found (for location reporting)
     * @param content      the Java source content (class body) to scan for fields
     * @param prefix       the current property key prefix (e.g. "app.database.author")
     * @param properties   the accumulator set
     * @param visited      tracks already-visited type names to prevent infinite recursion
     * @param fullContent  the full content of the original file (to search for nested classes)
     */
    private void extractFieldProperties(
            String filename,
            String content,
            String prefix,
            Set<PropertyInfo> properties,
            Set<String> visited,
            Map<String, String> projectProperties,
            String fullContent
    ) {
        Project project = getProject();
        FileTree javaFiles = getJavaFiles(project);

        // Strip nested class bodies to avoid matching their fields at this level
        String fieldsOnlyContent = stripNestedClasses(content);
        Matcher fieldMatcher = FIELD_PATTERN.matcher(fieldsOnlyContent);
        
        while (fieldMatcher.find()) {
            String rawType  = fieldMatcher.group(1); // e.g. "Map<String,DatabaseConfig>", "Author", "boolean"
            String fieldName = fieldMatcher.group(2);
            String kebabField = camelToKebab(fieldName);
            String fullPath = prefix + "." + kebabField;

            // Map<K, ComplexType> → expand as prefix.[*].subField
            String mapValueType = extractMapValueType(rawType);
            if (mapValueType != null && isComplexType(mapValueType)) {
                // record the map property itself as a node
                properties.add(new PropertyInfo(fullPath, null, PropertySource.CONFIGURATION_PROPERTIES, filename));

                if (!visited.contains(mapValueType)) {
                    processComplexType(mapValueType, filename, fullPath + ".[*]", properties, visited, projectProperties, fullContent, javaFiles);
                }
                continue;
            }

            // Complex user-defined type → recurse into its fields
            String baseType = rawType.replaceAll("<.*>", "").trim();
            if (isComplexType(baseType)) {

                if (isEnumType(baseType, fullContent, javaFiles)) {
                    properties.add(new PropertyInfo(
                            fullPath,
                            projectProperties.get(toCanonicalKey(fullPath)),
                            PropertySource.CONFIGURATION_PROPERTIES,
                            filename
                    ));
                    continue;
                }

                // Record the complex field itself
                properties.add(new PropertyInfo(fullPath, null, PropertySource.CONFIGURATION_PROPERTIES, filename));

                if (!visited.contains(baseType)) {
                    processComplexType(baseType, filename, fullPath, properties, visited, projectProperties, fullContent, javaFiles);
                }
                continue;
            }

            // Simple / Collection / Map<K, SimpleV> → record directly
            properties.add(new PropertyInfo(
                    fullPath,
                    projectProperties.get(toCanonicalKey(fullPath)),
                    PropertySource.CONFIGURATION_PROPERTIES,
                    filename
            ));
        }
    }

    /**
     * Removes nested class bodies from the given content to prevent matching fields
     * inside those nested classes.
     */
    private String stripNestedClasses(String content) {
        StringBuilder sb = new StringBuilder();
        int lastPos = 0;
        // Match "class Name {" or "static class Name {"
        Pattern classPattern = Pattern.compile("\\bclass\\s+\\w+\\b[^\\{]*\\{");
        Matcher m = classPattern.matcher(content);
        while (m.find()) {
            sb.append(content, lastPos, m.start());
            
            // Find matching closing brace for this class
            int braceCount = 1;
            int i = m.end();
            while (i < content.length() && braceCount > 0) {
                char c = content.charAt(i);
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                i++;
            }
            lastPos = i;
        }
        sb.append(content.substring(lastPos));
        return sb.toString();
    }

    /**
     * Resolves and recursively expands a complex type by locating its field definitions,
     * either as a nested class within the current file or as a separate {@code .java} source file.
     *
     * @param typeName         the simple class name to resolve (e.g. {@code "DatabaseConfig"})
     * @param filename         the file name to attribute discovered properties to in the report
     * @param fullPath         the current property key prefix (e.g. {@code "app.database"})
     * @param properties       the accumulator set of discovered properties
     * @param visited          type names already visited in the current recursion chain
     * @param projectProperties resolved default values from configuration files
     * @param fullContent      the full source content of the file currently being analyzed,
     *                         used to search for nested class definitions
     * @param javaFiles        the file tree of all Java source files in the project
     */
    private void processComplexType(
            String typeName,
            String filename,
            String fullPath,
            Set<PropertyInfo> properties,
            Set<String> visited,
            Map<String, String> projectProperties,
            String fullContent,
            FileTree javaFiles
    ) {
        // Try to find as a nested class in the same file
        String nestedClassBody = findNestedClassBody(fullContent, typeName);
        if (nestedClassBody != null) {
            Set<String> nextVisited = new HashSet<>(visited);
            nextVisited.add(typeName);
            extractFieldProperties(filename, nestedClassBody, fullPath, properties, nextVisited, projectProperties, fullContent);
            return;
        }

        // Try to find as a separate .java file
        File typeFile = resolveTypeFile(typeName, javaFiles);
        if (typeFile != null) {
            try {
                String typeContent = Files.readString(typeFile.toPath());
                Set<String> nextVisited = new HashSet<>(visited);
                nextVisited.add(typeName);
                
                // For a separate file, we need to find the main class body
                Pattern mainClassPattern = Pattern.compile("\\bclass\\s+" + typeName + "\\b[^\\{]*\\{");
                Matcher m = mainClassPattern.matcher(typeContent);
                if (m.find()) {
                    String body = extractClassBody(typeContent, m.start());
                    if (body != null) {
                        extractFieldProperties(typeFile.getName(), body, fullPath, properties, nextVisited, projectProperties, typeContent);
                    }
                }
            } catch (IOException e) {
                getLogger().error("Failed to read file for type: {}", typeName, e);
            }
        }
    }

    /**
     * Searches for a nested class definition by name within the given source content and
     * returns its body if found.
     * <p>
     * Matches the pattern {@code class TypeName} with any preceding modifiers (e.g.
     * {@code public static class TypeName}) and extracts the content between the matching
     * opening and closing braces via {@link #extractClassBody}.
     *
     * @param fullContent the full Java source content to search within
     * @param typeName    the simple class name to locate (e.g. {@code "Author"})
     * @return the class body as a string (excluding the outer braces), or {@code null} if
     *         no class with that name was found in the content
     */
    private String findNestedClassBody(String fullContent, String typeName) {
        // Find "class TypeName" with any modifiers before it, and capture everything until the opening brace
        Pattern classPattern = Pattern.compile("\\bclass\\s+" + typeName + "\\b[^\\{]*\\{");
        Matcher m = classPattern.matcher(fullContent);
        if (m.find()) {
            return extractClassBody(fullContent, m.start());
        }
        return null;
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
     * Returns true if the given type name resolves to a Java enum, either as a
     * nested enum in the provided fullContent or as a standalone .java file.
     *
     * @param typeName    simple type name to check (e.g. "Environment")
     * @param fullContent full source of the file currently being analyzed
     * @param javaFiles   file tree to search for standalone .java files
     * @return true if the type is an enum
     */
    private boolean isEnumType(String typeName, String fullContent, FileTree javaFiles) {
        // Check for a nested enum in the same file
        if (fullContent.contains("enum " + typeName)) {
            return true;
        }
        // Check for a standalone enum file
        File typeFile = resolveTypeFile(typeName, javaFiles);
        if (typeFile != null) {
            try {
                String typeContent = Files.readString(typeFile.toPath());
                return typeContent.contains("enum " + typeName);
            } catch (IOException e) {
                getLogger().warn("Could not read file to check enum status: {}", typeFile.getName());
            }
        }
        return false;
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

    /**
     * Prints a formatted summary of all discovered properties to the Gradle lifecycle log.
     * <p>
     * Properties are grouped by their {@link PropertySource} and printed in source declaration
     * order. For each property, the full key and location are always shown; the default value
     * is only included if one was resolved.
     * <p>
     * Only invoked when {@link #getVerboseMode()} is {@code true}.
     *
     * @param properties the full set of discovered properties, pre-sorted by
     *                   {@link PropertyInfo#fullPath()}
     */
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
