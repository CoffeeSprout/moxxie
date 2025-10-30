package com.coffeesprout.client;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ConsoleResponse {

    @JsonProperty("type")
    private String type;

    @JsonProperty("ticket")
    private String ticket;

    @JsonProperty("port")
    private Integer port;

    @JsonProperty("password")
    private String password;

    @JsonProperty("websocketPort")
    private Integer websocketPort;

    @JsonProperty("websocketPath")
    private String websocketPath;

    @JsonProperty("validUntil")
    private Instant validUntil;

    @JsonProperty("cert")
    private String cert;

    @JsonProperty("upid")
    private String upid;

    @JsonProperty("user")
    private String user;

    public ConsoleResponse() {}

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getWebsocketPort() {
        return websocketPort;
    }

    public void setWebsocketPort(Integer websocketPort) {
        this.websocketPort = websocketPort;
    }

    public String getWebsocketPath() {
        return websocketPath;
    }

    public void setWebsocketPath(String websocketPath) {
        this.websocketPath = websocketPath;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(Instant validUntil) {
        this.validUntil = validUntil;
    }

    public String getCert() {
        return cert;
    }

    public void setCert(String cert) {
        this.cert = cert;
    }

    public String getUpid() {
        return upid;
    }

    public void setUpid(String upid) {
        this.upid = upid;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }
}
