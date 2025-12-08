package com.example.metricsserver;

import java.util.List;
import java.util.Map;

public class MetricsService {

    private final MetricsDao metricsDao;

    public MetricsService(MetricsDao dao) {
        this.metricsDao = dao;
    }

    // 💡 MÉTODO PRINCIPAL: Recibe, Valida y Guarda
    public void procesarLote(String agentKey, Map<String, String> hostInfo, List<MetricDto> samples) {

        // 1. Validaciones de Integridad del Paquete
        if (agentKey == null || agentKey.trim().isEmpty()) {
            agentKey = "UNKNOWN-AGENT";
        }

        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("El paquete de métricas está vacío.");
        }

        // 2. LA ADUANA 🛡️ (Validación Dato por Dato)
        // Si una sola métrica del paquete está corrupta, rechazamos todo el lote.
        for (MetricDto muestra : samples) {
            if (!esMetricaValida(muestra)) {
                // Lanzamos excepción para que el Handler devuelva Error 400
                throw new IllegalArgumentException("RECHAZADO: El agente '" + agentKey +
                        "' envió métricas incompletas o valores imposibles.");
            }
        }

        // 3. Si todo es correcto, pasamos al DAO para guardar
        metricsDao.guardarLote(agentKey, hostInfo, samples);
    }

    // 🕵️‍♂️ Lógica Privada de Validación
    private boolean esMetricaValida(MetricDto m) {

        // --- MÉTRICAS BÁSICAS ---

        // 1. CPU % (Debe estar entre 0 y 100)
        if (!esValido(m.getCpuUsage(), 0, 100)) {
            Log.error("Validación fallida: CPU % inválida (" + m.getCpuUsage() + ")");
            return false;
        }

        // 2. RAM % (Debe ser positivo)
        if (!esValido(m.getRamUsage(), 0, Double.MAX_VALUE)) {
            Log.error("Validación fallida: RAM % inválida (" + m.getRamUsage() + ")");
            return false;
        }

        // 3. Disco % (Debe estar entre 0 y 100)
        if (!esValido(m.getDiskUsagePercent(), 0, 100)) {
            Log.error("Validación fallida: Disco % inválido (" + m.getDiskUsagePercent() + ")");
            return false;
        }

        // --- NUEVAS MÉTRICAS (Hardware Real) ---

        // 6. Temperatura (Opcional: A veces los sensores fallan y dan 0.0 o -999)
        // Permitimos nulos o 0, pero no negativos absurdos.
        if (m.getTemperature() != null && m.getTemperature() < -20) {
            Log.error("Validación fallida: Temperatura física imposible (" + m.getTemperature() + ")");
            return false;
        }

        return true; // ✅ Todo aprobado
    }

    // Helper para verificar rangos y nulos
    private boolean esValido(Double valor, double min, double max) {
        return valor != null && valor >= min && valor <= max;
    }
}