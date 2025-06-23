package com.coffeesprout.client;

import jakarta.ws.rs.FormParam;

public class CreateVMRequest {
    @FormParam("vmid")
    private int vmid;

    @FormParam("name")
    private String name;

    @FormParam("cores")
    private int cores;

    @FormParam("memory")
    private int memory; // in MB

    @FormParam("net0")
    private String net0; // e.g., "virtio,bridge=vmbr0"
    
    @FormParam("net1")
    private String net1;
    
    @FormParam("net2")
    private String net2;
    
    @FormParam("net3")
    private String net3;
    
    @FormParam("net4")
    private String net4;
    
    @FormParam("net5")
    private String net5;
    
    @FormParam("net6")
    private String net6;
    
    @FormParam("net7")
    private String net7;
    
    @FormParam("scsihw")
    private String scsihw = "virtio-scsi-pci"; // Default SCSI hardware
    
    @FormParam("scsi0")
    private String scsi0; // Disk configuration
    
    @FormParam("scsi1")
    private String scsi1;
    
    @FormParam("scsi2")
    private String scsi2;
    
    @FormParam("scsi3")
    private String scsi3;
    
    @FormParam("scsi4")
    private String scsi4;
    
    @FormParam("scsi5")
    private String scsi5;
    
    @FormParam("ide2")
    private String ide2; // Cloud-init drive
    
    @FormParam("boot")
    private String boot = "c"; // Boot from hard disk by default
    
    @FormParam("onboot")
    private int onboot; // Start on boot (0 or 1)
    
    @FormParam("pool")
    private String pool; // Resource pool name (optional)
    
    @FormParam("tags")
    private String tags; // Comma-separated tags
    
    @FormParam("agent")
    private String agent; // QEMU agent (0 or 1)
    
    @FormParam("cpu")
    private String cpu; // CPU type
    
    @FormParam("serial0")
    private String serial0; // Serial console
    
    @FormParam("vga")
    private String vga; // VGA hardware
    
    @FormParam("ciuser")
    private String ciuser; // Cloud-init user
    
    @FormParam("cipassword")
    private String cipassword; // Cloud-init password
    
    @FormParam("ipconfig0")
    private String ipconfig0; // Cloud-init IP config
    
    @FormParam("ipconfig1")
    private String ipconfig1;
    
    @FormParam("ipconfig2")
    private String ipconfig2;
    
    @FormParam("ipconfig3")
    private String ipconfig3;
    
    @FormParam("ipconfig4")
    private String ipconfig4;
    
    @FormParam("ipconfig5")
    private String ipconfig5;
    
    @FormParam("ipconfig6")
    private String ipconfig6;
    
    @FormParam("ipconfig7")
    private String ipconfig7;
    
    @FormParam("nameserver")
    private String nameserver; // Cloud-init DNS servers
    
    @FormParam("searchdomain")
    private String searchdomain; // Cloud-init search domain
    
    @FormParam("sshkeys")
    private String sshkeys; // Cloud-init SSH keys
    
    @FormParam("description")
    private String description; // VM description

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
    public int getCores() {
        return cores;
    }
    public void setCores(int cores) {
        this.cores = cores;
    }
    public int getMemory() {
        return memory;
    }
    public void setMemory(int memory) {
        this.memory = memory;
    }
    public String getNet0() {
        return net0;
    }
    public void setNet0(String net0) {
        this.net0 = net0;
    }
    public String getNet1() {
        return net1;
    }
    public void setNet1(String net1) {
        this.net1 = net1;
    }
    public String getNet2() {
        return net2;
    }
    public void setNet2(String net2) {
        this.net2 = net2;
    }
    public String getNet3() {
        return net3;
    }
    public void setNet3(String net3) {
        this.net3 = net3;
    }
    public String getNet4() {
        return net4;
    }
    public void setNet4(String net4) {
        this.net4 = net4;
    }
    public String getNet5() {
        return net5;
    }
    public void setNet5(String net5) {
        this.net5 = net5;
    }
    public String getNet6() {
        return net6;
    }
    public void setNet6(String net6) {
        this.net6 = net6;
    }
    public String getNet7() {
        return net7;
    }
    public void setNet7(String net7) {
        this.net7 = net7;
    }
    public String getScsihw() {
        return scsihw;
    }
    public void setScsihw(String scsihw) {
        this.scsihw = scsihw;
    }
    public String getScsi0() {
        return scsi0;
    }
    public void setScsi0(String scsi0) {
        this.scsi0 = scsi0;
    }
    public String getScsi1() {
        return scsi1;
    }
    public void setScsi1(String scsi1) {
        this.scsi1 = scsi1;
    }
    public String getScsi2() {
        return scsi2;
    }
    public void setScsi2(String scsi2) {
        this.scsi2 = scsi2;
    }
    public String getScsi3() {
        return scsi3;
    }
    public void setScsi3(String scsi3) {
        this.scsi3 = scsi3;
    }
    public String getScsi4() {
        return scsi4;
    }
    public void setScsi4(String scsi4) {
        this.scsi4 = scsi4;
    }
    public String getScsi5() {
        return scsi5;
    }
    public void setScsi5(String scsi5) {
        this.scsi5 = scsi5;
    }
    public String getIde2() {
        return ide2;
    }
    public void setIde2(String ide2) {
        this.ide2 = ide2;
    }
    public String getBoot() {
        return boot;
    }
    public void setBoot(String boot) {
        this.boot = boot;
    }
    public int getOnboot() {
        return onboot;
    }
    public void setOnboot(int onboot) {
        this.onboot = onboot;
    }
    public String getPool() {
        return pool;
    }
    public void setPool(String pool) {
        this.pool = pool;
    }
    public String getTags() {
        return tags;
    }
    public void setTags(String tags) {
        this.tags = tags;
    }
    public String getAgent() {
        return agent;
    }
    public void setAgent(String agent) {
        this.agent = agent;
    }
    public String getCpu() {
        return cpu;
    }
    public void setCpu(String cpu) {
        this.cpu = cpu;
    }
    public String getSerial0() {
        return serial0;
    }
    public void setSerial0(String serial0) {
        this.serial0 = serial0;
    }
    public String getVga() {
        return vga;
    }
    public void setVga(String vga) {
        this.vga = vga;
    }
    public String getCiuser() {
        return ciuser;
    }
    public void setCiuser(String ciuser) {
        this.ciuser = ciuser;
    }
    public String getCipassword() {
        return cipassword;
    }
    public void setCipassword(String cipassword) {
        this.cipassword = cipassword;
    }
    public String getIpconfig0() {
        return ipconfig0;
    }
    public void setIpconfig0(String ipconfig0) {
        this.ipconfig0 = ipconfig0;
    }
    public String getIpconfig1() {
        return ipconfig1;
    }
    public void setIpconfig1(String ipconfig1) {
        this.ipconfig1 = ipconfig1;
    }
    public String getIpconfig2() {
        return ipconfig2;
    }
    public void setIpconfig2(String ipconfig2) {
        this.ipconfig2 = ipconfig2;
    }
    public String getIpconfig3() {
        return ipconfig3;
    }
    public void setIpconfig3(String ipconfig3) {
        this.ipconfig3 = ipconfig3;
    }
    public String getIpconfig4() {
        return ipconfig4;
    }
    public void setIpconfig4(String ipconfig4) {
        this.ipconfig4 = ipconfig4;
    }
    public String getIpconfig5() {
        return ipconfig5;
    }
    public void setIpconfig5(String ipconfig5) {
        this.ipconfig5 = ipconfig5;
    }
    public String getIpconfig6() {
        return ipconfig6;
    }
    public void setIpconfig6(String ipconfig6) {
        this.ipconfig6 = ipconfig6;
    }
    public String getIpconfig7() {
        return ipconfig7;
    }
    public void setIpconfig7(String ipconfig7) {
        this.ipconfig7 = ipconfig7;
    }
    public String getNameserver() {
        return nameserver;
    }
    public void setNameserver(String nameserver) {
        this.nameserver = nameserver;
    }
    public String getSearchdomain() {
        return searchdomain;
    }
    public void setSearchdomain(String searchdomain) {
        this.searchdomain = searchdomain;
    }
    public String getSshkeys() {
        return sshkeys;
    }
    public void setSshkeys(String sshkeys) {
        if (sshkeys != null) {
            try {
                // SSH keys need to be double URL encoded per Proxmox forum recommendation
                // First normalize the SSH key
                String normalized = sshkeys.trim()
                    .replaceAll("\r\n", "\n")
                    .replaceAll("\r", "\n");
                
                // Apply first URL encoding - replace + with %20 to match Python's quote behavior
                String encoded = java.net.URLEncoder.encode(normalized, "UTF-8")
                        .replaceAll("\\+", "%20");
                
                // Since @FormParam will encode once more, we store the single-encoded version
                // This results in double encoding when sent
                this.sshkeys = encoded;
            } catch (Exception e) {
                throw new RuntimeException("Failed to encode SSH keys", e);
            }
        } else {
            this.sshkeys = sshkeys;
        }
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    
    @Override
    public String toString() {
        return "CreateVMRequest{" +
                "vmid=" + vmid +
                ", name='" + name + '\'' +
                ", cores=" + cores +
                ", memory=" + memory +
                ", net0='" + net0 + '\'' +
                ", scsihw='" + scsihw + '\'' +
                ", scsi0='" + scsi0 + '\'' +
                ", scsi1='" + scsi1 + '\'' +
                ", scsi2='" + scsi2 + '\'' +
                ", scsi3='" + scsi3 + '\'' +
                ", scsi4='" + scsi4 + '\'' +
                ", scsi5='" + scsi5 + '\'' +
                ", ide2='" + ide2 + '\'' +
                ", boot='" + boot + '\'' +
                ", onboot=" + onboot +
                ", pool='" + pool + '\'' +
                ", tags='" + tags + '\'' +
                ", agent='" + agent + '\'' +
                ", cpu='" + cpu + '\'' +
                ", serial0='" + serial0 + '\'' +
                ", ciuser='" + ciuser + '\'' +
                ", cipassword='" + (cipassword != null ? "***" : null) + '\'' +
                ", searchdomain='" + searchdomain + '\'' +
                ", nameserver='" + nameserver + '\'' +
                ", ipconfig0='" + ipconfig0 + '\'' +
                ", sshkeys='" + (sshkeys != null ? "[REDACTED]" : null) + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}