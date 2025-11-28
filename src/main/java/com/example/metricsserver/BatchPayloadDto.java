package com.example.metricsserver;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class BatchPayloadDto {
    @SerializedName("agent_key")
    private String agentKey;

    @SerializedName("host")
    private Map<String, String> hostInfo;

    @SerializedName("samples")
    private List<MetricDto> samples;

    // Getters y Setters
    public String getAgentKey() { return agentKey; }
    public Map<String, String> getHostInfo() { return hostInfo; }
    public List<MetricDto> getSamples() { return samples; }
}