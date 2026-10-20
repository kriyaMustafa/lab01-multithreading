package edu.itc.se.lab01.challenge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class WordCountTest {
    @TempDir
    Path tempDir;

    @Test
    void parallelCountsMatchReferenceFor20Files() throws Exception {
        Path fixture = Files.createDirectory(tempDir.resolve("fixture"));

        for (int i = 0; i < 20; i++) {
            Files.writeString(fixture.resolve("file" + i + ".txt"),
                    "Java thread hello java\n" + "Hello world thread " + i + "\n");
        }

        Map<String, Long> expected = referenceCount(fixture);

        WordCount app = new WordCount(fixture, 4, false, 10, 0);
        app.run();
        assertEquals(expected, app.getCounts());
    }

    // Single-threaded reference implementation: one thread, no concurrent map
    private Map<String, Long> referenceCount(Path dir) throws IOException {
        Map<String, Long> result = new HashMap<>();

        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                    .toList()) {
                for (String line : Files.readAllLines(file)) {
                    for (String word : line.split("[^\\p{L}]+")) {
                        if (!word.isEmpty()) {
                            String normalized = word.toLowerCase(Locale.ROOT);
                            result.merge(normalized, 1L, Long::sum);
                        }
                    }
                }
            }
        }

        return result;
    }

    @Test
    void timeoutStopsWordCountWithinThreeSeconds() {
        Path fixture = tempDir.resolve("large-fixture");

        assertDoesNotThrow(() -> Files.createDirectory(fixture));

        for (int i = 0; i < 5000; i++) {
            try {
                Files.writeString(fixture.resolve("file" + i + ".txt"), "java thread timeout test\n");
            } catch (IOException e) {
                fail("Could not create fixture file " + i, e);
            }
        }

        assertTimeout(Duration.ofSeconds(3), () -> {
            WordCount app = new WordCount(fixture, 4, false, 10, 1);
            app.run();
        });
    }
}
