package com.coffeesprout.client;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ConsoleWebSocketResponse {

    @JsonProperty("url")
    private String url;

    @JsonProperty("protocol")
    private String protocol;

    @JsonProperty("headers")
    private Map<String, String> headers;

    public ConsoleWebSocketResponse() {}

    public ConsoleWebSocketResponse(String url, String protocol, Map<String, String> headers) {
        this.url = url;
        this.protocol = protocol;
        this.headers = headers;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
}
