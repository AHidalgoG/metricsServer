package com.example.metricsserver;

import com.example.metricsserver.config.Conexion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartbeatService {

    private final Conexion conexion;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public HeartbeatService() {
        this.conexion = new Conexion();
    }

    public void start() {
        // Ejecutar cada 10 segundos
        scheduler.scheduleAtFixedRate(this::actualizarLatido, 0, 10, TimeUnit.SECONDS);
        Log.info("💓 [Heartbeat] Servicio de latido iniciado.");
    }

    private void actualizarLatido() {
        String sql = "UPDATE SERVER_STATUS SET LAST_SEEN = CURRENT_TIMESTAMP WHERE ID = 1";

        try (Connection conn = conexion.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.executeUpdate();

        } catch (SQLException e) {
            Log.info("Error enviando latido: " + e.getMessage());
        }
    }

    public void stop() {
        scheduler.shutdown();
    }
}