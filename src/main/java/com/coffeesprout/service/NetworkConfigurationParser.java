package com.coffeesprout.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import com.coffeesprout.api.dto.VMDetailResponse;
import com.coffeesprout.constants.VMConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for parsing network configuration from Proxmox VM config.
 * Extracts network interface parsing logic from VMResource for better
 * separation of concerns and testability.
 */
@ApplicationScoped
public class NetworkConfigurationParser {

    private static final Logger LOG = LoggerFactory.getLogger(NetworkConfigurationParser.class);
    private static final Pattern NETWORK_PATTERN = Pattern.compile(VMConstants.Network.INTERFACE_PATTERN);

    /**
     * Parse network interfaces from VM configuration.
     *
     * @param config VM configuration map from Proxmox
     * @return List of parsed network interface information
     */
    public List<VMDetailResponse.NetworkInterfaceInfo> parseFromConfig(Map<String, Object> config) {
        if (config == null) {
            return new ArrayList<>();
        }

        return config.entrySet().stream()
            .filter(entry -> isNetworkInterface(entry.getKey()))
            .map(entry -> parseNetworkInterface(entry.getKey(), entry.getValue()))
            .filter(Objects::nonNull)
            .sorted((a, b) -> a.name().compareTo(b.name()))
            .collect(Collectors.toList());
    }

    /**
     * Check if a configuration key represents a network interface.
     *
     * @param key Configuration key to check
     * @return true if the key is a network interface (e.g., net0, net1)
     */
    public boolean isNetworkInterface(String key) {
        return key != null && NETWORK_PATTERN.matcher(key).matches();
    }

    /**
     * Parse a single network interface configuration string.
     *
     * @param interfaceName Interface name (e.g., "net0")
     * @param configValue Configuration value from Proxmox
     * @return Parsed network interface info, or null if parsing fails
     */
    public VMDetailResponse.NetworkInterfaceInfo parseNetworkInterface(String interfaceName, Object configValue) {
        if (configValue == null) {
            return null;
        }

        String config = configValue.toString();
        LOG.debug("Parsing network interface {}: {}", interfaceName, config);

        try {
            // Parse the network string format: "model=virtio,bridge=vmbr0,tag=100,..."
            Map<String, String> params = parseNetworkParams(config);

            String model = extractModel(config, params);
            String bridge = params.get("bridge");
            String macAddress = params.get("macaddr");
            Integer vlan = parseVlan(params);
            String firewall = params.get("firewall");

            return new VMDetailResponse.NetworkInterfaceInfo(
                interfaceName,
                macAddress,
                bridge,
                vlan,
                model,
                firewall != null && "1".equals(firewall),
                config
            );

        } catch (Exception e) {
            LOG.warn("Failed to parse network interface {}: {}", interfaceName, e.getMessage());
            return new VMDetailResponse.NetworkInterfaceInfo(
                interfaceName,
                null,
                null,
                null,
                null,
                false,
                config
            );
        }
    }

    /**
     * Parse network parameters from configuration string.
     *
     * @param config Network configuration string
     * @return Map of parameter names to values
     */
    private Map<String, String> parseNetworkParams(String config) {
        Map<String, String> params = new java.util.HashMap<>();

        // Split by comma, but be careful with nested values
        String[] parts = config.split(",");
        for (String part : parts) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length == 2) {
                params.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }

        return params;
    }

    /**
     * Extract network model from configuration.
     * The model may be the first part before comma or in model= parameter.
     *
     * @param config Full configuration string
     * @param params Parsed parameters
     * @return Network model (e.g., "virtio", "e1000")
     */
    private String extractModel(String config, Map<String, String> params) {
        // Check if there's an explicit model parameter
        if (params.containsKey("model")) {
            return params.get("model");
        }

        // Otherwise, the model is usually the first part
        int commaIndex = config.indexOf(',');
        if (commaIndex > 0) {
            String firstPart = config.substring(0, commaIndex);
            if (!firstPart.contains("=")) {
                return firstPart;
            }
        }

        // Default to virtio if no model specified
        return VMConstants.Network.DEFAULT_MODEL;
    }

    /**
     * Parse VLAN tag from parameters.
     *
     * @param params Network parameters
     * @return VLAN tag or null if not present
     */
    private Integer parseVlan(Map<String, String> params) {
        String tag = params.get("tag");
        if (tag == null) {
            tag = params.get("vlan");
        }
        return parseInteger(tag);
    }

    /**
     * Parse integer value safely.
     *
     * @param value String value to parse
     * @return Parsed integer or null
     */
    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOG.debug("Failed to parse integer: {}", value);
            return null;
        }
    }

    /**
     * Parse double value safely.
     *
     * @param value String value to parse
     * @return Parsed double or null
     */
    private Double parseDouble(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            LOG.debug("Failed to parse double: {}", value);
            return null;
        }
    }

    /**
     * Parse boolean value from string.
     *
     * @param value String value to parse
     * @return Parsed boolean or null
     */
    private Boolean parseBoolean(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }
}
