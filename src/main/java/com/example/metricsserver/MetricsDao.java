package com.example.metricsserver;

import com.example.metricsserver.config.Conexion;
import java.sql.*;

import java.time.Instant;
import com.example.metricsserver.Log;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;

public class MetricsDao {

    private final Conexion conexion;
    // Caché para no consultar la tabla de tipos en cada petición (Clave -> ID_TIPO)
    private final Map<String, Integer> mapaTipos = new HashMap<>();
    // Caché para umbrales de alerta (ID_TIPO -> Umbral Max)
    private final Map<Integer, Double> mapaUmbrales = new HashMap<>();
    // Variable para guardar el ID de la sesión actual en memoria durante el proceso
    private Map<Integer, Long> cacheSesionesActivas = new HashMap<>();

    public Long gestionarSesionAutomatica(int idEquipo, String nombrePC, Timestamp fechaMuestra) {
        // 1. Verificar si tenemos una sesión activa válida en la BD
        String sqlBuscar = """
        SELECT ID_SESION, FECHA_TERMINO 
        FROM SESION 
        WHERE ID_EQ = ? AND ESTADO_SESION = 'ACTIVA' 
        ORDER BY ID_SESION DESC LIMIT 1
    """;

        try (Connection conn = conexion.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlBuscar)) {

            ps.setInt(1, idEquipo);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                long idSesion = rs.getLong("ID_SESION");
                Timestamp ultimaVez = rs.getTimestamp("FECHA_TERMINO");

                // Calculamos diferencia en minutos
                long diferenciaMinutos = (fechaMuestra.getTime() - ultimaVez.getTime()) / (60 * 1000);

                if (diferenciaMinutos > 30) {
                    // CASO: Corte de luz o apagado largo.
                    Log.info("🔄 NUEVA SESIÓN (Reinicio) | PC: " + nombrePC + " | Inactivo por " + diferenciaMinutos + " min");
                    // Cerramos la vieja y creamos una nueva.
                    cerrarSesion(idSesion);
                    return crearNuevaSesion(idEquipo, fechaMuestra);
                } else {
                    // CASO: Funcionamiento normal. Actualizamos la hora de fin.
                    actualizarSesion(idSesion, fechaMuestra);
                    return idSesion;
                }
            } else {
                // CASO: Primer encendido (no hay sesión activa)
                Log.info("🆕 INICIO DE SESIÓN | PC: " + nombrePC + " | Equipo encendido");
                return crearNuevaSesion(idEquipo, fechaMuestra);
            }
        } catch (SQLException e) {
            Log.error(e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private Long crearNuevaSesion(int idEquipo, Timestamp fecha) throws SQLException {
        String sqlSesion = "INSERT INTO SESION (ID_EQ, FECHA_INICIO, FECHA_TERMINO, ESTADO_SESION) VALUES (?, ?, ?, 'ACTIVA') RETURNING ID_SESION";

        //NUEVO: Actualizar estado del equipo
        String sqlEquipo = "UPDATE EQUIPO SET ESTADO_EQUIPO = 'EN USO' WHERE ID_EQ = ?";

        try (Connection conn = conexion.getConnection()) {
            conn.setAutoCommit(false); // Transacción para que ambos ocurran sí o sí

            try (PreparedStatement psSesion = conn.prepareStatement(sqlSesion);
                 PreparedStatement psEquipo = conn.prepareStatement(sqlEquipo)) {

                // 1. Crear Sesión
                psSesion.setInt(1, idEquipo);
                psSesion.setTimestamp(2, fecha);
                psSesion.setTimestamp(3, fecha);

                ResultSet rs = psSesion.executeQuery();
                long idSesion = -1;
                if (rs.next()) idSesion = rs.getLong(1);

                // 2. Marcar Equipo como En uso
                psEquipo.setInt(1, idEquipo);
                psEquipo.executeUpdate();

                conn.commit();
                return idSesion;

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void actualizarSesion(long idSesion, Timestamp fecha) {
        // Solo actualizamos el término ("Keep Alive")
        String sql = "UPDATE SESION SET FECHA_TERMINO = ? WHERE ID_SESION = ?";
        try (Connection conn = conexion.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, fecha);
            ps.setLong(2, idSesion);
            ps.executeUpdate();
        } catch (SQLException e) {
            Log.error(e.getMessage());
            e.printStackTrace();
        }
    }

    private void cerrarSesion(long idSesion) {
        String sql = "UPDATE SESION SET ESTADO_SESION = 'CERRADA' WHERE ID_SESION = ?";
        try (Connection conn = conexion.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idSesion);
            ps.executeUpdate();
        } catch (SQLException e) {
            Log.error(e.getMessage());
            e.printStackTrace();
        }
    }
    public MetricsDao() {
        this.conexion = new Conexion();
        recargarConfiguracionTipos();
    }

    // 1. Cargar configuración al iniciar (Tipos y Umbrales)
    private void recargarConfiguracionTipos() {
        String sql = "SELECT ID_TIPO, CLAVE, UMBRAL_MAX FROM TIPO_METRICAS";
        try (Connection conn = conexion.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            System.out.println("--- CARGANDO CONFIGURACIÓN ---");
            while (rs.next()) {
                int id = rs.getInt("ID_TIPO");
                String clave = rs.getString("CLAVE");
                double max = rs.getDouble("UMBRAL_MAX");

                mapaTipos.put(clave, id);
                mapaUmbrales.put(id, max);

                Log.info("Config cargada -> ID: " + id + " | Clave: " + clave + " | Umbral Max: " + max);
            }
            System.out.println("------------------------------");

        } catch (SQLException e) {
            Log.error(e.getMessage());
            e.printStackTrace();
        }
    }

    // 2. Obtener o Crear el Equipo (Devuelve el ID_EQ numérico)
    public int asegurarEquipo(String agentKey, Map<String, String> hostInfo) {
        // 1. Verificar si existe (Igual que antes)
        String sqlSelect = "SELECT ID_EQ FROM EQUIPO WHERE AGENT_KEY = ?";
        try (Connection conn = conexion.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlSelect)) {
            ps.setString(1, agentKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("ID_EQ");
            }
        } catch (SQLException e) {
            Log.error(e.getMessage());
            e.printStackTrace();
        }

        // 2. INSERTAR (Actualizado con Serial)
        // 💡 OJO: Agregué NUMERO_SERIE en la lista de columnas y un ? más
        String sqlInsert = """
            INSERT INTO EQUIPO (
                AGENT_KEY, ID_LAB, HOSTNAME, 
                SISTEMA_OPERATIVO, MAC, IP, NUMERO_SERIE, 
                ESTADO_EQUIPO, FECHA_INGRESO_EQ
            ) VALUES (?, 0, ?, ?, ?, ?, ?, 'OPERATIVO', CURRENT_DATE) 
            RETURNING ID_EQ
        """;

        try (Connection conn = conexion.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlInsert)) {

            ps.setString(1, agentKey);
            ps.setString(2, hostInfo.getOrDefault("hostname", "Unknown"));

            String soCompleto = hostInfo.getOrDefault("os_name", "Unknown") + " " + hostInfo.getOrDefault("os_version", "");
            ps.setString(3, soCompleto);

            ps.setString(4, hostInfo.getOrDefault("mac", "00-00-00-00-00-00"));
            ps.setString(5, hostInfo.getOrDefault("ip", "0.0.0.0"));

            // 💡 AQUÍ GUARDAMOS EL SERIAL
            ps.setString(6, hostInfo.getOrDefault("serial", "SN-UNKNOWN"));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Log.info("✅ NUEVO EQUIPO | " + agentKey + " | SN: " + hostInfo.get("serial"));
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            Log.error(e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    // 3. Guardar Métricas y Generar Alertas
    // 3. Guardar Métricas (Versión Optimizada: RAW + Rollup 1 Minuto)
    public void guardarLote(String agentKey, Map<String, String> hostInfo, List<MetricDto> samples) {
        int idEquipo = asegurarEquipo(agentKey, hostInfo);
        if (idEquipo == -1) return;

        // Fecha base para el lote
        Timestamp fechaBase = Timestamp.from(Instant.now());
        Long idSesion = gestionarSesionAutomatica(idEquipo, agentKey, fechaBase);

        // SQLs actualizados para las nuevas tablas
        String sqlRaw = "INSERT INTO METRICAS_RAW (ID_EQ, ID_TIPO, ID_SESION, VALOR, FECHA_REGISTRO) VALUES (?, ?, ?, ?, ?)";
        String sqlRollup = "INSERT INTO METRICAS_1MIN (ID_EQ, ID_TIPO, FECHA_REGISTRO, VALOR_PROM, VALOR_MAX, VALOR_P95) VALUES (?, ?, ?, ?, ?, ?)";
        String sqlAlerta = "INSERT INTO ALERTA (ID_EQ, ID_TIPO, VALOR_REGISTRADO, MENSAJE, FECHA_ALERTA) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = conexion.getConnection()) {
            conn.setAutoCommit(false); // Inicio Transacción

            // Listas para calcular estadísticas en memoria (CPU, RAM, Disco)
            List<Double> cpuValues = new ArrayList<>();
            List<Double> ramValues = new ArrayList<>();
            List<Double> tempValues = new ArrayList<>();
            List<Double> diskPercentValues = new ArrayList<>();
            List<Double> diskUsedGbValues = new ArrayList<>();

            try (PreparedStatement psRaw = conn.prepareStatement(sqlRaw);
                 PreparedStatement psRollup = conn.prepareStatement(sqlRollup);
                 PreparedStatement psAlerta = conn.prepareStatement(sqlAlerta)) {

                // A. Procesar cada muestra (Guardar RAW y llenar listas)
                for (MetricDto m : samples) {
                    Timestamp fechaMuestra = (m.getTimestamp() != null) ? Timestamp.from(Instant.parse(m.getTimestamp())) : fechaBase;

                    // CPU
                    if (mapaTipos.containsKey("cpu_usage")) {
                        cpuValues.add(m.getCpuUsage());
                        insertarRaw(psRaw, idEquipo, "cpu_usage", m.getCpuUsage(), fechaMuestra, idSesion);
                    }

                    //temperatura CPU
                    if (mapaTipos.containsKey("cpu_temp") && m.getTemperature() > 0) {
                        double temp = m.getTemperature();
                        tempValues.add(temp);
                        insertarRaw(psRaw, idEquipo, "cpu_temp", temp, fechaMuestra, idSesion);
                    }

                    // RAM (Convertir a MB si viene en Bytes)
                    if (mapaTipos.containsKey("ram_usage")) {
                        double ramMb = m.getRamUsage() > 1000000 ? m.getRamUsage() / (1024 * 1024) : m.getRamUsage();
                        ramValues.add(ramMb);
                        insertarRaw(psRaw, idEquipo, "ram_usage", ramMb, fechaMuestra, idSesion);
                    }
                    // DISCO - PORCENTAJE
                    if (mapaTipos.containsKey("disk_usage")) {
                        double diskPercent = m.getDiskUsagePercent();
                        diskPercentValues.add(diskPercent);
                        insertarRaw(psRaw, idEquipo, "disk_usage", diskPercent, fechaMuestra, idSesion);
                    }

                    // DISCO - GB USADOS (disk_used_gb)
                    if (mapaTipos.containsKey("disk_used_gb")) {
                        double usedGb = m.getDiskUsedGb();
                        diskUsedGbValues.add(usedGb);
                        insertarRaw(psRaw, idEquipo, "disk_used_gb", usedGb, fechaMuestra, idSesion);
                    }

                    // DISCO - GB TOTALES (disk_total_gb)
                    if (mapaTipos.containsKey("disk_total_gb")) {
                        double totalGb = m.getDiskTotalGb();
                        // No hace falta acumular para rollup si casi no cambia,
                        // pero sí queremos tener el valor RAW en la BD.
                        insertarRaw(psRaw, idEquipo, "disk_total_gb", totalGb, fechaMuestra, idSesion);
                    }
                }
                psRaw.executeBatch(); // Enviamos todos los datos crudos a la BD

                // B. Calcular y Guardar ROLLUP DE 1 MINUTO (En memoria)
                // Esto reemplaza al 'procesarDatoIndividual' antiguo para las alertas y resumenes
                generarRollup(psRollup, psAlerta, idEquipo, agentKey, "cpu_usage", cpuValues, fechaBase);
                generarRollup(psRollup, psAlerta, idEquipo, agentKey, "ram_usage", ramValues, fechaBase);
                generarRollup(psRollup, psAlerta, idEquipo, agentKey, "disk_usage", diskPercentValues, fechaBase);
                if (!diskUsedGbValues.isEmpty() && mapaTipos.containsKey("disk_used_gb")) {
                    generarRollup(psRollup, psAlerta, idEquipo, agentKey, "disk_used_gb", diskUsedGbValues, fechaBase);
                }

                if (!tempValues.isEmpty()) {
                    generarRollup(psRollup, psAlerta, idEquipo, agentKey, "cpu_temp", tempValues, fechaBase);
                }

                psRollup.executeBatch(); // Enviamos los promedios
                psAlerta.executeBatch(); // Enviamos las alertas (si hubo)

                conn.commit(); // Confirmamos todo el bloque

            } catch (Exception e) {
                conn.rollback();
                Log.error(e.getMessage());
                e.printStackTrace();
            }
        } catch (SQLException e) {
            Log.error(e.getMessage());
            e.printStackTrace();
        }
    }

    // Método auxiliar con logs
    private void procesarDatoIndividual(PreparedStatement psM, PreparedStatement psA,
                                        int idEq, String nombrePC, String clave, double valor, Timestamp fecha, Long idSesion) throws SQLException {
        Integer idTipo = mapaTipos.get(clave);

        if (idTipo == null) {
            Log.error("ERROR: No se encontró ID para la clave: " + clave);
            return;
        }

        // 1. Insertar Métrica
        psM.setInt(1, idEq);
        psM.setInt(2, idTipo);
        if (idSesion != null) psM.setLong(3, idSesion); else psM.setNull(3, Types.BIGINT);
        psM.setDouble(4, valor);
        psM.setTimestamp(5, fecha);
        psM.addBatch();

        // 2. Verificar Alerta
        Double umbral = mapaUmbrales.get(idTipo);


        if (umbral != null && valor > umbral) {
            Log.info("⚠️ ALERTA SQL | " + clave + " | PC: " + nombrePC + " | Valor: " + valor);

            psA.setInt(1, idEq);
            psA.setInt(2, idTipo);
            psA.setDouble(3, valor);
            psA.setString(4, "Critico: " + valor + " > " + umbral);
            psA.setTimestamp(5, fecha);
            psA.addBatch();
        }
    }

    // Método simple para guardar el dato crudo sin evaluar nada
    private void insertarRaw(PreparedStatement ps, int idEq, String clave, double valor, Timestamp fecha, Long idSesion) throws SQLException {
        Integer idTipo = mapaTipos.get(clave);
        if (idTipo == null) return;

        ps.setInt(1, idEq);
        ps.setInt(2, idTipo);
        if (idSesion != null) ps.setLong(3, idSesion); else ps.setNull(3, Types.BIGINT);
        ps.setDouble(4, valor);
        ps.setTimestamp(5, fecha);
        ps.addBatch();
    }

    // Método INTELIGENTE: Calcula Promedio, Max, P95 y evalúa Alertas
    private void generarRollup(PreparedStatement ps, PreparedStatement psAlerta, int idEq, String nombrePC, String clave, List<Double> valores, Timestamp fecha) throws SQLException {
        if (valores.isEmpty()) return;

        // 1. Cálculos Matemáticos
        double max = Collections.max(valores);
        double avg = valores.stream().mapToDouble(d -> d).average().orElse(0.0);

        // Percentil 95 (Ordenamos y tomamos el valor que está al 95% de la lista)
        // Esto sirve para ignorar picos falsos de 1 milisegundo.
        Collections.sort(valores);
        int index = (int) Math.ceil(0.95 * valores.size()) - 1;
        double p95 = valores.get(Math.max(0, index));

        // 2. Insertar en tabla METRICAS_1MIN
        Integer idTipo = mapaTipos.get(clave);
        if (idTipo == null) return;

        ps.setInt(1, idEq);
        ps.setInt(2, idTipo);
        ps.setTimestamp(3, fecha);
        ps.setDouble(4, avg);
        ps.setDouble(5, max);
        ps.setDouble(6, p95);
        ps.addBatch();

        // 3. Evaluar Alertas (Usamos P95 para evitar falsos positivos)
        Double umbral = mapaUmbrales.get(idTipo);
        if (umbral != null && p95 > umbral) {
            Log.info("⚠️ ALERTA (Rollup) | " + clave + " | PC: " + nombrePC + " | P95: " + String.format("%.2f", p95) + " > " + umbral);

            psAlerta.setInt(1, idEq);
            psAlerta.setInt(2, idTipo);
            psAlerta.setDouble(3, p95);
            psAlerta.setString(4, "Critico (P95): " + String.format("%.2f", p95) + " > " + umbral);
            psAlerta.setTimestamp(5, fecha);
            psAlerta.addBatch();
        }
    }
    // Método de Mantenimiento (Se ejecutará automáticamente cada 5 mins)
    public void ejecutarMantenimiento() {
        Log.info("🔄 MANTENIMIENTO: Verificando periodos cerrados y limpiando...");

        // A. Generar Rollup 5 MIN: Solo intervalos pasados
        String sql5Min = """
            INSERT INTO METRICAS_5MIN (ID_EQ, ID_TIPO, FECHA_REGISTRO, VALOR_PROM, VALOR_MAX, VALOR_P95)
            SELECT 
                ID_EQ, ID_TIPO, 
                date_trunc('hour', FECHA_REGISTRO) + interval '5 min' * floor(date_part('minute', FECHA_REGISTRO) / 5),
                AVG(VALOR_PROM), MAX(VALOR_MAX), MAX(VALOR_P95)
            FROM METRICAS_1MIN
            WHERE FECHA_REGISTRO < date_trunc('hour', NOW()) + interval '5 min' * floor(date_part('minute', NOW()) / 5)
              AND FECHA_REGISTRO > NOW() - INTERVAL '24 hours' 
            GROUP BY 1, 2, 3
            ON CONFLICT DO NOTHING
        """;

        // B. Generar Rollup 1 HORA: Solo horas pasadas completas
        String sql1Hora = """
            INSERT INTO METRICAS_1HORA (ID_EQ, ID_TIPO, FECHA_REGISTRO, VALOR_PROM, VALOR_MAX, VALOR_P95)
            SELECT 
                ID_EQ, ID_TIPO, 
                date_trunc('hour', FECHA_REGISTRO),
                AVG(VALOR_PROM), MAX(VALOR_MAX), MAX(VALOR_P95)
            FROM METRICAS_5MIN
            WHERE FECHA_REGISTRO < date_trunc('hour', NOW()) 
              AND FECHA_REGISTRO > NOW() - INTERVAL '48 hours'
            GROUP BY 1, 2, 3
            ON CONFLICT DO NOTHING
        """;

        // C. Limpieza (Retención de datos)
        String sqlCleanRaw = "DELETE FROM METRICAS_RAW WHERE FECHA_REGISTRO < NOW() - INTERVAL '48 hours'";
        String sqlClean1Min = "DELETE FROM METRICAS_1MIN WHERE FECHA_REGISTRO < NOW() - INTERVAL '7 days'";
        String sqlClean5Min = "DELETE FROM METRICAS_5MIN WHERE FECHA_REGISTRO < NOW() - INTERVAL '30 days'";

        try (Connection conn = conexion.getConnection();
             Statement stmt = conn.createStatement()) {

            // Ejecutamos las agregaciones
            int r5 = stmt.executeUpdate(sql5Min);
            int r1h = stmt.executeUpdate(sql1Hora);

            // Ejecutamos la limpieza
            stmt.executeUpdate(sqlCleanRaw);
            stmt.executeUpdate(sqlClean1Min);
            stmt.executeUpdate(sqlClean5Min);

            if (r5 > 0 || r1h > 0) {
                Log.info("✅ MANTENIMIENTO: Generados " + r5 + " rollups (5m) y " + r1h + " rollups (1h).");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            Log.error("❌ ERROR MANTENIMIENTO: " + e.getMessage());
        }
    }
}