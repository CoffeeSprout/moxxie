package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request parameters for creating a backup via vzdump
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VzdumpRequest {
    private String vmid;
    private String storage;
    private String mode;
    private String compress;
    private String notes;
    private Integer protect; // 0 or 1
    private Integer remove; // Remove backups older than N days
    private String mailto;
    private String mailnotification; // always or failure

    // Builder pattern for easier construction
    public static class Builder {
        private VzdumpRequest request = new VzdumpRequest();

        public Builder vmid(int vmid) {
            request.vmid = String.valueOf(vmid);
            return this;
        }

        public Builder storage(String storage) {
            request.storage = storage;
            return this;
        }

        public Builder mode(String mode) {
            request.mode = mode;
            return this;
        }

        public Builder compress(String compress) {
            request.compress = compress;
            return this;
        }

        public Builder notes(String notes) {
            request.notes = notes;
            return this;
        }

        public Builder protect(boolean protect) {
            request.protect = protect ? 1 : 0;
            return this;
        }

        public Builder removeOlder(Integer days) {
            request.remove = days;
            return this;
        }

        public Builder mailTo(String email) {
            request.mailto = email;
            return this;
        }

        public Builder mailNotification(String notification) {
            request.mailnotification = notification;
            return this;
        }

        public VzdumpRequest build() {
            return request;
        }
    }

    // Getters and setters
    public String getVmid() {
        return vmid;
    }

    public void setVmid(String vmid) {
        this.vmid = vmid;
    }

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getCompress() {
        return compress;
    }

    public void setCompress(String compress) {
        this.compress = compress;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getProtect() {
        return protect;
    }

    public void setProtect(Integer protect) {
        this.protect = protect;
    }

    public Integer getRemove() {
        return remove;
    }

    public void setRemove(Integer remove) {
        this.remove = remove;
    }

    public String getMailto() {
        return mailto;
    }

    public void setMailto(String mailto) {
        this.mailto = mailto;
    }

    public String getMailnotification() {
        return mailnotification;
    }

    public void setMailnotification(String mailnotification) {
        this.mailnotification = mailnotification;
    }
}
