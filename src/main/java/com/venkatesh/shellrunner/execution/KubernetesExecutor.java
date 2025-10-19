package com.venkatesh.shellrunner.execution;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Profile("k8s")
public class KubernetesExecutor implements TaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(KubernetesExecutor.class);

    @Value("${executor.image:busybox:latest}")
    private String executorImage;

    @Value("${executor.timeout:30}")
    private int executorTimeout;

    @Override
    public ExecutionResult runCommand(String command, Duration timeout) throws Exception {
        String podName = "task-exec-" + UUID.randomUUID().toString().substring(0, 8);
        KubernetesClient client = null;

        try {
            // Create Kubernetes client
            client = new KubernetesClientBuilder().build();

            logger.info("Creating pod {} to execute command: {}", podName, command);

            // Create pod specification
            Pod pod = new PodBuilder()
                    .withNewMetadata()
                    .withName(podName)
                    .withNamespace("shell-runner")
                    .addToLabels("app", "task-executor")
                    .addToLabels("task-id", UUID.randomUUID().toString())
                    .endMetadata()
                    .withNewSpec()
                    .withRestartPolicy("Never")
                    .addNewContainer()
                    .withName("executor")
                    .withImage(executorImage)
                    .withCommand("sh", "-c", command)
                    .withImagePullPolicy("IfNotPresent")
                    .withNewResources()
                    .addToLimits("memory", new Quantity("128Mi"))
                    .addToLimits("cpu", new Quantity("100m"))
                    .addToRequests("memory", new Quantity("64Mi"))
                    .addToRequests("cpu", new Quantity("50m"))
                    .endResources()
                    .endContainer()
                    .endSpec()
                    .build();

            // Create the pod
            Pod createdPod = client.pods().inNamespace("shell-runner").create(pod);
            logger.info("Pod {} created successfully", podName);

            // Wait for pod to complete (with timeout)
            Duration actualTimeout = timeout.compareTo(Duration.ofSeconds(executorTimeout)) < 0 ? timeout
                    : Duration.ofSeconds(executorTimeout);

            Pod completedPod = client.pods()
                    .inNamespace("shell-runner")
                    .withName(podName)
                    .waitUntilCondition(p -> {
                        String phase = p.getStatus().getPhase();
                        return "Succeeded".equals(phase) || "Failed".equals(phase);
                    }, actualTimeout.toSeconds(), TimeUnit.SECONDS);

            boolean completed = completedPod != null;

            if (!completed) {
                logger.warn("Pod {} did not complete within timeout", podName);
                // Clean up the pod
                client.pods().inNamespace("shell-runner").withName(podName).delete();
                return new ExecutionResult(124, "", "Command execution timed out");
            }

            // Get pod logs
            String logs = client.pods()
                    .inNamespace("shell-runner")
                    .withName(podName)
                    .getLog();

            // Get pod status to determine exit code
            Pod finalPod = client.pods().inNamespace("shell-runner").withName(podName).get();
            String phase = finalPod.getStatus().getPhase();
            int exitCode = "Succeeded".equals(phase) ? 0 : 1;

            logger.info("Pod {} completed with phase: {}, logs length: {}", podName, phase, logs.length());

            // Clean up the pod
            client.pods().inNamespace("shell-runner").withName(podName).delete();
            logger.info("Pod {} cleaned up", podName);

            return new ExecutionResult(exitCode, logs, "");

        } catch (Exception e) {
            logger.error("Error executing command in Kubernetes pod {}: {}", podName, e.getMessage(), e);

            // Clean up pod if it exists
            if (client != null) {
                try {
                    client.pods().inNamespace("shell-runner").withName(podName).delete();
                } catch (Exception cleanupException) {
                    logger.warn("Failed to clean up pod {}: {}", podName, cleanupException.getMessage());
                }
            }

            return new ExecutionResult(1, "", "Kubernetes execution failed: " + e.getMessage());
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }
}
