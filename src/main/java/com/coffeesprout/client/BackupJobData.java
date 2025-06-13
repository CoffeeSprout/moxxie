package com.coffeesprout.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class BackupJobData {
    private String id;
    private String comment;
    private String schedule;
    private String dow;  // day of week
    private Integer enabled;
    private String starttime;
    private String storage;
    private String mode;
    private String compress;
    private String vmid;  // Can be comma-separated list
    private String pool;
    private String exclude;
    @JsonProperty("exclude-path")
    private String excludePath;
    private String mailto;
    private String mailnotification;
    @JsonProperty("max-files")
    private Integer maxFiles;
    private String node;  // Target node for single-node jobs
    @JsonProperty("prune-backups")
    private String pruneBackups;
    private Integer quiet;
    private Integer remove;
    private String script;
    private Integer stdexcludes;
    private Integer stop;
    private String stopwait;
    private String tmpdir;
    private String next_run;  // ISO timestamp
    
    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public String getDow() {
        return dow;
    }

    public void setDow(String dow) {
        this.dow = dow;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public String getStarttime() {
        return starttime;
    }

    public void setStarttime(String starttime) {
        this.starttime = starttime;
    }

    public String getStorage() {
        return storage;
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getCompress() {
        return compress;
    }

    public void setCompress(String compress) {
        this.compress = compress;
    }

    public String getVmid() {
        return vmid;
    }

    public void setVmid(String vmid) {
        this.vmid = vmid;
    }

    public String getPool() {
        return pool;
    }

    public void setPool(String pool) {
        this.pool = pool;
    }

    public String getExclude() {
        return exclude;
    }

    public void setExclude(String exclude) {
        this.exclude = exclude;
    }

    public String getExcludePath() {
        return excludePath;
    }

    public void setExcludePath(String excludePath) {
        this.excludePath = excludePath;
    }

    public String getMailto() {
        return mailto;
    }

    public void setMailto(String mailto) {
        this.mailto = mailto;
    }

    public String getMailnotification() {
        return mailnotification;
    }

    public void setMailnotification(String mailnotification) {
        this.mailnotification = mailnotification;
    }

    public Integer getMaxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(Integer maxFiles) {
        this.maxFiles = maxFiles;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getPruneBackups() {
        return pruneBackups;
    }

    public void setPruneBackups(String pruneBackups) {
        this.pruneBackups = pruneBackups;
    }

    public Integer getQuiet() {
        return quiet;
    }

    public void setQuiet(Integer quiet) {
        this.quiet = quiet;
    }

    public Integer getRemove() {
        return remove;
    }

    public void setRemove(Integer remove) {
        this.remove = remove;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public Integer getStdexcludes() {
        return stdexcludes;
    }

    public void setStdexcludes(Integer stdexcludes) {
        this.stdexcludes = stdexcludes;
    }

    public Integer getStop() {
        return stop;
    }

    public void setStop(Integer stop) {
        this.stop = stop;
    }

    public String getStopwait() {
        return stopwait;
    }

    public void setStopwait(String stopwait) {
        this.stopwait = stopwait;
    }

    public String getTmpdir() {
        return tmpdir;
    }

    public void setTmpdir(String tmpdir) {
        this.tmpdir = tmpdir;
    }

    public String getNext_run() {
        return next_run;
    }

    public void setNext_run(String next_run) {
        this.next_run = next_run;
    }
}