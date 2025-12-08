package com.example.metricsserver;

import com.google.gson.annotations.SerializedName;

public class MetricDto {

    // timestamp
    @SerializedName(value = "timestamp", alternate = {"tsUtc", "time", "date"})
    private String timestamp;

    // CPU
    @SerializedName(value = "cpuUsage", alternate = {"cpu", "cpuTotal"})
    private double cpuUsage;

    // Temperatura
    @SerializedName(value = "temperature", alternate = {"temp", "cpuTemp"})
    private double temperature;

    // RAM
    @SerializedName(value = "ramUsage", alternate = {"ram", "memUsedBytes"})
    private double ramUsage;

    //Tiempo de Actividad (0-100%)
    @SerializedName(value = "diskPercent", alternate = {"diskUsage", "diskActivity"})
    private double diskUsagePercent;

    // Velocidad I/O (Nuevos - En KB/s)
    @SerializedName(value = "diskReadRate", alternate = {"diskRead", "readKb"})
    private double diskReadRate;

    @SerializedName(value = "diskWriteRate", alternate = {"diskWrite", "writeKb"})
    private double diskWriteRate;

    // Getters
    public String getTimestamp() {
        return timestamp;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public Double getTemperature() {
        return temperature;
    }

    public double getRamUsage() {
        return ramUsage;
    }

    public double getDiskUsagePercent() {
        return diskUsagePercent;
    }

    public double getDiskReadRate() {
        return diskReadRate;
    }

    public double getDiskWriteRate() {
        return diskWriteRate;
    }
}