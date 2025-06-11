package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VMStatusResponse {
    private VMStatusData data;

    public VMStatusData getData() {
        return data;
    }

    public void setData(VMStatusData data) {
        this.data = data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VMStatusData {
        private String status;
        private int vmid;
        private String name;
        private double cpu;
        private long mem;
        private long maxmem;
        private long disk;
        private long maxdisk;
        private long netin;
        private long netout;
        private long diskread;
        private long diskwrite;
        private long uptime;
        private int cpus;
        private String lock;
        private String qmpstatus;
        private String pid;
        private boolean running;
        
        @JsonProperty("ha")
        private Object haStatus;
        
        @JsonProperty("agent")
        private int agentStatus;

        // Getters and setters
        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getVmid() {
            return vmid;
        }

        public void setVmid(int vmid) {
            this.vmid = vmid;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public double getCpu() {
            return cpu;
        }

        public void setCpu(double cpu) {
            this.cpu = cpu;
        }

        public long getMem() {
            return mem;
        }

        public void setMem(long mem) {
            this.mem = mem;
        }

        public long getMaxmem() {
            return maxmem;
        }

        public void setMaxmem(long maxmem) {
            this.maxmem = maxmem;
        }

        public long getDisk() {
            return disk;
        }

        public void setDisk(long disk) {
            this.disk = disk;
        }

        public long getMaxdisk() {
            return maxdisk;
        }

        public void setMaxdisk(long maxdisk) {
            this.maxdisk = maxdisk;
        }

        public long getNetin() {
            return netin;
        }

        public void setNetin(long netin) {
            this.netin = netin;
        }

        public long getNetout() {
            return netout;
        }

        public void setNetout(long netout) {
            this.netout = netout;
        }

        public long getDiskread() {
            return diskread;
        }

        public void setDiskread(long diskread) {
            this.diskread = diskread;
        }

        public long getDiskwrite() {
            return diskwrite;
        }

        public void setDiskwrite(long diskwrite) {
            this.diskwrite = diskwrite;
        }

        public long getUptime() {
            return uptime;
        }

        public void setUptime(long uptime) {
            this.uptime = uptime;
        }

        public int getCpus() {
            return cpus;
        }

        public void setCpus(int cpus) {
            this.cpus = cpus;
        }

        public String getLock() {
            return lock;
        }

        public void setLock(String lock) {
            this.lock = lock;
        }

        public String getQmpstatus() {
            return qmpstatus;
        }

        public void setQmpstatus(String qmpstatus) {
            this.qmpstatus = qmpstatus;
        }

        public String getPid() {
            return pid;
        }

        public void setPid(String pid) {
            this.pid = pid;
        }

        public boolean isRunning() {
            return running;
        }

        public void setRunning(boolean running) {
            this.running = running;
        }

        public Object getHaStatus() {
            return haStatus;
        }

        public void setHaStatus(Object haStatus) {
            this.haStatus = haStatus;
        }

        public int getAgentStatus() {
            return agentStatus;
        }

        public void setAgentStatus(int agentStatus) {
            this.agentStatus = agentStatus;
        }
    }
}