package io.github.timoIpeters.custompropertyanalyzer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestUtils {

    /**
     * Writes string content to a specified File, creating parent directories if they do not exist.
     *
     * @param destination The File object representing the target path.
     * @param content     The string content to write.
     * @throws IOException  If an I/O error occurs while writing the file.
     */
    public static void writeFile(File destination, String content) throws IOException {
        Files.createDirectories(destination.getParentFile().toPath());
        try (FileWriter writer = new FileWriter(destination)) {
            writer.write(content);
        }
    }

    /**
     * Writes string content to a specified Path, creating parent directories if they do not exist.
     *
     * @param destination The Path representing the target location.
     * @param content     The string content to write.
     * @throws IOException If an I/O error occurs while writing the file.
     */
    public static void writeFile(Path destination, String content) throws IOException {
        Files.createDirectories(destination.getParent());
        try (FileWriter writer = new FileWriter(destination.toFile())) {
            writer.write(content);
        }
    }

    /**
     * Creates a Java source file within the standard Maven/Gradle project structure.
     *
     * @param projectDir  The root directory of the project.
     * @param packageName The Java package name (e.g., "com.example").
     * @param className   The name of the class (without the .java extension).
     * @param content     The source code content.
     * @throws IOException If an I/O error occurs while writing the file.
     */
    public static void writeJavaSource(Path projectDir, String packageName, String className, String content) throws IOException {
        Path path = projectDir.resolve("src/main/java/" + packageName.replace('.', '/') + "/" + className + ".java");
        writeFile(path, content);
    }

    /**
     * Creates a file within the standard Maven/Gradle resources directory.
     *
     * @param projectDir The root directory of the project.
     * @param fileName   The name of the file to create in src/main/resources.
     * @param content    The content to write to the file.
     * @throws IOException If an I/O error occurs while writing the file.
     */
    public static void writeResourcesFile(Path projectDir, String fileName, String content) throws IOException {
        Path path = projectDir.resolve("src/main/resources/" + fileName);
        writeFile(path, content);
    }
}
