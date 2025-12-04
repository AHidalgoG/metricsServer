package com.example.metricsserver; // O tu paquete util/task

import com.example.metricsserver.config.Conexion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SessionCleaner {

    private final Conexion conexion;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public SessionCleaner() {
        this.conexion = new Conexion();
    }

    public void start() {
        // Ejecutar cada 5 minutos
        scheduler.scheduleAtFixedRate(this::cerrarSesionesZombies, 0, 5, TimeUnit.MINUTES);
        System.out.println("🧹 [Cleaner] Servicio de limpieza de sesiones iniciado.");
    }

    private void cerrarSesionesZombies() {
        // Lógica: Si dice ACTIVA pero la última señal fue hace más de 35 minutos -> CERRARLA.
        // (Damos 5 mins extra de margen sobre los 30 del heartbeat normal)
        String sql = """
            UPDATE SESION 
            SET ESTADO_SESION = 'CERRADA' 
            WHERE ESTADO_SESION = 'ACTIVA' 
              AND FECHA_TERMINO < NOW() - INTERVAL '35 minutes'
        """;

        try (Connection conn = conexion.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int cerradas = ps.executeUpdate();
            if (cerradas > 0) {
                System.out.println("🧹 [Cleaner] Se cerraron automáticamente " + cerradas + " sesiones zombies.");
            }

        } catch (SQLException e) {
            System.err.println("Error en limpieza de sesiones: " + e.getMessage());
        }
    }

    public void stop() {
        scheduler.shutdown();
    }
}