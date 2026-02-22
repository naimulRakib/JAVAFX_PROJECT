package com.scholar.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
public class DatabaseConnection {

    private static DataSource dataSource;

    @Autowired
    public void setDataSource(DataSource dataSource) {
        DatabaseConnection.dataSource = dataSource;
    }

    public static synchronized Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            System.err.println("❌ DB CONNECTION FAILED: " + e.getMessage());
            return null;
        }
    }
}