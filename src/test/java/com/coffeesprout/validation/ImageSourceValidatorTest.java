package com.coffeesprout.validation;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ImageSourceValidatorTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "local-zfs:9002/base-9002-disk-0.raw",
        "local-lvm:9001/base-9001-disk-0.qcow2",
        "ceph-pool:8000/base-8000-disk-1.raw",
        "storage:1234/base-1234-disk-10.vmdk"
    })
    void testValidImageSource(String imageSource) {
        TestDto dto = new TestDto(imageSource);
        Set<ConstraintViolation<TestDto>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Expected no violations for: " + imageSource);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "local-iso:iso/debian-12.iso",
        "util-iso:iso/ubuntu-22.04.iso",
        "storage:iso/talos-v1.6.0.iso",
        "local-zfs:/path/to/file.iso",
        "ISO-storage:template.qcow2"
    })
    void testInvalidImageSourceWithISO(String imageSource) {
        TestDto dto = new TestDto(imageSource);
        Set<ConstraintViolation<TestDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Expected violations for: " + imageSource);

        String message = violations.iterator().next().getMessage();
        assertTrue(message.contains("Do not use ISO file paths"),
                  "Expected ISO-specific error message, got: " + message);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "local-zfs:template.qcow2",
        "storage:9002/disk-0.raw",
        "local-lvm:base-9001.qcow2",
        "invalid-format",
        "storage:vmid/wrong-format.raw"
    })
    void testInvalidImageSourceFormat(String imageSource) {
        TestDto dto = new TestDto(imageSource);
        Set<ConstraintViolation<TestDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Expected violations for: " + imageSource);

        String message = violations.iterator().next().getMessage();
        assertTrue(message.contains("Expected format: 'storage:vmid/base-vmid-disk-N'"),
                  "Expected format error message, got: " + message);
    }

    @Test
    void testNullImageSource() {
        TestDto dto = new TestDto(null);
        Set<ConstraintViolation<TestDto>> violations = validator.validate(dto);
        // Should be valid - let @NotBlank handle null
        assertTrue(violations.isEmpty(), "Null should be valid (handled by @NotBlank)");
    }

    @Test
    void testEmptyImageSource() {
        TestDto dto = new TestDto("");
        Set<ConstraintViolation<TestDto>> violations = validator.validate(dto);
        // Should be valid - let @NotBlank handle empty
        assertTrue(violations.isEmpty(), "Empty should be valid (handled by @NotBlank)");
    }

    // Test DTO class
    static class TestDto {
        @ValidImageSource
        private final String imageSource;

        TestDto(String imageSource) {
            this.imageSource = imageSource;
        }

        public String getImageSource() {
            return imageSource;
        }
    }
}
