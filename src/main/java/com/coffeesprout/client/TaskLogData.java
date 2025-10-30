package com.coffeesprout.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskLogData {
    private Integer n;
    private String t;
    private List<LogLine> lines;

    @RegisterForReflection
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LogLine {
        private Integer n;
        private String t;

        public Integer getN() {
            return n;
        }

        public void setN(Integer n) {
            this.n = n;
        }

        public String getT() {
            return t;
        }

        public void setT(String t) {
            this.t = t;
        }
    }

    // Getters and setters
    public Integer getN() {
        return n;
    }

    public void setN(Integer n) {
        this.n = n;
    }

    public String getT() {
        return t;
    }

    public void setT(String t) {
        this.t = t;
    }

    public List<LogLine> getLines() {
        return lines;
    }

    public void setLines(List<LogLine> lines) {
        this.lines = lines;
    }
}
