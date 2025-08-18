package com.coffeesprout.api.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FirmwareConfig and related classes
 */
public class FirmwareConfigTest {
    
    @Nested
    @DisplayName("FirmwareConfig Validation Tests")
    class ValidationTests {
        
        @Test
        @DisplayName("UEFI configuration requires EFI disk")
        void testUEFIRequiresEFIDisk() {
            FirmwareConfig config = new FirmwareConfig(
                FirmwareConfig.FirmwareType.UEFI,
                FirmwareConfig.MachineType.Q35,
                null,  // Missing EFI disk
                false
            );
            
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                config::validate
            );
            assertEquals("EFI disk configuration is required for UEFI firmware", exception.getMessage());
        }
        
        @Test
        @DisplayName("UEFI configuration requires Q35 machine type")
        void testUEFIRequiresQ35() {
            FirmwareConfig.EFIDiskConfig efidisk = new FirmwareConfig.EFIDiskConfig(
                "local-zfs", FirmwareConfig.EFIDiskConfig.EFIType.LARGE, false
            );
            
            FirmwareConfig config = new FirmwareConfig(
                FirmwareConfig.FirmwareType.UEFI,
                FirmwareConfig.MachineType.PC,  // Wrong machine type
                efidisk,
                false
            );
            
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                config::validate
            );
            assertEquals("UEFI firmware requires q35 machine type, not pc", exception.getMessage());
        }
        
        @Test
        @DisplayName("SeaBIOS should not have EFI disk configuration")
        void testSeaBIOSNoEFIDisk() {
            FirmwareConfig.EFIDiskConfig efidisk = new FirmwareConfig.EFIDiskConfig(
                "local-zfs", FirmwareConfig.EFIDiskConfig.EFIType.LARGE, false
            );
            
            FirmwareConfig config = new FirmwareConfig(
                FirmwareConfig.FirmwareType.SEABIOS,
                FirmwareConfig.MachineType.PC,
                efidisk,  // Should not be present for SeaBIOS
                false
            );
            
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                config::validate
            );
            assertEquals("EFI disk should not be configured for SEABIOS firmware", exception.getMessage());
        }
        
        @Test
        @DisplayName("Secure boot requires UEFI firmware")
        void testSecureBootRequiresUEFI() {
            FirmwareConfig config = new FirmwareConfig(
                FirmwareConfig.FirmwareType.SEABIOS,
                FirmwareConfig.MachineType.PC,
                null,
                true  // Secure boot with SeaBIOS
            );
            
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                config::validate
            );
            assertEquals("Secure boot is only available with UEFI firmware", exception.getMessage());
        }
        
        @Test
        @DisplayName("Valid UEFI configuration passes validation")
        void testValidUEFIConfiguration() {
            FirmwareConfig.EFIDiskConfig efidisk = new FirmwareConfig.EFIDiskConfig(
                "local-zfs", FirmwareConfig.EFIDiskConfig.EFIType.LARGE, false
            );
            
            FirmwareConfig config = new FirmwareConfig(
                FirmwareConfig.FirmwareType.UEFI,
                FirmwareConfig.MachineType.Q35,
                efidisk,
                false
            );
            
            assertDoesNotThrow(config::validate);
        }
        
        @Test
        @DisplayName("Valid SeaBIOS configuration passes validation")
        void testValidSeaBIOSConfiguration() {
            FirmwareConfig config = new FirmwareConfig(
                FirmwareConfig.FirmwareType.SEABIOS,
                FirmwareConfig.MachineType.PC,
                null,
                false
            );
            
            assertDoesNotThrow(config::validate);
        }
    }
    
    @Nested
    @DisplayName("EFIDiskConfig Tests")
    class EFIDiskConfigTests {
        
        @Test
        @DisplayName("EFI disk generates correct Proxmox string with defaults")
        void testEFIDiskProxmoxStringDefaults() {
            FirmwareConfig.EFIDiskConfig efidisk = new FirmwareConfig.EFIDiskConfig(
                "local-zfs", null, null
            );
            
            String expected = "local-zfs:1,efitype=4m,pre-enrolled-keys=0";
            assertEquals(expected, efidisk.toProxmoxString());
        }
        
        @Test
        @DisplayName("EFI disk generates correct Proxmox string with custom settings")
        void testEFIDiskProxmoxStringCustom() {
            FirmwareConfig.EFIDiskConfig efidisk = new FirmwareConfig.EFIDiskConfig(
                "storage01", FirmwareConfig.EFIDiskConfig.EFIType.SMALL, true
            );
            
            String expected = "storage01:1,efitype=2m,pre-enrolled-keys=1";
            assertEquals(expected, efidisk.toProxmoxString());
        }
        
        @Test
        @DisplayName("EFI disk with large type")
        void testEFIDiskLargeType() {
            FirmwareConfig.EFIDiskConfig efidisk = new FirmwareConfig.EFIDiskConfig(
                "local-zfs", FirmwareConfig.EFIDiskConfig.EFIType.LARGE, false
            );
            
            String expected = "local-zfs:1,efitype=4m,pre-enrolled-keys=0";
            assertEquals(expected, efidisk.toProxmoxString());
        }
    }
    
    @Nested
    @DisplayName("Factory Methods Tests")
    class FactoryMethodsTests {
        
        @Test
        @DisplayName("Default SeaBIOS configuration")
        void testDefaultSeaBIOS() {
            FirmwareConfig config = FirmwareConfig.defaultSeaBIOS();
            
            assertEquals(FirmwareConfig.FirmwareType.SEABIOS, config.type());
            assertEquals(FirmwareConfig.MachineType.PC, config.machine());
            assertNull(config.efidisk());
            assertFalse(config.secureboot());
        }
        
        @Test
        @DisplayName("Default UEFI configuration")
        void testDefaultUEFI() {
            String storage = "local-zfs";
            FirmwareConfig config = FirmwareConfig.defaultUEFI(storage);
            
            assertEquals(FirmwareConfig.FirmwareType.UEFI, config.type());
            assertEquals(FirmwareConfig.MachineType.Q35, config.machine());
            assertNotNull(config.efidisk());
            assertEquals(storage, config.efidisk().storage());
            assertEquals(FirmwareConfig.EFIDiskConfig.EFIType.LARGE, config.efidisk().efitype());
            assertFalse(config.efidisk().preEnrolledKeys());
            assertFalse(config.secureboot());
        }
    }
    
    @Nested
    @DisplayName("Enum Value Tests")
    class EnumValueTests {
        
        @Test
        @DisplayName("FirmwareType Proxmox values")
        void testFirmwareTypeValues() {
            assertEquals("seabios", FirmwareConfig.FirmwareType.SEABIOS.getProxmoxValue());
            assertEquals("ovmf", FirmwareConfig.FirmwareType.UEFI.getProxmoxValue());
        }
        
        @Test
        @DisplayName("MachineType values")
        void testMachineTypeValues() {
            assertEquals("pc", FirmwareConfig.MachineType.PC.getValue());
            assertEquals("q35", FirmwareConfig.MachineType.Q35.getValue());
        }
        
        @Test
        @DisplayName("EFIType values")
        void testEFITypeValues() {
            assertEquals("2m", FirmwareConfig.EFIDiskConfig.EFIType.SMALL.getValue());
            assertEquals("4m", FirmwareConfig.EFIDiskConfig.EFIType.LARGE.getValue());
        }
        
        @Test
        @DisplayName("Recommended machine types")
        void testRecommendedMachineTypes() {
            assertEquals("pc", FirmwareConfig.FirmwareType.SEABIOS.getRecommendedMachine());
            assertEquals("q35", FirmwareConfig.FirmwareType.UEFI.getRecommendedMachine());
        }
    }
    
    @Nested
    @DisplayName("Configuration Helper Tests")
    class ConfigurationHelperTests {
        
        @Test
        @DisplayName("UEFI configuration requires post-creation setup")
        void testUEFIRequiresPostCreationSetup() {
            FirmwareConfig.EFIDiskConfig efidisk = new FirmwareConfig.EFIDiskConfig(
                "local-zfs", FirmwareConfig.EFIDiskConfig.EFIType.LARGE, false
            );
            
            FirmwareConfig config = new FirmwareConfig(
                FirmwareConfig.FirmwareType.UEFI,
                FirmwareConfig.MachineType.Q35,
                efidisk,
                false
            );
            
            assertTrue(config.requiresPostCreationSetup());
        }
        
        @Test
        @DisplayName("SeaBIOS configuration does not require post-creation setup")
        void testSeaBIOSNoPostCreationSetup() {
            FirmwareConfig config = FirmwareConfig.defaultSeaBIOS();
            
            assertFalse(config.requiresPostCreationSetup());
        }
        
        @Test
        @DisplayName("UEFI without EFI disk does not require post-creation setup")
        void testUEFIWithoutEFIDiskNoPostCreationSetup() {
            FirmwareConfig config = new FirmwareConfig(
                FirmwareConfig.FirmwareType.UEFI,
                FirmwareConfig.MachineType.Q35,
                null,  // No EFI disk
                false
            );
            
            assertFalse(config.requiresPostCreationSetup());
        }
    }
}