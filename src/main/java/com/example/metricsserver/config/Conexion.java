package com.example.metricsserver.config;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import io.github.cdimascio.dotenv.Dotenv;
import com.example.metricsserver.Log;

public class Conexion {
    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()                   // evita que el server muera en VPS
            .directory("./")                     // busca el .env en el mismo dir del jar
            .filename(".env")                    // archivo exacto
            .load();
    private static final String URL = dotenv.get("DB_URL");
    private static final String USER = dotenv.get("DB_USER");
    private static final String PASSWORD = dotenv.get("DB_PASSWORD");

    public Connection getConnection() throws SQLException {
        try {
            assert URL != null;
            return DriverManager.getConnection(URL, USER, PASSWORD); //en caso de dar error lanza un SQLException
        }catch (SQLException e) {
            Log.error("🔌 Fallo de conexión a PostgreSQL: " + e.getMessage());
            throw e;
        }
    }

    //este metodo hace que para guardar los cambios en la BD, debemos ingresar conn.commit(), sirve cuando se hacen varias operaciones juntas, insert, update, etc.
    public Connection getTxConnection() throws SQLException {
        Connection c = getConnection();
        c.setAutoCommit(false);
        return c;
    }
}
