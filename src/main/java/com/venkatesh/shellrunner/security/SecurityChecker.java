package com.venkatesh.shellrunner.security;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SecurityChecker {

    private static final List<String> BLOCKED_PATTERNS = List.of(
            "rm ", " rm", "sudo ", "shutdown", "reboot", "halt",
            ":(){:|:&};:",
            "mkfs", "dd ", ">|", ">>", "<(",
            "curl ", "wget ", "nc ", "netcat", "telnet",
            "scp ", "ssh ", "ftp ",
            "kill ", "pkill ", "killall",
            "chmod ", "chown ", "useradd ", "userdel ", "passwd ",
            "docker ", "kubectl ", "helm ");

    public void validateCommand(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("Command cannot be empty or null");
        }
        String lowerCase = command.toLowerCase();
        for (String pattern : BLOCKED_PATTERNS) {
            if (lowerCase.contains(pattern)) {
                throw new IllegalArgumentException(
                        "Security violation: Command is not safe '" + pattern.trim() + "'");
            }
        }
        if (command.length() > 250) {
            throw new IllegalArgumentException("Command exceeds maximum length of 250 characters");
        }
    }
}
