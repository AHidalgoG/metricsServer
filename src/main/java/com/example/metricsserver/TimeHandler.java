package com.example.metricsserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;

public class TimeHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Obtener el tiempo UTC del servidor
            String serverTime = Instant.now().toString();

            // Enviar la respuesta 200 OK
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, serverTime.length());

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(serverTime.getBytes());
            }

        } catch (Exception e) {
            // Manejo básico de errores del servidor
            exchange.sendResponseHeaders(500, 0);
            Log.error("Error al servir el tiempo: " + e.getMessage());
        }
    }
}