package com.example.metricsserver;

import java.util.List;
import java.util.Map; // <--- Importante: No olvides importar esto

public class MetricsService {

    private final MetricsDao metricsDao;

    public MetricsService(MetricsDao dao) {
        this.metricsDao = dao;
    }

    // Método antiguo: Bórralo o coméntalo si ya no tienes el método 'guardar' en el DAO
    // public void procesarMetric(MetricDto dto) {
    //    metricsDao.guardar(dto);
    // }

    // 💡 MÉTODO CORREGIDO: Ahora acepta y pasa el 'hostInfo'
    public void procesarLote(String agentKey, Map<String, String> hostInfo, List<MetricDto> samples) {

        // Validaciones básicas
        if (agentKey == null || agentKey.isEmpty()) {
            agentKey = "UNKNOWN-AGENT";
        }

        // Delegar al DAO pasando los 3 datos necesarios
        metricsDao.guardarLote(agentKey, hostInfo, samples);
    }
}