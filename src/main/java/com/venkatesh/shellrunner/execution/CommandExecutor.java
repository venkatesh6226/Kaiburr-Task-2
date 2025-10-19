package com.venkatesh.shellrunner.execution;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
@Profile("local")
public class CommandExecutor implements TaskExecutor {

    public ExecutionResult runCommand(String command, Duration timeout) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "-lc", command);
        Process process = pb.start();

        OutputCollector stdout = new OutputCollector(process.getInputStream());
        OutputCollector stderr = new OutputCollector(process.getErrorStream());
        stdout.start();
        stderr.start();

        boolean finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Command execution timed out after " + timeout.getSeconds() + " seconds");
        }

        int code = process.exitValue();
        stdout.join();
        stderr.join();
        return new ExecutionResult(code, stdout.getOutput(), stderr.getOutput());
    }

    private static class OutputCollector extends Thread {
        private final java.io.InputStream stream;
        private final StringBuilder output = new StringBuilder();

        OutputCollector(java.io.InputStream stream) {
            this.stream = stream;
        }

        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (Exception ignored) {
            }
        }

        public String getOutput() {
            return output.toString().trim();
        }
    }
}
