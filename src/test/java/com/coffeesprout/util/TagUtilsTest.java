package com.coffeesprout.util;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class TagUtilsTest {

    @Test
    void testConstants() {
        assertEquals("moxxie", TagUtils.MOXXIE_MANAGED);
        assertEquals("always-on", TagUtils.ALWAYS_ON);
        assertEquals("maint-ok", TagUtils.MAINTENANCE_OK);
    }

    @Test
    void testClientTag() {
        assertEquals("client-nixz", TagUtils.client("nixz"));
        assertEquals("client-acme", TagUtils.client("ACME")); // Should lowercase
        assertEquals("client-test-123", TagUtils.client("test-123"));
    }

    @Test
    void testClientTagInvalid() {
        assertThrows(IllegalArgumentException.class, () -> TagUtils.client(null));
        assertThrows(IllegalArgumentException.class, () -> TagUtils.client(""));
        assertThrows(IllegalArgumentException.class, () -> TagUtils.client("  "));
        // client-test is actually valid since it's just a client name with hyphen
        // Testing with invalid characters instead
        assertThrows(IllegalArgumentException.class, () -> TagUtils.client("test:invalid")); // Colons not allowed
        assertThrows(IllegalArgumentException.class, () -> TagUtils.client("test@123")); // Invalid chars
    }

    @Test
    void testEnvTag() {
        assertEquals("env-prod", TagUtils.env("prod"));
        assertEquals("env-dev", TagUtils.env("DEV")); // Should lowercase
        assertEquals("env-staging", TagUtils.env("staging"));
    }

    @Test
    void testK8sRoleTag() {
        assertEquals("k8s-controlplane", TagUtils.k8sRole("controlplane"));
        assertEquals("k8s-worker", TagUtils.k8sRole("worker"));
        assertEquals("k8s-etcd", TagUtils.k8sRole("ETCD")); // Should lowercase
    }

    @Test
    void testLocationTag() {
        assertEquals("location-ams", TagUtils.location("AMS"));
        assertEquals("location-us-east-1", TagUtils.location("us-east-1"));
    }

    @Test
    void testCriticalityTag() {
        assertEquals("criticality-high", TagUtils.criticality("HIGH"));
        assertEquals("criticality-low", TagUtils.criticality("low"));
    }

    @Test
    void testParseVMTags() {
        // Empty cases
        assertTrue(TagUtils.parseVMTags(null).isEmpty());
        assertTrue(TagUtils.parseVMTags("").isEmpty());
        assertTrue(TagUtils.parseVMTags("  ").isEmpty());

        // Single tag
        Set<String> tags = TagUtils.parseVMTags("moxxie");
        assertEquals(1, tags.size());
        assertTrue(tags.contains("moxxie"));

        // Multiple tags - using semicolons as Proxmox expects
        tags = TagUtils.parseVMTags("moxxie;client-nixz;env-prod");
        assertEquals(3, tags.size());
        assertTrue(tags.contains("moxxie"));
        assertTrue(tags.contains("client-nixz"));
        assertTrue(tags.contains("env-prod"));

        // With spaces
        tags = TagUtils.parseVMTags("moxxie; client-nixz ; env-prod ");
        assertEquals(3, tags.size());
        assertTrue(tags.contains("moxxie"));
        assertTrue(tags.contains("client-nixz"));
        assertTrue(tags.contains("env-prod"));
    }

    @Test
    void testTagsToString() {
        assertEquals("", TagUtils.tagsToString(null));
        assertEquals("", TagUtils.tagsToString(Set.of()));

        // Order might vary in set, so check contains
        String result = TagUtils.tagsToString(Set.of("moxxie", "env-prod"));
        assertTrue(result.contains("moxxie"));
        assertTrue(result.contains("env-prod"));
        assertTrue(result.contains(";"));
    }

    @ParameterizedTest
    @CsvSource({
        "moxxie,true",
        "client-nixz,true",
        "env-prod,true",
        "k8s-worker,true",
        "test-tag_123.abc,true",
        "TEST-VALUE,true",
        "'',false",
        "tag with spaces,false",
        "tag@symbol,false",
        "tag#hash,false",
        "client:test:nested,false"
    })
    void testIsValidTag(String tag, boolean expected) {
        assertEquals(expected, TagUtils.isValidTag(tag));
    }

    @ParameterizedTest
    @CsvSource({
        "nixz-web-01,nixz",
        "acme-db-prod,acme",
        "test-app,test",
        "single,",
        "'-test',''"
    })
    void testExtractClientFromName(String vmName, String expectedClient) {
        String result = TagUtils.extractClientFromName(vmName);
        if (expectedClient == null || expectedClient.isEmpty()) {
            assertNull(result);
        } else {
            assertEquals(expectedClient, result);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "web-prod,prod",
        "db-prod-01,prod",
        "app-dev,dev",
        "test-server,test",
        "staging-api,staging",
        "prod,",  // Single word 'prod' doesn't match pattern
        "development,",  // Single word 'development' doesn't match pattern
        "random-name,",
        ","
    })
    void testExtractEnvironmentFromName(String vmName, String expectedEnv) {
        String result = TagUtils.extractEnvironmentFromName(vmName);
        if (expectedEnv == null || expectedEnv.isEmpty()) {
            assertNull(result);
        } else {
            assertEquals(expectedEnv, result);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "k8s-cp-01,controlplane",
        "controlplane-1,controlplane",
        "k8s-master,controlplane",
        "k8s-worker-01,worker",
        "worker-node-1,worker",
        "web-server,",
        ","
    })
    void testExtractK8sRole(String vmName, String expectedRole) {
        String result = TagUtils.extractK8sRole(vmName);
        if (expectedRole == null || expectedRole.isEmpty()) {
            assertNull(result);
        } else {
            assertEquals(expectedRole, result);
        }
    }

    @Test
    void testAutoGenerateTags() {
        // Basic VM - gets moxxie tag and extracted client tag
        Set<String> tags = TagUtils.autoGenerateTags("random-vm");
        assertEquals(2, tags.size());  // moxxie + client-random
        assertTrue(tags.contains("moxxie"));
        assertTrue(tags.contains("client-random"));

        // Client VM with environment
        tags = TagUtils.autoGenerateTags("nixz-web-prod");
        assertTrue(tags.contains("moxxie"));
        assertTrue(tags.contains("client-nixz"));
        assertTrue(tags.contains("env-prod"));
        assertTrue(tags.contains("always-on")); // prod is always-on

        // Dev environment
        tags = TagUtils.autoGenerateTags("acme-api-dev");
        assertTrue(tags.contains("moxxie"));
        assertTrue(tags.contains("client-acme"));
        assertTrue(tags.contains("env-dev"));
        assertTrue(tags.contains("maint-ok")); // non-prod is maint-ok

        // Kubernetes control plane
        tags = TagUtils.autoGenerateTags("k8s-cp-01");
        assertTrue(tags.contains("moxxie"));
        assertTrue(tags.contains("k8s-controlplane"));
        assertTrue(tags.contains("always-on")); // control plane is always-on

        // Kubernetes worker
        tags = TagUtils.autoGenerateTags("k8s-worker-03");
        assertTrue(tags.contains("moxxie"));
        assertTrue(tags.contains("k8s-worker"));
        assertFalse(tags.contains("always-on")); // workers are not always-on by default
    }
}
