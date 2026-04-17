package com.tpeters.custompropertyanalyzer;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static com.tpeters.custompropertyanalyzer.TestUtils.*;

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
}
