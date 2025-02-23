package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginResponse {
    private Data data;

    public Data getData() {
        return data;
    }
    public void setData(Data data) {
        this.data = data;
    }

    public static class Data {
        private String ticket;
        @JsonProperty("CSRFPreventionToken")
        private String csrfPreventionToken;

        public String getTicket() {
            return ticket;
        }
        public void setTicket(String ticket) {
            this.ticket = ticket;
        }
        public String getCsrfPreventionToken() {
            return csrfPreventionToken;
        }
        public void setCsrfPreventionToken(String csrfPreventionToken) {
            this.csrfPreventionToken = csrfPreventionToken;
        }
    }
}