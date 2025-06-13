package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProxmoxConsoleResponse {
    
    @JsonProperty("data")
    private ProxmoxConsoleData data;
    
    public ProxmoxConsoleResponse() {}
    
    public ProxmoxConsoleData getData() {
        return data;
    }
    
    public void setData(ProxmoxConsoleData data) {
        this.data = data;
    }
    
    public static class ProxmoxConsoleData {
        @JsonProperty("ticket")
        private String ticket;
        
        @JsonProperty("port")
        private String port;
        
        @JsonProperty("cert")
        private String cert;
        
        @JsonProperty("upid")
        private String upid;
        
        @JsonProperty("user")
        private String user;
        
        @JsonProperty("password")
        private String password;
        
        public String getTicket() {
            return ticket;
        }
        
        public void setTicket(String ticket) {
            this.ticket = ticket;
        }
        
        public String getPort() {
            return port;
        }
        
        public void setPort(String port) {
            this.port = port;
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
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
    }
}