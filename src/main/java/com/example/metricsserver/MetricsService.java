package com.example.metricsserver;

import java.util.List;
import java.util.Map;

public class MetricsService {

    private final MetricsDao metricsDao;

    public MetricsService(MetricsDao dao) {
        this.metricsDao = dao;
    }

    // 💡 MÉTODO PRINCIPAL
    public void procesarLote(String agentKey, Map<String, String> hostInfo, List<MetricDto> samples) {

        // 1. Validaciones básicas
        if (agentKey == null || agentKey.trim().isEmpty()) {
            agentKey = "UNKNOWN-AGENT";
        }

        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("El paquete de métricas está vacío.");
        }

        // 2. LA ADUANA 🛡️ (Validación Ajustada)
        for (MetricDto muestra : samples) {
            if (!esMetricaValida(muestra)) {
                // Rechazar si faltan los datos CRÍTICOS (CPU, RAM, %)
                throw new IllegalArgumentException("RECHAZADO: El agente '" + agentKey +
                        "' envió métricas corruptas (CPU/RAM/Disco inválidos).");
            }
        }

        // 3. Guardar
        metricsDao.guardarLote(agentKey, hostInfo, samples);
    }

    // 🕵️‍♂️ Lógica Privada de Validación
    private boolean esMetricaValida(MetricDto m) {

        // --- SOLO VALIDAMOS LO QUE EL AGENTE YA ENVÍA ---

        // 1. CPU % (Obligatorio 0-100)
        if (!esValido(m.getCpuUsage(), 0, 100)) {
            Log.error("Validación fallida: CPU % inválida (" + m.getCpuUsage() + ")");
            return false;
        }

        // 2. RAM % (Obligatorio Positivo)
        if (!esValido(m.getRamUsage(), 0, Double.MAX_VALUE)) {
            Log.error("Validación fallida: RAM % inválida (" + m.getRamUsage() + ")");
            return false;
        }

        // 3. Disco % (Obligatorio 0-100)
        if (!esValido(m.getDiskUsagePercent(), 0, 100)) {
            Log.error("Validación fallida: Disco % inválido (" + m.getDiskUsagePercent() + ")");
            return false;
        }

        // --- LOS CAMPOS NUEVOS AHORA SON OPCIONALES ---
        // (Los comentamos para que no rechace la métrica si son null)

        /* if (!esValido(m.getDiskTotalGb(), 1, 999999)) return false;
        if (!esValido(m.getDiskUsedGb(), 0, Double.MAX_VALUE)) return false;
        */

        return true; // ✅ Todo aprobado
    }

    // Helper para verificar rangos y nulos
    private boolean esValido(Double valor, double min, double max) {
        return valor != null && valor >= min && valor <= max;
    }
}