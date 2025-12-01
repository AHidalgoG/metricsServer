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

    public void start(int port) throws IOException {
        // Inicializamos DAO y Servicio
        MetricsDao dao = new MetricsDao();
        MetricsService service = new MetricsService(dao); // Asegúrate que tu Service acepte el DAO
        MetricsHandler handler = new MetricsHandler(service); // Asegúrate que tu Handler use Gson y HttpHandler

        // Configuración del Servidor HTTP Nativo
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/metrics", handler); // Ojo: Ajusté la ruta a /api/metrics/batch para coincidir con el agente
        server.setExecutor(null);
        server.start();

        Log.info("🚀 MetricsServer nativo escuchando en puerto " + port);

        // 2. Programar el Mantenimiento Automático (Cada 5 minutos)
        scheduler.scheduleAtFixedRate(() -> {
            try {
                Log.info("⏰ Ejecutando mantenimiento programado...");
                dao.ejecutarMantenimiento(); // Llamamos a la lógica SQL que creamos antes
            } catch (Exception e) {
                Log.error("❌ Error en tarea programada: " + e.getMessage());
                e.printStackTrace();
            }
        }, 1, 5, TimeUnit.MINUTES); // Inicia en 1 min, repite cada 5 min
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