package com.example.metricsserver;

// MetricsService.java
public class MetricsService {

    private final MetricsDao metricsDao;

    public MetricsService(MetricsDao metricsDao) {
        this.metricsDao = metricsDao;
    }

    public void procesarMetric(MetricDto dto) throws Exception {
        // Validaciones mínimas
        if (dto.getMachineId() == null || dto.getMachineId().isBlank()) {
            throw new IllegalArgumentException("machineId obligatorio");
        }
        if (dto.getTimestamp() == null || dto.getTimestamp().isBlank()) {
            throw new IllegalArgumentException("timestamp obligatorio");
        }

        // Puedes agregar validaciones de rango si quieres
        // if (dto.getCpuUsage() < 0 || dto.getCpuUsage() > 100) ...

        metricsDao.guardar(dto);
    }
}
