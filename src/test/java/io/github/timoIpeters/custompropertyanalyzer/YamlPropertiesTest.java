package io.github.timoIpeters.custompropertyanalyzer;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static io.github.timoIpeters.custompropertyanalyzer.TestUtils.*;

class YamlPropertiesTest extends AbstractPluginTest {

    @Test
    void testYmlBaseline() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "YmlService", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class YmlService {
              @Value("${app.yml.name}")
              private String name;
            }
            """);

        writeResourcesFile(testProjectDir, "application.yml", """
            app:
              yml:
                name: ymlValue
            """);

        runAnalyze();
        assertHasProperty("app.yml.name", "ymlValue", "VALUE_ANNOTATION");
    }

    @Test
    void testYamlBaseline() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "YamlService", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class YamlService {
              @Value("${app.yaml.name}")
              private String name;
            }
            """);

        writeResourcesFile(testProjectDir, "application.yaml", """
            app:
              yaml:
                name: yamlValue
            """);

        runAnalyze();
        assertHasProperty("app.yaml.name", "yamlValue", "VALUE_ANNOTATION");
    }

    @Test
    void testAdditionalYamlPattern() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "ProfileService", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class ProfileService {
              @Value("${app.profile}")
              private String profile;
            }
            """);

        writeResourcesFile(testProjectDir, "application-dev.yaml", """
            app:
              profile: dev
            """);

        // Run with the pattern
        org.gradle.testkit.runner.GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("analyzeCustomProperties", "--additionalPropertiesPattern=application-*.yaml")
            .withPluginClasspath()
            .build();

        assertHasProperty("app.profile", "dev", "VALUE_ANNOTATION");
    }

    @Test
    void testRelaxedBindingAndListIndexing() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "ComplexService", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class ComplexService {
              @Value("${app.max-connections}")
              private int max;
              
              @Value("${app.server-list[0]}")
              private String firstServer;
            }
            """);

        writeResourcesFile(testProjectDir, "application.yml", """
            app:
              maxConnections: 50
              serverList:
                - srv1
                - srv2
            """);

        runAnalyze();
        
        // app.maxConnections (camelCase in YAML) should match app.max-connections (canonical lookup)
        assertHasProperty("app.max-connections", "50", "VALUE_ANNOTATION");
        
        // app.serverList[0] should match app.server-list[0]
        assertHasProperty("app.server-list[0]", "srv1", "VALUE_ANNOTATION");
    }

    @Test
    void testListOfComplexObjectsInYaml() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "ServerService", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class ServerService {
              @Value("${app.servers[0].host}")
              private String host;
            }
            """);

        writeResourcesFile(testProjectDir, "application.yml", """
            app:
              servers:
                - host: localhost
                  port: 8080
            """);

        runAnalyze();
        assertHasProperty("app.servers[0].host", "localhost", "VALUE_ANNOTATION");
    }
}
