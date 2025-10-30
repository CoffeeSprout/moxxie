package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ConsoleRequest {

    @JsonProperty("type")
    private ConsoleType type;

    @JsonProperty("generatePassword")
    private boolean generatePassword;

    @JsonProperty("node")
    private String node;

    public ConsoleRequest() {
        this.type = ConsoleType.VNC;
        this.generatePassword = true;
    }

    public ConsoleRequest(ConsoleType type, boolean generatePassword) {
        this.type = type;
        this.generatePassword = generatePassword;
    }

    public ConsoleType getType() {
        return type;
    }

    public void setType(ConsoleType type) {
        this.type = type;
    }

    public boolean isGeneratePassword() {
        return generatePassword;
    }

    public void setGeneratePassword(boolean generatePassword) {
        this.generatePassword = generatePassword;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }
}
