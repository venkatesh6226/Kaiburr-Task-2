package com.venkatesh.shellrunner.execution;

import java.time.Duration;

public interface TaskExecutor {

    class ExecutionResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ExecutionResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    ExecutionResult runCommand(String command, Duration timeout) throws Exception;
}
