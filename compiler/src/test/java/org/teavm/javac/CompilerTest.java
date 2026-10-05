/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.javac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Each subdirectory of the cases directory is a test case. It contains Java sources (with {@code Main} as the
 * main class) and {@code expected.txt} with the expected stdout. Test case is skipped if it contains
 * {@code disabled.txt}, whose content explains why.
 */
class CompilerTest {
    private static final String MAIN_CLASS = "Main";
    private static CompilerTestHarness harness;

    @BeforeAll
    static void startHarness() {
        var harnessDir = new File(System.getProperty("teavm.javac.test.harnessDir", "build/test-harness"));
        var browser = System.getProperty("teavm.javac.test.browser", "browser-chrome");
        harness = new CompilerTestHarness(harnessDir, browser);
    }

    @AfterAll
    static void stopHarness() {
        if (harness != null) {
            harness.close();
            harness = null;
        }
    }

    @TestFactory
    Stream<DynamicTest> cases() throws IOException {
        var casesDir = Path.of(System.getProperty("teavm.javac.test.casesDir", "src/test/cases"));
        try (var dirs = Files.list(casesDir)) {
            return dirs.filter(Files::isDirectory)
                    .sorted()
                    .map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), dir.toUri(),
                            () -> runCase(dir)))
                    .toList()
                    .stream();
        }
    }

    private void runCase(Path dir) throws IOException {
        var disabled = dir.resolve("disabled.txt");
        if (Files.exists(disabled)) {
            Assumptions.abort(Files.readString(disabled).trim());
        }

        var result = harness.run(collectSources(dir), MAIN_CLASS);
        assertEquals(CompilerTestResult.Status.OK, result.status(), result::describe);
        var expected = Files.readString(dir.resolve("expected.txt"));
        assertEquals(expected, result.stdout(), result::describe);
    }

    private static Map<String, String> collectSources(Path dir) throws IOException {
        var sources = new LinkedHashMap<String, String>();
        try (var files = Files.walk(dir)) {
            for (var file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                var name = dir.relativize(file).toString().replace(File.separatorChar, '/');
                sources.put(name, Files.readString(file));
            }
        }
        return sources;
    }
}
