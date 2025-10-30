package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SpiceConnectionFile {

    @JsonProperty("content")
    private String content;

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("mimeType")
    private String mimeType;

    public SpiceConnectionFile() {
        this.mimeType = "application/x-virt-viewer";
    }

    public SpiceConnectionFile(String content, String filename) {
        this.content = content;
        this.filename = filename;
        this.mimeType = "application/x-virt-viewer";
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
}
