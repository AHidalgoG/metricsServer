package com.example.metricsserver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Log {

    private static final String LOG_DIR = "logs";

    // Método público para registrar información normal
    public static void info(String mensaje) {
        escribir("INFO", mensaje);
    }

    // Método público para registrar errores
    public static void error(String mensaje) {
        escribir("ERROR", mensaje);
    }

    // Lógica interna
    private static void escribir(String nivel, String mensaje) {
        // 1. Obtener fecha y hora
        LocalDateTime ahora = LocalDateTime.now();
        String fechaHoy = ahora.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")); // Para el nombre del archivo
        String horaExacta = ahora.format(DateTimeFormatter.ofPattern("HH:mm:ss")); // Para el mensaje

        // 2. Formatear el mensaje: "[14:30:00] [INFO] Mensaje..."
        String lineaLog = String.format("[%s] [%s] %s", horaExacta, nivel, mensaje);

        // 3. Imprimir en CONSOLA (Para que lo veas en vivo)
        if (nivel.equals("ERROR")) {
            System.err.println(lineaLog);
        } else {
            System.out.println(lineaLog);
        }

        // 4. Guardar en ARCHIVO (Persistencia)
        guardarEnArchivo(fechaHoy, lineaLog);
    }

    private static void guardarEnArchivo(String fecha, String linea) {
        try {
            // Asegurar que la carpeta logs existe
            File directorio = new File(LOG_DIR);
            if (!directorio.exists()) {
                directorio.mkdirs();
            }

            // Nombre del archivo: logs/2025-12-01.log
            File archivo = new File(directorio, fecha + ".log");

            // Escribir (el 'true' significa append/agregar al final, no sobrescribir)
            try (PrintWriter out = new PrintWriter(new FileWriter(archivo, true))) {
                out.println(linea);
            }
        } catch (IOException e) {
            System.err.println("❌ No se pudo escribir en el log: " + e.getMessage());
        }
    }
}