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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.teavm.browserrunner.BrowserRunDescriptor;
import org.teavm.browserrunner.BrowserRunner;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs the compiler in a browser: sends Java sources to {@code harness.js}, which compiles them to WebAssembly
 * with the compiler built from this project, runs the result and reports back output and diagnostics.
 */
public class CompilerTestHarness implements AutoCloseable {
    private static final String RESULT_MARKER = "TEAVM_JAVAC_TEST_RESULT ";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrintStream originalOut;
    private final OutputCapture capture;
    private final BrowserRunner runner;
    private int runIndex;

    public CompilerTestHarness(File harnessDir, String browser) {
        // BrowserRunner from TeaVM 0.16.0 prints browser output to System.out and does not offer any other way
        // to obtain it, so we intercept System.out to pick up the line with the result.
        originalOut = System.out;
        capture = new OutputCapture(originalOut);
        System.setOut(new PrintStream(capture, true, StandardCharsets.UTF_8));
        runner = new BrowserRunner(harnessDir, "JAVASCRIPT", BrowserRunner.pickBrowser(browser), false);
        try {
            runner.start();
        } catch (RuntimeException e) {
            System.setOut(originalOut);
            throw e;
        }
    }

    public CompilerTestResult run(Map<String, String> sources, String mainClass) throws IOException {
        var request = objectMapper.writeValueAsString(Map.of("sources", sources, "mainClass", mainClass));
        var descriptor = new BrowserRunDescriptor("compile-" + ++runIndex, "tests/harness.js", true, List.of(),
                request, true);
        List<String> results;
        capture.startRecording();
        try {
            runner.runTest(descriptor);
        } finally {
            results = capture.stopRecording();
        }
        if (results.size() != 1) {
            throw new IllegalStateException("Expected exactly one result from harness, got " + results.size());
        }
        return objectMapper.readValue(results.get(0).substring(RESULT_MARKER.length()), CompilerTestResult.class);
    }

    @Override
    public void close() {
        try {
            runner.stop();
        } finally {
            System.setOut(originalOut);
        }
    }

    private static class OutputCapture extends OutputStream {
        private final PrintStream target;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();
        private List<String> recorded;

        OutputCapture(PrintStream target) {
            this.target = target;
        }

        synchronized void startRecording() {
            recorded = new ArrayList<>();
        }

        synchronized List<String> stopRecording() {
            var result = recorded;
            recorded = null;
            return result;
        }

        @Override
        public synchronized void write(int b) {
            if (b == '\n') {
                var text = line.toString(StandardCharsets.UTF_8);
                line.reset();
                if (recorded != null && text.startsWith(RESULT_MARKER)) {
                    recorded.add(text);
                } else {
                    target.println(text);
                }
            } else {
                line.write(b);
            }
        }

        @Override
        public synchronized void flush() {
            target.flush();
        }
    }
}
