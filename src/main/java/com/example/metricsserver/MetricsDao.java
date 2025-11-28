package com.example.metricsserver;

import com.example.metricsserver.config.Conexion;
import java.sql.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricsDao {

    private final Conexion conexion;
    // Caché para no consultar la tabla de tipos en cada petición (Clave -> ID_TIPO)
    private final Map<String, Integer> mapaTipos = new HashMap<>();
    // Caché para umbrales de alerta (ID_TIPO -> Umbral Max)
    private final Map<Integer, Double> mapaUmbrales = new HashMap<>();
    // Variable para guardar el ID de la sesión actual en memoria durante el proceso
    private Map<Integer, Long> cacheSesionesActivas = new HashMap<>();

    public Long gestionarSesionAutomatica(int idEquipo, Timestamp fechaMuestra) {
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
                return crearNuevaSesion(idEquipo, fechaMuestra);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Long crearNuevaSesion(int idEquipo, Timestamp fecha) throws SQLException {
        String sql = "INSERT INTO SESION (ID_EQ, FECHA_INICIO, FECHA_TERMINO, ESTADO_SESION) VALUES (?, ?, ?, 'ACTIVA') RETURNING ID_SESION";
        try (Connection conn = conexion.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idEquipo);
            ps.setTimestamp(2, fecha);
            ps.setTimestamp(3, fecha);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong(1);
        }
        return null;
    }

    private void actualizarSesion(long idSesion, Timestamp fecha) {
        // Solo actualizamos el término ("Keep Alive")
        String sql = "UPDATE SESION SET FECHA_TERMINO = ? WHERE ID_SESION = ?";
        try (Connection conn = conexion.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, fecha);
            ps.setLong(2, idSesion);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void cerrarSesion(long idSesion) {
        String sql = "UPDATE SESION SET ESTADO_SESION = 'CERRADA' WHERE ID_SESION = ?";
        try (Connection conn = conexion.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idSesion);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
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

                System.out.println("Config cargada -> ID: " + id + " | Clave: " + clave + " | Umbral Max: " + max);
            }
            System.out.println("------------------------------");

        } catch (SQLException e) {
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
        } catch (SQLException e) { e.printStackTrace(); }

        // 2. INSERTAR (Actualizado con Serial)
        // 💡 OJO: Agregué NUMERO_SERIE en la lista de columnas y un ? más
        String sqlInsert = """
            INSERT INTO EQUIPO (
                AGENT_KEY, ID_LAB, HOSTNAME, 
                SISTEMA_OPERATIVO, MAC, IP, NUMERO_SERIE, 
                ESTADO_EQUIPO, FECHA_INGRESO_EQ
            ) VALUES (?, 0, ?, ?, ?, ?, ?, 'ACTIVO', CURRENT_DATE) 
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
                    System.out.println("✅ Equipo registrado: " + agentKey + " [SN: " + hostInfo.get("serial") + "]");
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    // 3. Guardar Métricas y Generar Alertas
    public void guardarLote(String agentKey, Map<String, String> hostInfo, List<MetricDto> samples) {
        int idEquipo = asegurarEquipo(agentKey, hostInfo);
        if (idEquipo == -1) return;

        // 1. Calcular la Sesión Automática (Heartbeat)
        Timestamp fechaBase = Timestamp.from(Instant.now());
        if (!samples.isEmpty() && samples.get(0).getTimestamp() != null) {
            try { fechaBase = Timestamp.from(Instant.parse(samples.get(0).getTimestamp())); }
            catch (Exception e) {}
        }

        // Obtenemos el ID de sesión calculado por el servidor
        Long idSesionAuto = gestionarSesionAutomatica(idEquipo, fechaBase);

        // SQLs
        String sqlMetrica = "INSERT INTO METRICAS (ID_EQ, ID_TIPO, ID_SESION, VALOR, FECHA_REGISTRO) VALUES (?, ?, ?, ?, ?)";
        String sqlAlerta = "INSERT INTO ALERTA (ID_EQ, ID_TIPO, VALOR_REGISTRADO, MENSAJE, FECHA_ALERTA) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = conexion.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement psMetrica = conn.prepareStatement(sqlMetrica);
                 PreparedStatement psAlerta = conn.prepareStatement(sqlAlerta)) {

                for (MetricDto m : samples) {
                    Timestamp fecha;
                    try {
                        fecha = (m.getTimestamp() != null) ? Timestamp.from(Instant.parse(m.getTimestamp())) : Timestamp.from(Instant.now());
                    } catch (Exception e) { fecha = Timestamp.from(Instant.now()); }

                    // 🔴 ERROR ANTERIOR:
                    // Long idSesion = (m.getSessionId() != null...) ? ... : null;

                    // 🟢 CORRECCIÓN: Usamos la sesión automática que calculamos arriba
                    Long idSesion = idSesionAuto;

                    // --- CPU ---
                    if (mapaTipos.containsKey("cpu_usage")) {
                        procesarDatoIndividual(psMetrica, psAlerta, idEquipo, "cpu_usage", m.getCpuUsage(), fecha, idSesion);
                    }

                    // --- RAM ---
                    if (mapaTipos.containsKey("ram_usage")) {
                        double valorRam = m.getRamUsage();
                        if (valorRam > 1000000) valorRam = valorRam / (1024 * 1024); // Parche bytes
                        procesarDatoIndividual(psMetrica, psAlerta, idEquipo, "ram_usage", valorRam, fecha, idSesion);
                    }

                    // Buscamos la clave 'disk_usage' que insertamos en el Paso 1
                    if (mapaTipos.containsKey("disk_usage")) {
                        procesarDatoIndividual(
                                psMetrica, psAlerta,
                                idEquipo,
                                "disk_usage",   // <--- La clave SQL
                                m.getDiskUsage(), // <--- El valor del DTO
                                fecha, idSesion
                        );
                    }
                }

                psMetrica.executeBatch();
                psAlerta.executeBatch();
                conn.commit();

            } catch (Exception e) {
                conn.rollback();
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Método auxiliar con logs
    private void procesarDatoIndividual(PreparedStatement psM, PreparedStatement psA,
                                        int idEq, String clave, double valor, Timestamp fecha, Long idSesion) throws SQLException {
        Integer idTipo = mapaTipos.get(clave);

        if (idTipo == null) {
            System.err.println("ERROR: No se encontró ID para la clave: " + clave);
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
            System.out.println("GENERANDO ALERTA SQL para " + clave);

            psA.setInt(1, idEq);
            psA.setInt(2, idTipo);
            psA.setDouble(3, valor);
            psA.setString(4, "Critico: " + valor + " > " + umbral);
            psA.setTimestamp(5, fecha);
            psA.addBatch();
        }
    }
}