package com.example.metricsserver;

import com.google.gson.annotations.SerializedName;

public class MetricDto {

    //usará cualquiera de las 4 opciones para leer el json.
    @SerializedName(value = "timestamp", alternate = {"tsUtc", "time", "date"})
    private String timestamp;

    @SerializedName(value = "cpuUsage", alternate = {"cpu", "cpuTotal"}) // Agregué cpuTotal por si acaso
    private double cpuUsage;

    @SerializedName(value = "ramUsage", alternate = {"ram", "memUsedBytes"})
    private double ramUsage;

    @SerializedName(value = "diskUsage", alternate = {"disk", "diskUsedGb"})
    private double diskUsage;

    // Getters
    public String getTimestamp() { return timestamp; }
    public double getCpuUsage() { return cpuUsage; }
    public double getRamUsage() { return ramUsage; }
    public double getDiskUsage() { return diskUsage; }
}