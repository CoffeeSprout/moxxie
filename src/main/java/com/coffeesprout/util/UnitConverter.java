package com.coffeesprout.util;

/**
 * Utility class for converting between different units of measurement.
 * Provides consistent conversion methods to avoid magic numbers throughout the codebase.
 */
public final class UnitConverter {

    private UnitConverter() {
        // Prevent instantiation
    }

    /**
     * Byte conversion constants
     */
    public static final class Bytes {
        public static final long BYTES_PER_KB = 1024L;
        public static final long BYTES_PER_MB = 1024L * UnitConverter.Bytes.BYTES_PER_KB;
        public static final long BYTES_PER_GB = 1024L * UnitConverter.Bytes.BYTES_PER_MB;
        public static final long BYTES_PER_TB = 1024L * UnitConverter.Bytes.BYTES_PER_GB;

        private Bytes() {}
    }

    /**
     * Convert bytes to kilobytes
     */
    public static double bytesToKB(long bytes) {
        return (double) bytes / Bytes.BYTES_PER_KB;
    }

    /**
     * Convert bytes to megabytes
     */
    public static double bytesToMB(long bytes) {
        return (double) bytes / Bytes.BYTES_PER_MB;
    }

    /**
     * Convert bytes to gigabytes
     */
    public static double bytesToGB(long bytes) {
        return (double) bytes / Bytes.BYTES_PER_GB;
    }

    /**
     * Convert bytes to terabytes
     */
    public static double bytesToTB(long bytes) {
        return (double) bytes / Bytes.BYTES_PER_TB;
    }

    /**
     * Convert kilobytes to bytes
     */
    public static long kbToBytes(double kb) {
        return (long) (kb * Bytes.BYTES_PER_KB);
    }

    /**
     * Convert megabytes to bytes
     */
    public static long mbToBytes(double mb) {
        return (long) (mb * Bytes.BYTES_PER_MB);
    }

    /**
     * Convert gigabytes to bytes
     */
    public static long gbToBytes(double gb) {
        return (long) (gb * Bytes.BYTES_PER_GB);
    }

    /**
     * Convert terabytes to bytes
     */
    public static long tbToBytes(double tb) {
        return (long) (tb * Bytes.BYTES_PER_TB);
    }

    /**
     * Format bytes to human-readable string with specified precision.
     * Examples: "5.23 GB", "1.50 TB", "512 B"
     *
     * @param bytes the byte value to format
     * @param precision decimal places for the output (e.g., 2 for "5.23 GB")
     * @return formatted string with appropriate unit (B, KB, MB, GB, TB, PB)
     */
    public static String formatBytes(long bytes, int precision) {
        if (bytes < 0) {
            return "0 B";
        }
        if (bytes < Bytes.BYTES_PER_KB) {
            return bytes + " B";
        }

        int exp = (int) (Math.log(bytes) / Math.log(1024));
        exp = Math.min(exp, 5); // Cap at PB (petabytes)
        String unit = "KMGTPE".charAt(exp - 1) + "B";

        return String.format("%." + precision + "f %s",
            bytes / Math.pow(1024, exp), unit);
    }

    /**
     * Format bytes to human-readable string with default 2 decimal places.
     * Examples: "5.23 GB", "1.50 TB", "512 B"
     *
     * @param bytes the byte value to format
     * @return formatted string with appropriate unit (B, KB, MB, GB, TB, PB)
     */
    public static String formatBytes(long bytes) {
        return formatBytes(bytes, 2);
    }

    /**
     * Time conversion constants
     */
    public static final class Time {
        public static final int SECONDS_PER_MINUTE = 60;
        public static final int MINUTES_PER_HOUR = 60;
        public static final int HOURS_PER_DAY = 24;
        public static final int SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR;
        public static final int SECONDS_PER_DAY = SECONDS_PER_HOUR * HOURS_PER_DAY;
        public static final long MILLIS_PER_SECOND = 1000L;
        public static final long MILLIS_PER_MINUTE = MILLIS_PER_SECOND * SECONDS_PER_MINUTE;
        public static final long MILLIS_PER_HOUR = MILLIS_PER_MINUTE * MINUTES_PER_HOUR;
        public static final long MILLIS_PER_DAY = MILLIS_PER_HOUR * HOURS_PER_DAY;

        private Time() {}
    }

    /**
     * Convert seconds to milliseconds
     */
    public static long secondsToMillis(long seconds) {
        return seconds * Time.MILLIS_PER_SECOND;
    }

    /**
     * Convert milliseconds to seconds
     */
    public static long millisToSeconds(long millis) {
        return millis / Time.MILLIS_PER_SECOND;
    }

    /**
     * Convert minutes to milliseconds
     */
    public static long minutesToMillis(long minutes) {
        return minutes * Time.MILLIS_PER_MINUTE;
    }

    /**
     * Convert hours to milliseconds
     */
    public static long hoursToMillis(long hours) {
        return hours * Time.MILLIS_PER_HOUR;
    }

    /**
     * Convert days to milliseconds
     */
    public static long daysToMillis(long days) {
        return days * Time.MILLIS_PER_DAY;
    }

    /**
     * Percentage conversion constants
     */
    public static final class Percentage {
        public static final double PERCENT_MULTIPLIER = 100.0;

        private Percentage() {}
    }

    /**
     * Convert a fraction to percentage (0.75 -> 75.0)
     */
    public static double toPercentage(double fraction) {
        return fraction * Percentage.PERCENT_MULTIPLIER;
    }

    /**
     * Convert a percentage to fraction (75.0 -> 0.75)
     */
    public static double fromPercentage(double percentage) {
        return percentage / Percentage.PERCENT_MULTIPLIER;
    }
}
