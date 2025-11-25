package com.example.metricsserver;// MetricsDao.java
import com.example.metricsserver.config.Conexion;
import java.sql.*;

public class MetricsDao {

    private final Conexion conexion;

    public MetricsDao() {
        this.conexion = new Conexion();
    }

    public void guardar(MetricDto dto) {
        String sql = "INSERT INTO metrics (machine_id, sample_timestamp, cpu_usage, ram_usage) VALUES (?, ?, ?, ?)";

        try (Connection conn = conexion.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) { //retornar id de la metrica generada

            ps.setString(1, dto.getMachineId());
            ps.setString(2, dto.getTimestamp());
            ps.setDouble(3, dto.getCpuUsage());
            ps.setDouble(4, dto.getRamUsage());

            ps.executeUpdate();
        }
        catch (SQLException e){
            throw new RuntimeException(e);
        }
    }
}
