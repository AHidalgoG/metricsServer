package com.example.metricsserver;

import com.google.gson.annotations.SerializedName;

public class MetricDto {

    // timestamp (acepta varios nombres)
    @SerializedName(value = "timestamp", alternate = {"tsUtc", "time", "date"})
    private String timestamp;

    // CPU
    @SerializedName(value = "cpuUsage", alternate = {"cpu", "cpuTotal"})
    private double cpuUsage;

    // RAM
    @SerializedName(value = "ramUsage", alternate = {"ram", "memUsedBytes"})
    private double ramUsage;

    // DISCO - PORCENTAJE
    @SerializedName(value = "diskUsage", alternate = {"diskPercent"})
    private double diskUsagePercent;

    // DISCO - GB USADOS
    @SerializedName(value = "diskUsedGb", alternate = {"diskUsed", "usedGb"})
    private double diskUsedGb;

    // DISCO - GB TOTALES
    @SerializedName(value = "diskTotalGb", alternate = {"diskTotal", "totalGb"})
    private double diskTotalGb;

    // Getters
    public String getTimestamp() { return timestamp; }
    public double getCpuUsage() { return cpuUsage; }
    public double getRamUsage() { return ramUsage; }

    public double getDiskUsagePercent() { return diskUsagePercent; }
    public double getDiskUsedGb() { return diskUsedGb; }
    public double getDiskTotalGb() { return diskTotalGb; }
}
