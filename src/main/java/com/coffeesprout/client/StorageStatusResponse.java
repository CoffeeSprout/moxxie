package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from Proxmox storage status endpoint
 */
public class StorageStatusResponse {
    private StorageStatus data;

    public StorageStatus getData() {
        return data;
    }

    public void setData(StorageStatus data) {
        this.data = data;
    }

    public static class StorageStatus {
        private long total;
        private long used;
        private long avail;
        @JsonProperty("used_fraction")
        private double usedFraction;
        private int active;
        private int enabled;
        private String type;
        private String content;
        private int shared;

        // Getters and setters
        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public long getUsed() {
            return used;
        }

        public void setUsed(long used) {
            this.used = used;
        }

        public long getAvail() {
            return avail;
        }

        public void setAvail(long avail) {
            this.avail = avail;
        }

        public double getUsedFraction() {
            return usedFraction;
        }

        public void setUsedFraction(double usedFraction) {
            this.usedFraction = usedFraction;
        }

        public int getActive() {
            return active;
        }

        public void setActive(int active) {
            this.active = active;
        }

        public int getEnabled() {
            return enabled;
        }

        public void setEnabled(int enabled) {
            this.enabled = enabled;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public int getShared() {
            return shared;
        }

        public void setShared(int shared) {
            this.shared = shared;
        }
    }
}
