package com.tpeters.custompropertyanalyzer;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static com.tpeters.custompropertyanalyzer.TestUtils.*;

class ConfigurationPropertiesTest extends AbstractPluginTest {

    @Test
    void testSimpleConfigurationProperties() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "AppProperties", """
            package com.example;
            import org.springframework.boot.context.properties.ConfigurationProperties;
            @ConfigurationProperties(prefix = "app")
            public class AppProperties {
              private String name;
              private int timeout;
            }
            """);

        runAnalyze();
        assertHasProperty("app.name", null, "CONFIGURATION_PROPERTIES");
        assertHasProperty("app.timeout", null, "CONFIGURATION_PROPERTIES");
    }

    @Test
    void testCamelCaseToKebabCase() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "KebabProperties", """
            package com.example;
            import org.springframework.boot.context.properties.ConfigurationProperties;
            @ConfigurationProperties("service.config")
            public class KebabProperties {
              private String maximumRetries;
              private boolean sslEnabled;
            }
            """);

        runAnalyze();
        assertHasProperty("service.config.maximum-retries", null, "CONFIGURATION_PROPERTIES");
        assertHasProperty("service.config.ssl-enabled", null, "CONFIGURATION_PROPERTIES");
    }

    @Test
    void testNestedConfigurationProperties() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "ServerProperties", """
            package com.example;
            import org.springframework.boot.context.properties.ConfigurationProperties;
            @ConfigurationProperties("server")
            public class ServerProperties {
              private String host;
              private Security security;
            }
            """);

        writeJavaSource(testProjectDir, "com.example", "Security", """
            package com.example;
            public class Security {
              private String username;
              private String password;
            }
            """);

        runAnalyze();
        assertHasProperty("server.host", null, "CONFIGURATION_PROPERTIES");
        assertHasProperty("server.security.username", null, "CONFIGURATION_PROPERTIES");
        assertHasProperty("server.security.password", null, "CONFIGURATION_PROPERTIES");
    }

    @Test
    void testMapConfigurationProperties() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "MapProperties", """
            package com.example;
            import org.springframework.boot.context.properties.ConfigurationProperties;
            import java.util.Map;
            @ConfigurationProperties("app.map")
            public class MapProperties {
              private Map<String, String> simpleMap;
              private Map<String, Details> complexMap;
            }
            """);

        writeJavaSource(testProjectDir, "com.example", "Details", """
            package com.example;
            public class Details {
              private String info;
            }
            """);

        runAnalyze();
        assertHasProperty("app.map.simple-map", null, "CONFIGURATION_PROPERTIES");
        assertHasProperty("app.map.complex-map.[*].info", null, "CONFIGURATION_PROPERTIES");
    }

    @Test
    void testListConfigurationProperties() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "ListProperties", """
            package com.example;
            import org.springframework.boot.context.properties.ConfigurationProperties;
            import java.util.List;
            @ConfigurationProperties("app.list")
            public class ListProperties {
              private List<String> names;
            }
            """);

        runAnalyze();
        assertHasProperty("app.list.names", null, "CONFIGURATION_PROPERTIES");
    }

    @Test
    void testCircularDependency() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "ClassA", """
            package com.example;
            import org.springframework.boot.context.properties.ConfigurationProperties;
            @ConfigurationProperties("circular")
            public class ClassA {
              private ClassB b;
            }
            """);

        writeJavaSource(testProjectDir, "com.example", "ClassB", """
            package com.example;
            public class ClassB {
              private ClassA a;
            }
            """);

        // If recursion isn't handled, this would throw StackOverflowError
        runAnalyze();
        assertHasProperty("circular.b", null, "CONFIGURATION_PROPERTIES");
    }

    @Test
    void testStaticNestedClass() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "OuterProperties", """
            package com.example;
            import org.springframework.boot.context.properties.ConfigurationProperties;
            @ConfigurationProperties("outer")
            public class OuterProperties {
              private Nested nested;
              public static class Nested {
                private String inner;
              }
            }
            """);

        runAnalyze();
        // Currently expected to fail resolving 'inner' because it's a nested class,
        // but we record the 'nested' field itself.
        assertHasProperty("outer.nested", null, "CONFIGURATION_PROPERTIES");
    }
}
