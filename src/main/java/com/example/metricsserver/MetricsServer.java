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
    private HeartbeatService heartbeatService;
    private SessionCleaner sessionCleaner;
    public void start(int port) {
        try {
            // Inicializamos DAO y Servicio
            MetricsDao dao = new MetricsDao();
            MetricsService service = new MetricsService(dao);
            MetricsHandler handler = new MetricsHandler(service);
            TimeHandler timeHandler = new TimeHandler();

            this.heartbeatService = new HeartbeatService();
            this.sessionCleaner = new SessionCleaner();

            heartbeatService.start();
            sessionCleaner.start();

            // Crear servidor HTTP
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/metrics", handler);
            server.createContext("/time", timeHandler);
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
        if (heartbeatService != null) {
            heartbeatService.stop(); // 🛑 Matamos el hilo del latido
            System.out.println("Heartbeat detenido.");
        }

        if (sessionCleaner != null) {
            sessionCleaner.stop();
        }

        if (server != null) {
            server.stop(0);
            System.out.println("Servidor HTTP detenido.");
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