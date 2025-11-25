package com.example.metricsserver;// MetricsHandler.java
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MetricsHandler implements HttpHandler {

    private final MetricsService metricsService;
    private final Gson gson = new Gson();

    public MetricsHandler(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        String body = sb.toString();
        System.out.println("BODY RECIBIDO: " + body);  // <-- importante

        try {
            MetricDto dto = gson.fromJson(body, MetricDto.class);

            if (dto == null) {
                // Esto pasa si el body es literalmente "null"
                String response = "JSON nulo (body = 'null')";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
                return;
            }

            metricsService.procesarMetric(dto);

            String response = "OK";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }

        } catch (JsonSyntaxException e) {
            e.printStackTrace();
            String response = "JSON inválido";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }

        } catch (Exception e) {
            e.printStackTrace();
            String response = "Error interno";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }
    }
}
