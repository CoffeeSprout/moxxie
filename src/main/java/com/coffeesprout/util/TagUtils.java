package com.coffeesprout.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility class for consistent tag handling across the application.
 * Provides methods for creating structured tags and parsing tag strings.
 */
public class TagUtils {

    // Core tags
    public static final String MOXXIE_MANAGED = "moxxie";
    public static final String ALWAYS_ON = "always-on";
    public static final String MAINTENANCE_OK = "maint-ok";

    // Tag prefixes
    // Using hyphen instead of colon since Proxmox doesn't support colons in tags
    private static final String CLIENT_PREFIX = "client-";
    private static final String ENV_PREFIX = "env-";
    private static final String K8S_PREFIX = "k8s-";
    private static final String LOCATION_PREFIX = "location-";
    private static final String CRITICALITY_PREFIX = "criticality-";

    // Validation pattern - alphanumeric, underscore, dash, and dot (no colons allowed by Proxmox)
    private static final Pattern TAG_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]+$");

    /**
     * Create a client tag
     */
    public static String client(String name) {
        return CLIENT_PREFIX + validateTagValue(name);
    }

    /**
     * Create an environment tag
     */
    public static String env(String environment) {
        return ENV_PREFIX + validateTagValue(environment);
    }

    /**
     * Create a Kubernetes role tag
     */
    public static String k8sRole(String role) {
        return K8S_PREFIX + validateTagValue(role);
    }

    /**
     * Create a location tag
     */
    public static String location(String location) {
        return LOCATION_PREFIX + validateTagValue(location);
    }

    /**
     * Create a criticality tag
     */
    public static String criticality(String level) {
        return CRITICALITY_PREFIX + validateTagValue(level);
    }

    /**
     * Parse semicolon-separated tag string into a set (Proxmox format)
     */
    public static Set<String> parseVMTags(String tagString) {
        Set<String> tags = new HashSet<>();
        if (tagString == null || tagString.trim().isEmpty()) {
            return tags;
        }

        String[] tagArray = tagString.split(";");
        for (String tag : tagArray) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return tags;
    }

    /**
     * Convert tag set to semicolon-separated string for Proxmox
     */
    public static String tagsToString(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return String.join(";", tags);
    }

    /**
     * Validate tag value and convert to lowercase
     */
    private static String validateTagValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Tag value cannot be null or empty");
        }

        // Just trim the value
        String cleaned = value.trim();

        if (!cleaned.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new IllegalArgumentException("Invalid tag value: " + value +
                ". Only alphanumeric characters, underscores, dashes, and dots are allowed.");
        }

        return cleaned.toLowerCase();
    }

    /**
     * Validate a complete tag (including structured tags like client:name)
     */
    public static boolean isValidTag(String tag) {
        return tag != null && TAG_PATTERN.matcher(tag).matches();
    }

    /**
     * Extract client name from VM name if it follows pattern: <client>-<rest>
     */
    public static String extractClientFromName(String vmName) {
        if (vmName == null || !vmName.contains("-")) {
            return null;
        }

        String[] parts = vmName.split("-", 2);
        if (parts.length > 0 && !parts[0].isEmpty()) {
            try {
                return validateTagValue(parts[0]);
            } catch (IllegalArgumentException e) {
                // Invalid client name format
                return null;
            }
        }
        return null;
    }

    /**
     * Extract environment from VM name if it contains known environment patterns
     */
    public static String extractEnvironmentFromName(String vmName) {
        if (vmName == null) {
            return null;
        }

        String lower = vmName.toLowerCase();

        // Check for common environment patterns
        if (lower.contains("-prod") || lower.contains("prod-") || lower.endsWith("-prod")) {
            return "prod";
        } else if (lower.contains("-dev") || lower.contains("dev-") || lower.endsWith("-dev")) {
            return "dev";
        } else if (lower.contains("-test") || lower.contains("test-") || lower.endsWith("-test")) {
            return "test";
        } else if (lower.contains("-staging") || lower.contains("staging-") || lower.endsWith("-staging")) {
            return "staging";
        }

        return null;
    }

    /**
     * Check if VM name suggests it's a Kubernetes node
     */
    public static String extractK8sRole(String vmName) {
        if (vmName == null) {
            return null;
        }

        String lower = vmName.toLowerCase();

        if (lower.contains("-cp-") || lower.contains("controlplane") || lower.contains("master")) {
            return "controlplane";
        } else if (lower.contains("-worker-") || lower.contains("worker")) {
            return "worker";
        }

        return null;
    }

    /**
     * Auto-generate tags based on VM name
     */
    public static Set<String> autoGenerateTags(String vmName) {
        Set<String> tags = new HashSet<>();

        // Always add moxxie managed tag
        tags.add(MOXXIE_MANAGED);

        // Try to extract client
        String client = extractClientFromName(vmName);
        if (client != null) {
            tags.add(client(client));
        }

        // Try to extract environment
        String env = extractEnvironmentFromName(vmName);
        if (env != null) {
            tags.add(env(env));

            // Production VMs are always-on by default
            if ("prod".equals(env)) {
                tags.add(ALWAYS_ON);
            } else {
                tags.add(MAINTENANCE_OK);
            }
        }

        // Check for Kubernetes roles
        String k8sRole = extractK8sRole(vmName);
        if (k8sRole != null) {
            tags.add(k8sRole(k8sRole));

            // Control plane nodes are always-on
            if ("controlplane".equals(k8sRole)) {
                tags.add(ALWAYS_ON);
            }
        }

        return tags;
    }
}
