package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NodeStatus {

    private Memory memory;
    @JsonProperty("cpuinfo")
    private CpuInfo cpuInfo;

    public Memory getMemory() {
        return memory;
    }
    public void setMemory(Memory memory) {
        this.memory = memory;
    }
    public CpuInfo getCpuInfo() {
        return cpuInfo;
    }
    public void setCpuInfo(CpuInfo cpuInfo) {
        this.cpuInfo = cpuInfo;
    }

    public static class Memory {
        private long free;
        private long total;
        private long used;

        public long getFree() {
            return free;
        }
        public void setFree(long free) {
            this.free = free;
        }
        public long getTotal() {
            return total;
        }
        public void setTotal(long total) {
            this.total = total;
        }
        public long getUsed() {
            return used;
        }
        public void setUsed(long used) {
            this.used = used;
        }
    }

    public static class CpuInfo {
        private int cpus;    // Total number of CPUs available (e.g., 48)
        private int cores;   // Number of cores per socket (e.g., 24)
        private int sockets; // Number of sockets (e.g., 2)

        public int getCpus() {
            return cpus;
        }
        public void setCpus(int cpus) {
            this.cpus = cpus;
        }
        public int getCores() {
            return cores;
        }
        public void setCores(int cores) {
            this.cores = cores;
        }
        public int getSockets() {
            return sockets;
        }
        public void setSockets(int sockets) {
            this.sockets = sockets;
        }
    }
}
