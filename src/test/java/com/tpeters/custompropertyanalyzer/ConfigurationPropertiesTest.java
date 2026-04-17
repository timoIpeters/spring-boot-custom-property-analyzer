package com.tpeters.custompropertyanalyzer;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        String content = getReportContent();
        assertTrue(content.contains("\"key\": \"app.name\""));
        assertTrue(content.contains("\"key\": \"app.timeout\""));
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
        String content = getReportContent();
        assertTrue(content.contains("\"key\": \"service.config.maximum-retries\""));
        assertTrue(content.contains("\"key\": \"service.config.ssl-enabled\""));
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
        String content = getReportContent();
        assertTrue(content.contains("\"key\": \"server.host\""));
        assertTrue(content.contains("\"key\": \"server.security.username\""));
        assertTrue(content.contains("\"key\": \"server.security.password\""));
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
        String content = getReportContent();
        assertTrue(content.contains("\"key\": \"app.map.simple-map\""));
        assertTrue(content.contains("\"key\": \"app.map.complex-map.[*].info\""));
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
        String content = getReportContent();
        assertTrue(content.contains("\"key\": \"app.list.names\""));
    }
}
