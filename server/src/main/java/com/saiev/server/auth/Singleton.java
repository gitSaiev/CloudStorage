package com.saiev.server.auth;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Singleton {

    static final String DRIVER = "com.mysql.cj.jdbc.Driver";
    static final String DB = "jdbc:mysql://tech/cloud_db?useUnicode=true&serverTimezone=UTC&useSSL=false";
    static final String USER = "root";

    static final String PASSWORD = "";

    public static Connection connection;

    private Singleton() {

    }

    public static Connection getConnection() throws SQLException, ClassNotFoundException {
        if (connection == null) {
            connection = initConnection();
        }
        return  connection;
    }

    private static Connection initConnection () throws ClassNotFoundException, SQLException {
        Class.forName(DRIVER);
        return DriverManager.getConnection(DB, USER, PASSWORD);
    }
}
