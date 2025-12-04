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
        scheduler.scheduleAtFixedRate(this::cerrarSesionesVencidas, 0, 5, TimeUnit.MINUTES);
        System.out.println("🧹 [Cleaner] Servicio de limpieza de sesiones iniciado.");
    }

    private void cerrarSesionesVencidas() {
        // ESTA CONSULTA HACE DOS COSAS A LA VEZ:
        // 1. Cierra las sesiones viejas y captura sus ID_EQ en una lista temporal llamada 'cerradas'.
        // 2. Usa esa lista 'cerradas' para poner esos equipos específicos como DISPONIBLE.
        String sql = """
        WITH sesiones_cerradas AS (
            UPDATE SESION 
            SET ESTADO_SESION = 'CERRADA' 
            WHERE ESTADO_SESION = 'ACTIVA' 
              AND FECHA_TERMINO < NOW() - INTERVAL '30 minutes'
            RETURNING ID_EQ
        )
        UPDATE EQUIPO
        SET ESTADO_EQUIPO = 'DISPONIBLE'
        WHERE ID_EQ IN (SELECT ID_EQ FROM sesiones_cerradas)
    """;

        try (Connection conn = conexion.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int equiposLiberados = ps.executeUpdate();

            if (equiposLiberados > 0) {
                System.out.println("🧹 [Cleaner] Se cerraron sesiones y se liberaron " + equiposLiberados + " equipos.");
            }

        } catch (SQLException e) {
            System.err.println("Error en SessionCleaner: " + e.getMessage());
        }
    }

    public void stop() {
        scheduler.shutdown();
    }
}