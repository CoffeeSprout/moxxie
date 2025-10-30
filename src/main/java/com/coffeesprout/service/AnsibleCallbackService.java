package com.coffeesprout.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.api.exception.ProxmoxException;
import com.coffeesprout.config.AnsibleConfig;
import org.jboss.logging.Logger;

/**
 * Service for triggering Ansible playbooks after VM creation or configuration changes.
 * Supports multiple callback methods:
 * - Webhook to Ansible Tower/AWX
 * - Generic webhook to external automation controller
 * - Direct ansible-playbook execution (if configured)
 */
@ApplicationScoped
public class AnsibleCallbackService {

    private static final Logger LOG = Logger.getLogger(AnsibleCallbackService.class);

    @Inject
    AnsibleConfig ansibleConfig;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    /**
     * Trigger Ansible callback for a newly created VM.
     * Executes asynchronously and logs results.
     *
     * @param vm The VM that was created
     * @param playbook Optional playbook name to execute (uses default if null)
     */
    public void triggerPostCreationCallback(VMResponse vm, String playbook) {
        if (!ansibleConfig.enabled()) {
            LOG.debugf("Ansible callbacks disabled, skipping for VM %d", vm.vmid());
            return;
        }

        LOG.infof("Triggering Ansible callback for VM %d (%s), playbook=%s",
                 vm.vmid(), vm.name(), playbook);

        CompletableFuture.runAsync(() -> {
            try {
                executeCallback(vm, playbook);
            } catch (Exception e) {
                LOG.errorf(e, "Ansible callback failed for VM %d: %s", vm.vmid(), e.getMessage());
                // Continue - don't block VM creation on callback failure
            }
        });
    }

    /**
     * Execute the configured callback method with retry logic.
     */
    private void executeCallback(VMResponse vm, String playbook) {
        String effectivePlaybook = playbook != null ? playbook : ansibleConfig.defaultPlaybook();

        int maxRetries = ansibleConfig.maxRetries();
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                LOG.debugf("Callback attempt %d/%d for VM %d", attempt + 1, maxRetries, vm.vmid());

                switch (ansibleConfig.callbackType()) {
                    case "tower" -> executeTowerCallback(vm, effectivePlaybook);
                    case "awx" -> executeTowerCallback(vm, effectivePlaybook); // Same as tower
                    case "webhook" -> executeWebhookCallback(vm, effectivePlaybook);
                    default -> LOG.warnf("Unknown callback type: %s", ansibleConfig.callbackType());
                }

                LOG.infof("Ansible callback successful for VM %d after %d attempts", vm.vmid(), attempt + 1);
                return; // Success

            } catch (Exception e) {
                attempt++;
                if (attempt >= maxRetries) {
                    LOG.errorf(e, "Ansible callback failed for VM %d after %d attempts", vm.vmid(), maxRetries);
                    throw ProxmoxException.internalError("Ansible callback for VM " + vm.vmid() + " after " + maxRetries + " attempts", e);
                }

                // Wait before retry with exponential backoff
                try {
                    long delayMs = (long) (ansibleConfig.retryDelaySeconds() * 1000 * Math.pow(2, attempt - 1));
                    LOG.debugf("Retrying in %d ms", delayMs);
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    ProxmoxException interrupted = ProxmoxException.internalError("callback retry (interrupted)", ie);
                    interrupted.addSuppressed(e); // Preserve the original exception
                    throw interrupted;
                }
            }
        }
    }

    /**
     * Execute callback to Ansible Tower/AWX by launching a job template.
     */
    private void executeTowerCallback(VMResponse vm, String playbook) throws IOException, InterruptedException {
        String towerUrl = ansibleConfig.towerUrl();
        String towerToken = ansibleConfig.towerToken();
        String jobTemplateId = ansibleConfig.towerJobTemplateId();

        if (towerUrl == null || towerUrl.isBlank()) {
            throw new IllegalStateException("Tower URL not configured");
        }
        if (towerToken == null || towerToken.isBlank()) {
            throw new IllegalStateException("Tower token not configured");
        }
        if (jobTemplateId == null || jobTemplateId.isBlank()) {
            throw new IllegalStateException("Tower job template ID not configured");
        }

        String endpoint = String.format("%s/api/v2/job_templates/%s/launch/",
                                       towerUrl.replaceAll("/$", ""),
                                       jobTemplateId);

        // Build extra vars with VM information
        String extraVars = String.format("""
            {
              "moxxie_vm_id": %d,
              "moxxie_vm_name": "%s",
              "moxxie_vm_node": "%s",
              "moxxie_playbook": "%s"
            }
            """, vm.vmid(), vm.name(), vm.node(), playbook);

        String requestBody = String.format("""
            {
              "extra_vars": %s
            }
            """, extraVars);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Authorization", "Bearer " + towerToken)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(ansibleConfig.timeoutSeconds()))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        LOG.debugf("Sending Tower/AWX request to %s", endpoint);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(String.format("Tower API returned status %d: %s",
                                              response.statusCode(), response.body()));
        }

        LOG.infof("Tower job launched for VM %d: %s", vm.vmid(), response.body());
    }

    /**
     * Execute generic webhook callback.
     */
    private void executeWebhookCallback(VMResponse vm, String playbook) throws IOException, InterruptedException {
        String webhookUrl = ansibleConfig.webhookUrl();

        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalStateException("Webhook URL not configured");
        }

        // Build webhook payload
        String payload = String.format("""
            {
              "event": "vm_created",
              "vm": {
                "id": %d,
                "name": "%s",
                "node": "%s",
                "status": "%s",
                "tags": %s
              },
              "playbook": "%s",
              "timestamp": "%s"
            }
            """,
            vm.vmid(),
            vm.name(),
            vm.node(),
            vm.status(),
            vm.tags() != null ? "[\"" + String.join("\",\"", vm.tags()) + "\"]" : "[]",
            playbook,
            java.time.Instant.now()
        );

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(webhookUrl))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(ansibleConfig.timeoutSeconds()))
            .POST(HttpRequest.BodyPublishers.ofString(payload));

        // Add optional authentication header
        if (ansibleConfig.webhookToken() != null && !ansibleConfig.webhookToken().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + ansibleConfig.webhookToken());
        }

        HttpRequest request = requestBuilder.build();

        LOG.debugf("Sending webhook request to %s", webhookUrl);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(String.format("Webhook returned status %d: %s",
                                              response.statusCode(), response.body()));
        }

        LOG.infof("Webhook successful for VM %d: status=%d", vm.vmid(), response.statusCode());
    }

    /**
     * Check if Ansible callbacks are enabled.
     */
    public boolean isEnabled() {
        return ansibleConfig.enabled();
    }
}
