package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Pool {
    private String poolid;
    private String comment;
    
    @JsonProperty("poolid")
    public String getPoolid() {
        return poolid;
    }
    
    public void setPoolid(String poolid) {
        this.poolid = poolid;
    }
    
    public String getComment() {
        return comment;
    }
    
    public void setComment(String comment) {
        this.comment = comment;
    }
}