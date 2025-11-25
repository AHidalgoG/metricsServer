module com.example.metricsserver {
    requires javafx.controls;
    requires javafx.fxml;
    requires jdk.httpserver;
    requires com.google.gson;
    requires java.sql;
    requires java.dotenv;


    opens com.example.metricsserver to com.google.gson;
    exports com.example.metricsserver;
}