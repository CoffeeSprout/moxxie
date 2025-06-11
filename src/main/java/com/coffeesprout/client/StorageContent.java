package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageContent {
    private String volid;
    private String content;
    private Long size;
    private String format;
    private String notes;
    @JsonProperty("protected")
    private Integer protectedFlag; // 0 or 1
    @JsonProperty("ctime")
    private Long ctime; // Creation time as Unix timestamp
    private Integer vmid;
    @JsonProperty("verification")
    private VerificationInfo verification;
    private String subtype; // For backups, indicates compression type

    // Nested class for verification info
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VerificationInfo {
        private String state; // ok, failed, none
        private Long time;
        
        public String getState() {
            return state;
        }
        
        public void setState(String state) {
            this.state = state;
        }
        
        public Long getTime() {
            return time;
        }
        
        public void setTime(Long time) {
            this.time = time;
        }
    }
    
    // Getters and setters
    public String getVolid() {
        return volid;
    }
    
    public void setVolid(String volid) {
        this.volid = volid;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public Long getSize() {
        return size;
    }
    
    public void setSize(Long size) {
        this.size = size;
    }
    
    public String getFormat() {
        return format;
    }
    
    public void setFormat(String format) {
        this.format = format;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    public Integer getProtectedFlag() {
        return protectedFlag;
    }
    
    public void setProtectedFlag(Integer protectedFlag) {
        this.protectedFlag = protectedFlag;
    }
    
    public Long getCtime() {
        return ctime;
    }
    
    public void setCtime(Long ctime) {
        this.ctime = ctime;
    }
    
    public Integer getVmid() {
        return vmid;
    }
    
    public void setVmid(Integer vmid) {
        this.vmid = vmid;
    }
    
    public VerificationInfo getVerification() {
        return verification;
    }
    
    public void setVerification(VerificationInfo verification) {
        this.verification = verification;
    }
    
    public String getSubtype() {
        return subtype;
    }
    
    public void setSubtype(String subtype) {
        this.subtype = subtype;
    }
    
    // Helper methods
    public boolean isProtected() {
        return protectedFlag != null && protectedFlag == 1;
    }
    
    public boolean isBackup() {
        return "backup".equals(content);
    }
    
    public String getFilename() {
        if (volid != null && volid.contains(":")) {
            String[] parts = volid.split(":", 2);
            if (parts.length > 1 && parts[1].startsWith("backup/")) {
                return parts[1].substring(7); // Remove "backup/" prefix
            }
        }
        return null;
    }
    
    public String getStorageId() {
        if (volid != null && volid.contains(":")) {
            return volid.split(":", 2)[0];
        }
        return null;
    }
}