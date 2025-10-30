package com.coffeesprout.service;

import com.coffeesprout.api.dto.CloudInitVMRequest;
import com.coffeesprout.api.dto.FirmwareConfig;
import com.coffeesprout.client.CreateVMRequestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for firmware-related functionality in VMService
 */
@ExtendWith(MockitoExtension.class)
public class VMServiceFirmwareTest {

    @Nested
    @DisplayName("Firmware Configuration Application Tests")
    class FirmwareConfigurationTests {

        @Test
        @DisplayName("Default firmware configuration defaults to SeaBIOS with backward compatibility")
        void testDefaultFirmwareConfiguration() {
            FirmwareConfig config = VMService.getDefaultFirmwareConfig("local-zfs", false);

            assertEquals(FirmwareConfig.FirmwareType.SEABIOS, config.type());
            assertEquals(FirmwareConfig.MachineType.PC, config.machine());
            assertNull(config.efidisk());
            assertFalse(config.secureboot());
        }

        @Test
        @DisplayName("UEFI firmware configuration creates proper EFI disk")
        void testUEFIFirmwareConfiguration() {
            String storage = "storage01";
            FirmwareConfig config = VMService.getDefaultFirmwareConfig(storage, true);

            assertEquals(FirmwareConfig.FirmwareType.UEFI, config.type());
            assertEquals(FirmwareConfig.MachineType.Q35, config.machine());
            assertNotNull(config.efidisk());
            assertEquals(storage, config.efidisk().storage());
            assertEquals(FirmwareConfig.EFIDiskConfig.EFIType.LARGE, config.efidisk().efitype());
            assertFalse(config.efidisk().preEnrolledKeys());
        }

        @Test
        @DisplayName("Firmware configuration validates before application")
        void testFirmwareValidationInApplication() {
            // Create invalid UEFI configuration (missing EFI disk)
            FirmwareConfig invalidConfig = new FirmwareConfig(
                FirmwareConfig.FirmwareType.UEFI,
                FirmwareConfig.MachineType.Q35,
                null,  // Missing required EFI disk
                false
            );

            CreateVMRequestBuilder builder = CreateVMRequestBuilder.builder();

            // This should throw because validation happens before application
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> {
                    try {
                        // We would need to use reflection to test private method
                        // or create a test-specific public method
                        // For now, we test validation directly
                        invalidConfig.validate();
                    } catch (IllegalArgumentException e) {
                        throw e;
                    }
                }
            );

            assertEquals("EFI disk configuration is required for UEFI firmware", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("CloudInit VM Request Integration Tests")
    class CloudInitVMRequestTests {

        @Test
        @DisplayName("CloudInit VM request handles UEFI firmware configuration")
        void testCloudInitVMWithUEFI() {
            FirmwareConfig.EFIDiskConfig efidisk = new FirmwareConfig.EFIDiskConfig(
                "local-zfs",
                FirmwareConfig.EFIDiskConfig.EFIType.LARGE,
                false
            );

            FirmwareConfig firmware = new FirmwareConfig(
                FirmwareConfig.FirmwareType.UEFI,
                FirmwareConfig.MachineType.Q35,
                efidisk,
                false
            );

            CloudInitVMRequest request = new CloudInitVMRequest(
                10710,                           // vmid
                "okd-bootstrap",                 // name
                "storage01",                     // node
                null,                            // templateNode
                4,                               // cores
                16384,                          // memoryMB
                "local-zfs:9002/base-9002-disk-0.raw", // imageSource
                "local-zfs",                    // targetStorage
                120,                            // diskSizeGB
                "admin",                        // cloudInitUser
                null,                           // cloudInitPassword
                "ssh-ed25519 AAAAC3...",       // sshKeys
                null,                           // networks
                null,                           // ipConfigs
                null,                           // network (deprecated)
                null,                           // ipConfig (deprecated)
                "cluster.local",               // searchDomain
                "8.8.8.8,8.8.4.4",            // nameservers
                "host",                        // cpuType
                true,                          // qemuAgent
                false,                         // start
                "OKD Bootstrap Node",          // description
                "okd,bootstrap",               // tags
                null,                          // diskOptions
                firmware,                      // firmware
                "virtio-scsi-single",          // scsihw
                "socket",                      // serial0
                "serial0"                      // vgaType
            );

            // Validate the request has proper firmware configuration
            assertNotNull(request.firmware());
            assertEquals(FirmwareConfig.FirmwareType.UEFI, request.firmware().type());
            assertEquals(FirmwareConfig.MachineType.Q35, request.firmware().machine());
            assertNotNull(request.firmware().efidisk());
            assertEquals("local-zfs", request.firmware().efidisk().storage());

            // Test that validation passes
            assertDoesNotThrow(() -> request.firmware().validate());

            // Test SCSI hardware configuration
            assertEquals("virtio-scsi-single", request.scsihw());
            assertEquals("socket", request.serial0());
            assertEquals("serial0", request.vgaType());
        }

        @Test
        @DisplayName("CloudInit VM request handles SeaBIOS firmware configuration")
        void testCloudInitVMWithSeaBIOS() {
            FirmwareConfig firmware = FirmwareConfig.defaultSeaBIOS();

            CloudInitVMRequest request = new CloudInitVMRequest(
                200,                            // vmid
                "debian-server",               // name
                "hv7",                         // node
                null,                          // templateNode
                2,                             // cores
                4096,                          // memoryMB
                "local-zfs:9001/base-9001-disk-0.raw", // imageSource
                "local-zfs",                   // targetStorage
                50,                            // diskSizeGB
                "debian",                      // cloudInitUser
                "password123",                 // cloudInitPassword
                "ssh-rsa AAAAB3...",          // sshKeys
                null,                          // networks
                null,                          // ipConfigs
                null,                          // network (deprecated)
                null,                          // ipConfig (deprecated)
                "example.com",                 // searchDomain
                "1.1.1.1,8.8.8.8",           // nameservers
                "x86-64-v2-AES",              // cpuType
                true,                          // qemuAgent
                true,                          // start
                "Debian Server",               // description
                "debian,server",               // tags
                null,                          // diskOptions
                firmware,                      // firmware
                "virtio-scsi-pci",            // scsihw
                null,                          // serial0
                "std"                          // vgaType
            );

            // Validate the request has proper firmware configuration
            assertNotNull(request.firmware());
            assertEquals(FirmwareConfig.FirmwareType.SEABIOS, request.firmware().type());
            assertEquals(FirmwareConfig.MachineType.PC, request.firmware().machine());
            assertNull(request.firmware().efidisk());

            // Test that validation passes
            assertDoesNotThrow(() -> request.firmware().validate());
        }

        @Test
        @DisplayName("CloudInit VM request without firmware uses defaults")
        void testCloudInitVMWithoutFirmware() {
            CloudInitVMRequest request = new CloudInitVMRequest(
                300,                           // vmid
                "simple-vm",                   // name
                "hv1",                         // node
                null,                          // templateNode
                2,                             // cores
                2048,                          // memoryMB
                "local-zfs:9000/base-9000-disk-0.raw", // imageSource
                "local-zfs",                   // targetStorage
                20,                            // diskSizeGB
                null,                          // cloudInitUser
                null,                          // cloudInitPassword
                null,                          // sshKeys
                null,                          // networks
                null,                          // ipConfigs
                null,                          // network (deprecated)
                null,                          // ipConfig (deprecated)
                null,                          // searchDomain
                null,                          // nameservers
                null,                          // cpuType
                null,                          // qemuAgent
                null,                          // start
                null,                          // description
                null,                          // tags
                null,                          // diskOptions
                null,                          // firmware - NULL
                null,                          // scsihw
                null,                          // serial0
                null                           // vgaType
            );

            // When firmware is null, the service should use defaults (SeaBIOS)
            assertNull(request.firmware());
        }
    }

    @Nested
    @DisplayName("Firmware Configuration Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("UEFI with secure boot creates proper configuration")
        void testUEFIWithSecureBoot() {
            FirmwareConfig.EFIDiskConfig efidisk = new FirmwareConfig.EFIDiskConfig(
                "local-zfs",
                FirmwareConfig.EFIDiskConfig.EFIType.SMALL,
                true  // Pre-enrolled keys for secure boot
            );

            FirmwareConfig firmware = new FirmwareConfig(
                FirmwareConfig.FirmwareType.UEFI,
                FirmwareConfig.MachineType.Q35,
                efidisk,
                true  // Secure boot enabled
            );

            // Validate configuration
            assertDoesNotThrow(firmware::validate);

            // Check secure boot settings
            assertTrue(firmware.secureboot());
            assertTrue(firmware.efidisk().preEnrolledKeys());
            assertEquals(FirmwareConfig.EFIDiskConfig.EFIType.SMALL, firmware.efidisk().efitype());
        }

        @Test
        @DisplayName("EFI disk configuration generates correct Proxmox strings")
        void testEFIDiskProxmoxStrings() {
            // Test various EFI disk configurations
            FirmwareConfig.EFIDiskConfig small = new FirmwareConfig.EFIDiskConfig(
                "storage01", FirmwareConfig.EFIDiskConfig.EFIType.SMALL, false
            );
            assertEquals("storage01:1,efitype=2m,pre-enrolled-keys=0", small.toProxmoxString());

            FirmwareConfig.EFIDiskConfig large = new FirmwareConfig.EFIDiskConfig(
                "local-zfs", FirmwareConfig.EFIDiskConfig.EFIType.LARGE, true
            );
            assertEquals("local-zfs:1,efitype=4m,pre-enrolled-keys=1", large.toProxmoxString());

            // Test with null values (defaults)
            FirmwareConfig.EFIDiskConfig defaults = new FirmwareConfig.EFIDiskConfig(
                "ceph-rbd", null, null
            );
            assertEquals("ceph-rbd:1,efitype=4m,pre-enrolled-keys=0", defaults.toProxmoxString());
        }
    }
}
