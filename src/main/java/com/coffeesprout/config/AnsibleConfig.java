package com.coffeesprout.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Configuration for Ansible integration and callbacks.
 */
@ConfigMapping(prefix = "moxxie.ansible")
public interface AnsibleConfig {

    /**
     * Enable Ansible callbacks after VM creation.
     * Default: false
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Callback type: tower, awx, or webhook.
     * Default: webhook
     */
    @WithDefault("webhook")
    @WithName("callback-type")
    String callbackType();

    /**
     * Default playbook to execute if not specified.
     * Default: site.yml
     */
    @WithDefault("site.yml")
    @WithName("default-playbook")
    String defaultPlaybook();

    /**
     * Ansible Tower/AWX base URL (e.g., https://tower.example.com).
     * Required if callback-type is tower or awx.
     */
    @WithName("tower.url")
    String towerUrl();

    /**
     * Ansible Tower/AWX API token for authentication.
     * Required if callback-type is tower or awx.
     */
    @WithName("tower.token")
    String towerToken();

    /**
     * Ansible Tower/AWX job template ID to launch.
     * Required if callback-type is tower or awx.
     */
    @WithName("tower.job-template-id")
    String towerJobTemplateId();

    /**
     * Generic webhook URL for callbacks.
     * Required if callback-type is webhook.
     */
    @WithName("webhook.url")
    String webhookUrl();

    /**
     * Optional bearer token for webhook authentication.
     */
    @WithName("webhook.token")
    String webhookToken();

    /**
     * Maximum retry attempts for failed callbacks.
     * Default: 3
     */
    @WithDefault("3")
    @WithName("max-retries")
    int maxRetries();

    /**
     * Initial delay between retries in seconds (uses exponential backoff).
     * Default: 5
     */
    @WithDefault("5")
    @WithName("retry-delay-seconds")
    int retryDelaySeconds();

    /**
     * Timeout for callback requests in seconds.
     * Default: 30
     */
    @WithDefault("30")
    @WithName("timeout-seconds")
    int timeoutSeconds();
}
