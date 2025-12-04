package com.example.metricsserver;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import com.example.metricsserver.Log;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MetricsServer {

    private HttpServer server;
    // 1. Creamos el planificador (reloj) para tareas de fondo
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public void start(int port) {
        try {
            // Inicializamos DAO y Servicio
            new SessionCleaner().start();
            MetricsDao dao = new MetricsDao();
            MetricsService service = new MetricsService(dao);
            MetricsHandler handler = new MetricsHandler(service);

            // Crear servidor HTTP
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/metrics", handler);
            server.setExecutor(null);

            server.start(); // 🚨 AQUÍ suele fallar si el puerto está ocupado

            Log.info("🚀 MetricsServer nativo escuchando en puerto " + port);

            // Programar mantenimiento
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    Log.info("⏰ Ejecutando mantenimiento programado...");
                    dao.ejecutarMantenimiento();
                } catch (Exception e) {
                    Log.error("❌ Error en tarea programada: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 1, 5, TimeUnit.MINUTES);

        } catch (IOException e) {
            Log.error("❌ No se pudo iniciar el servidor en el puerto " + port +
                    " → " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            Log.error("🔥 Error inesperado al iniciar el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        // Detener el reloj al cerrar
        scheduler.shutdown();
    }

    public static void main(String[] args) throws IOException {
        int port = 8080;
        MetricsServer metricsServer = new MetricsServer();
        metricsServer.start(port);
    }
}