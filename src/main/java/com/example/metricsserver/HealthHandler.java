package com.example.metricsserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;

public class HealthHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // 1. Configurar CORS (Vital para que el cliente no sea bloqueado)
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        // 2. Crear respuesta JSON simple
        String jsonResponse = "{\"status\": \"UP\", \"message\": \"Servidor Operativo\"}";

        // 3. Enviar respuesta HTTP 200 (OK)
        exchange.sendResponseHeaders(200, jsonResponse.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonResponse.getBytes());
        }
    }
}