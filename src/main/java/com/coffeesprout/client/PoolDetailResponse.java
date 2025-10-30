package com.coffeesprout.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PoolDetailResponse {
    private PoolData data;

    @JsonProperty("data")
    public PoolData getData() {
        return data;
    }

    public void setData(PoolData data) {
        this.data = data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PoolData {
        private String comment;
        private List<PoolMember> members;

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public List<PoolMember> getMembers() {
            return members;
        }

        public void setMembers(List<PoolMember> members) {
            this.members = members;
        }
    }
}
