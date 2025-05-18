package com.coffeesprout.client;

import jakarta.ws.rs.FormParam;

public class ImportImageRequest {
    // Example fields – adjust based on your image import requirements.
    @FormParam("filename")
    private String filename;

    @FormParam("url")
    private String url;

    @FormParam("format")
    private String format; // e.g., "qcow2" or "raw"

    // Add additional parameters if needed.

    public String getFilename() {
        return filename;
    }
    public void setFilename(String filename) {
        this.filename = filename;
    }
    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public String getFormat() {
        return format;
    }
    public void setFormat(String format) {
        this.format = format;
    }
}
