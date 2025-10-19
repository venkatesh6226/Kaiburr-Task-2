package com.venkatesh.shellrunner.execution;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Profile("k8s")
public class KubernetesExecutor implements TaskExecutor {

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

            // Clean up the pod
            client.pods().inNamespace("shell-runner").withName(podName).delete();

            return new ExecutionResult(exitCode, logs, "");

        } catch (Exception e) {
            // Clean up pod if it exists
            if (client != null) {
                try {
                    client.pods().inNamespace("shell-runner").withName(podName).delete();
                } catch (Exception cleanupException) {
                    // Ignore cleanup errors
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
