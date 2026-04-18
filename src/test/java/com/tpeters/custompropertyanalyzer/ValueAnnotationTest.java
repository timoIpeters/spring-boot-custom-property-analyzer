package com.tpeters.custompropertyanalyzer;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static com.tpeters.custompropertyanalyzer.TestUtils.*;

class ValueAnnotationTest extends AbstractPluginTest {

    @Test
    void testSimpleValue() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "SimpleService", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class SimpleService {
              @Value("${app.simple}")
              private String simple;
            }
            """);

        runAnalyze();
        assertHasProperty("app.simple", null, "VALUE_ANNOTATION");
    }

    @Test
    void testValueWithDefault() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "DefaultService", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class DefaultService {
              @Value("${app.default:myDefaultValue}")
              private String defaultProp;
            }
            """);

        runAnalyze();
        assertHasProperty("app.default", "myDefaultValue", "VALUE_ANNOTATION");
    }

    @Test
    void testValueInSingleQuotes() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "SingleQuoteService", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class SingleQuoteService {
              @Value('${app.single.quote:val}')
              private String prop;
            }
            """);

        runAnalyze();
        assertHasProperty("app.single.quote", "val", "VALUE_ANNOTATION");
    }

    @Test
    void testValueWithPropertiesFileOverride() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "OverrideService", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class OverrideService {
              @Value("${app.override:hardcoded}")
              private String prop;
            }
            """);

        writeResourcesFile(testProjectDir, "application.properties", """
            app.override=fromPropertiesFile
            """);

        runAnalyze();
        assertHasProperty("app.override", "fromPropertiesFile", "VALUE_ANNOTATION");
    }

    @Test
    void testMultipleValueAnnotations() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "MultiService", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class MultiService {
              @Value("${app.first}") private String f;
              @Value("${app.second:2}") private int s;
            }
            """);

        runAnalyze();
        assertHasProperty("app.first", null, "VALUE_ANNOTATION");
        assertHasProperty("app.second", "2", "VALUE_ANNOTATION");
    }

    @Test
    void testEnumValueAnnotation() throws IOException {
        writeJavaSource(testProjectDir, "com.example", "SomeService", """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class SomeService {
              @Value("${props.env1}") private DeploymentEnv env1;
              @Value("${props.env2:LOCAL_DEV}") private DeploymentEnv env2;
            }
            """);

        writeJavaSource(testProjectDir, "com.example", "DeploymentEnv", """
            package com.example;
            public enum DeploymentEnv {
              STAGING,
              PRODUCTION,
              LOCAL_DEV;
            }
            """);

        runAnalyze();
        assertHasProperty("props.env1", null, "VALUE_ANNOTATION");
        assertHasProperty("props.env2", "LOCAL_DEV", "VALUE_ANNOTATION");
    }
}
