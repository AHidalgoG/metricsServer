package com.example.metricsserver;

// MetricDto.java
public class MetricDto {
    private String machineId;
    private String timestamp; // luego puedes mapear a LocalDateTime
    private double cpuUsage;
    private double ramUsage;

    public MetricDto(){}

    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public double getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }

    public double getRamUsage() { return ramUsage; }
    public void setRamUsage(double ramUsage) { this.ramUsage = ramUsage; }
}
