package io.github.timoIpeters.custompropertyanalyzer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.options.Option;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Checks for properties that are defined in configuration files but never referenced
 * in the project's Java source code.
 * <p>
 * This task reads the report produced by {@code analyzeCustomProperties} to obtain the
 * set of all properties referenced in code, then compares it against the keys found in
 * {@code application.properties}, {@code application.yml}, or {@code application.yaml}
 * (+ additional files based on a glob pattern).
 * Any key present in a configuration file but absent from the code is flagged as unused.
 * <p>
 * By default, the build <b>fails</b> if unused properties are found, making this task
 * suitable as a CI gate. Pass {@code --ignoreUnused} to report without failing.
 * <p>
 * This task depends on {@code analyzeCustomProperties} and runs it automatically.
 * <p>
 * Usage:
 * <pre>
 *   ./gradlew checkUnusedProperties
 *   ./gradlew checkUnusedProperties --verbose
 *   ./gradlew checkUnusedProperties --outputFile=my-unused-report.json
 *   ./gradlew checkUnusedProperties --ignoreUnused
 *   ./gradlew checkUnusedProperties --additionalPropertiesPattern="application-*.yml"
 * </pre>
 *
 * The report is written to {@code build/reports/unused-properties-report.json} by default.
 */
public class CheckUnusedPropertiesTask extends DefaultTask {

    private String analysisReportFile = "custom-properties-analysis.json";
    private String outputFile = "unused-properties-report.json";
    private String additionalPropertiesPattern = null;
    private boolean ignoreUnused = false;
    private boolean verboseMode = false;

    /**
     * The name of the analysis report file produced by {@code analyzeCustomProperties} to
     * read referenced property keys from.
     * Defaults to {@code custom-properties-analysis.json}.
     * @return The analysis report file name
     */
    @Input
    @org.gradle.api.tasks.Optional
    public String getAnalysisReportFile() {
        return analysisReportFile;
    }

    /**
     * The name of the generated JSON report file listing unused properties.
     * <p>
     * The file is written to {@code build/reports/<outputFile>}.
     * Defaults to {@code unused-properties-report.json}.
     * @return The unused properties output file name
     */
    @Input
    @org.gradle.api.tasks.Optional
    public String getOutputFile() {
        return outputFile;
    }

    /**
     * Glob pattern for additional property files to include when scanning for unused keys,
     * beyond the baseline {@code application.properties}, {@code application.yml}, and
     * {@code application.yaml}.
     * <p>
     * Use this to include profile-specific files such as {@code application-prod.yml} in the
     * unused property check. This option is independent of the same option on
     * {@code analyzeCustomProperties} — set it based on which files you want to audit for
     * orphaned keys.
     * @return The additional properties glob pattern
     */
    @Input
    @org.gradle.api.tasks.Optional
    public String getAdditionalPropertiesPattern() {
        return additionalPropertiesPattern;
    }

    /**
     * Whether to suppress build failure when unused properties are found.
     * <p>
     * When {@code false} (the default), the build fails if any unused properties are
     * detected. When {@code true}, unused properties are reported in the JSON output
     * and console but the build continues.
     * @return True if ignore unused flag is set, otherwise false
     */
    @Input
    public boolean getIgnoreUnused() {
        return ignoreUnused;
    }

    /**
     * Whether to print the full unused properties results to the console in addition
     * to the JSON report. Defaults to {@code false}.
     * @return True if verbose mode is enabled, otherwise false
     */
    @Input
    public boolean getVerboseMode() {
        return verboseMode;
    }

    /**
     * Option to set a custom analysis report file name
     *
     * @param analysisReportFile Name of the analysis report produced by analyzeCustomProperties. Default: custom-properties-analysis.json
     */
    @Option(option = "analysisReportFile", description = "Name of the analysis report produced by analyzeCustomProperties. Default: custom-properties-analysis.json")
    public void setAnalysisReportFile(String analysisReportFile) {
        this.analysisReportFile = analysisReportFile;
    }

    /**
     * Option to set a custom output file name
     *
     * @param outputFile Name of the generated unused-properties report file. Default: unused-properties-report.json
     */
    @Option(option = "outputFile", description = "Name of the generated unused-properties report file. Default: unused-properties-report.json")
    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * Option to set an additional properties glob pattern
     *
     * @param additionalPropertiesPattern Glob pattern for additional property files to include (e.g. 'application-*.yml'). Should match what was passed to analyzeCustomProperties.
     */
    @Option(option = "additionalPropertiesPattern", description = "Glob pattern for additional property files to include (e.g. 'application-*.yml'). Should match what was passed to analyzeCustomProperties.")
    public void setAdditionalPropertiesPattern(String additionalPropertiesPattern) {
        this.additionalPropertiesPattern = additionalPropertiesPattern;
    }

    /**
     * Option to enable/disable build failure when unused properties are detected
     *
     * @param ignoreUnused If set, unused properties are reported but the build does not fail.
     */
    @Option(option = "ignoreUnused", description = "If set, unused properties are reported but the build does not fail.")
    public void setIgnoreUnused(boolean ignoreUnused) {
        this.ignoreUnused = ignoreUnused;
    }

    /**
     * Option to enable/disable verbose console outputs
     *
     * @param verboseMode Enable verbose console output.
     */
    @Option(option = "verbose", description = "Enable verbose console output.")
    public void setVerboseMode(boolean verboseMode) {
        this.verboseMode = verboseMode;
    }

    /**
     * The analysis report file produced by {@code analyzeCustomProperties}, used as input
     * to determine which property keys are referenced in code.
     * Resolved to {@code build/reports/<analysisReportFile>}.
     * @return The analysis report file provider
     */
    @InputFile
    public Provider<RegularFile> getAnalysisReportFileProvider() {
        return getProject().getLayout().getBuildDirectory().file("reports/" + analysisReportFile);
    }

    /**
     * The output file to which the unused properties JSON report is written.
     * Resolved to {@code build/reports/<outputFile>}.
     * @return The report file provider
     */
    @OutputFile
    public Provider<RegularFile> getReportFile() {
        return getProject().getLayout().getBuildDirectory().file("reports/" + outputFile);
    }

    /**
     * Task action that compares defined configuration keys against code-referenced keys
     * and flags any that are unused.
     */
    @TaskAction
    public void check() {
        Set<String> referencedKeys = loadReferencedKeys();
        Map<String, String> definedProperties = loadDefinedProperties();

        // Normalize referenced keys to canonical kebab-case for comparison
        Set<String> normalizedReferencedKeys = new HashSet<>();
        for (String key : referencedKeys) {
            normalizedReferencedKeys.add(toCanonicalKey(key));
        }

        List<UnusedProperty> unusedProperties = new ArrayList<>();
        for (Map.Entry<String, String> entry : definedProperties.entrySet()) {
            String definedKey = entry.getKey(); // already canonical after loadDefinedProperties
            if (!normalizedReferencedKeys.contains(definedKey)) {
                unusedProperties.add(new UnusedProperty(definedKey, entry.getValue()));
            }
        }

        unusedProperties.sort(Comparator.comparing(UnusedProperty::key));

        exportToJson(unusedProperties);

        if (verboseMode || !unusedProperties.isEmpty()) {
            printResults(unusedProperties);
        }

        if (!unusedProperties.isEmpty() && !ignoreUnused) {
            throw new GradleException(
                    unusedProperties.size() + " unused property/properties found in configuration files. " +
                            "Run with --ignoreUnused to suppress build failure, or check the report at: " +
                            getReportFile().get().getAsFile().getAbsolutePath()
            );
        }
    }

    /**
     * Reads the JSON report produced by analyzeCustomProperties and returns the set
     * of all property keys that are referenced in the Java source code.
     */
    private Set<String> loadReferencedKeys() {
        File reportFile = getAnalysisReportFileProvider().get().getAsFile();
        if (!reportFile.exists()) {
            throw new GradleException(
                    "Analysis report not found at: " + reportFile.getAbsolutePath() + ". " +
                            "Make sure analyzeCustomProperties has been run first."
            );
        }

        Set<String> keys = new HashSet<>();
        try (Reader reader = new FileReader(reportFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray properties = root.getAsJsonArray("properties");
            for (var element : properties) {
                JsonObject prop = element.getAsJsonObject();
                keys.add(prop.get("key").getAsString());
            }
        } catch (IOException e) {
            throw new GradleException("Failed to read analysis report: " + reportFile.getAbsolutePath(), e);
        }
        return keys;
    }

    /**
     * Loads all property keys defined in application.properties/yml/yaml (and any
     * additional files matching additionalPropertiesPattern) across all subprojects.
     * Keys are returned in canonical kebab-case.
     */
    private Map<String, String> loadDefinedProperties() {
        Map<String, String> result = new LinkedHashMap<>();

        Set<Project> projectsToAnalyze = getProject().getSubprojects().isEmpty()
                ? Collections.singleton(getProject())
                : getProject().getAllprojects();

        String[] baselines = {"application.properties", "application.yml", "application.yaml"};

        for (Project p : projectsToAnalyze) {
            File resourcesDir = new File(p.getProjectDir(), "src/main/resources");
            if (!resourcesDir.exists()) continue;

            File[] allPropFiles = resourcesDir.listFiles(
                    f -> f.isFile() && (f.getName().endsWith(".properties")
                            || f.getName().endsWith(".yml")
                            || f.getName().endsWith(".yaml"))
            );
            if (allPropFiles == null) continue;

            List<File> filesToLoad = new ArrayList<>();
            for (String baseline : baselines) {
                for (File f : allPropFiles) {
                    if (f.getName().equals(baseline)) {
                        filesToLoad.add(f);
                        break;
                    }
                }
            }

            if (additionalPropertiesPattern != null && !additionalPropertiesPattern.isBlank()) {
                PathMatcher matcher = FileSystems.getDefault()
                        .getPathMatcher("glob:" + additionalPropertiesPattern);
                for (File f : allPropFiles) {
                    boolean isBaseline = Arrays.stream(baselines).anyMatch(b -> f.getName().equals(b));
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
     *   tags:
     *     - admin
     *     - ops
     * </pre>
     * is flattened into:
     * <pre>
     * app.server.port  → "8080"
     * app.tags[0]      → "admin"
     * app.tags[1]      → "ops"
     * app.tags         → "[admin, ops]"   (list fallback entry)
     * </pre>
     *
     * @param prefix  the current key prefix being built, empty string at the root level
     * @param yamlMap the current nested map being processed
     * @param result  the accumulator map where flattened key-value pairs are stored
     */
    @SuppressWarnings("unchecked")
    private void flattenYaml(String prefix, Map<String, Object> yamlMap, Map<String, String> result) {
        for (Map.Entry<String, Object> entry : yamlMap.entrySet()) {
            String key = prefix.isEmpty() ? toCanonicalKey(entry.getKey()) : prefix + "." + toCanonicalKey(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map) {
                flattenYaml(key, (Map<String, Object>) value, result);
            } else if (value instanceof List) {
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
                result.put(key, value.toString());
            } else if (value != null) {
                result.put(key, value.toString());
            }
        }
    }

    /**
     * Writes the list of unused properties to the JSON report file at {@code build/reports/<outputFile>}.
     * <p>
     * The parent directory is created if it does not already exist. The report is
     * written even when no unused properties are found, in which case
     * {@code totalUnusedProperties} is {@code 0} and {@code unusedProperties} is empty.
     *
     * @param unusedProperties the sorted list of unused properties to include in the report
     */
    private void exportToJson(List<UnusedProperty> unusedProperties) {
        File jsonFile = getReportFile().get().getAsFile();
        getProject().mkdir(jsonFile.getParentFile());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonObject root = new JsonObject();
        root.addProperty("projectName", getProject().getName());
        root.addProperty("analysisDate", new Date().toString());
        root.addProperty("totalUnusedProperties", unusedProperties.size());

        JsonArray propsArray = new JsonArray();
        for (UnusedProperty prop : unusedProperties) {
            JsonObject propObj = new JsonObject();
            propObj.addProperty("key", prop.key());
            propObj.addProperty("value", prop.value());
            propsArray.add(propObj);
        }
        root.add("unusedProperties", propsArray);

        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(root, writer);
            getLogger().lifecycle("✓ Unused properties report saved to: " + jsonFile.getAbsolutePath());
        } catch (IOException e) {
            getLogger().error("Failed to export unused properties report", e);
        }
    }

    /**
     * Prints a formatted summary of all unused properties to the Gradle lifecycle log.
     * <p>
     * Always invoked when unused properties exist. Also invoked when
     * {@link #getVerboseMode()} is {@code true}, even if no unused properties were found.
     *
     * @param unusedProperties the sorted list of unused properties to print
     */
    private void printResults(List<UnusedProperty> unusedProperties) {
        getLogger().lifecycle("\n========================================");
        getLogger().lifecycle("Unused Properties Check Results");
        getLogger().lifecycle("========================================\n");

        if (unusedProperties.isEmpty()) {
            getLogger().lifecycle("✓ No unused properties found.");
            return;
        }

        getLogger().lifecycle("The following properties are defined in configuration files but never referenced in code:\n");
        getLogger().lifecycle("─".repeat(50));
        for (UnusedProperty prop : unusedProperties) {
            getLogger().lifecycle("  • " + prop.key());
            if (prop.value() != null && !prop.value().isBlank()) {
                getLogger().lifecycle("    Value: " + prop.value());
            }
        }
        getLogger().lifecycle("\nTotal unused properties: " + unusedProperties.size());
    }

    /**
     * Converts a full property key to its canonical kebab-case representation,
     * normalizing each dot-separated segment individually.
     *
     * @param key the property key to normalize, may be a single segment or a full dot-notation key
     * @return the normalized key in kebab-case, or {@code null} if the input is {@code null}
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
     * Converts a camelCase string to its kebab-case equivalent.
     * <p>
     * Inserts a hyphen between each lowercase-to-uppercase character transition
     * and converts the entire string to lowercase.
     *
     * @param camelCase the camelCase string to convert
     * @return the kebab-case equivalent
     */
    private String camelToKebab(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }
}
