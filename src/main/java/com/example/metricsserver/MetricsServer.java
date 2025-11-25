package com.example.metricsserver;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;


public class MetricsServer {

    private HttpServer server;

    public void start(int port) throws IOException {
        MetricsDao dao = new MetricsDao();
        MetricsService service = new MetricsService(dao);
        MetricsHandler handler = new MetricsHandler(service);

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/metrics", handler);
        server.setExecutor(null); // default executor
        server.start();

        System.out.println("MetricsServer escuchando en puerto " + port + " en /metrics");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public static void main(String[] args) throws IOException {
        int port = 8080; // ajusta si quieres otro puerto
        MetricsServer metricsServer = new MetricsServer();
        metricsServer.start(port);
    }
}
