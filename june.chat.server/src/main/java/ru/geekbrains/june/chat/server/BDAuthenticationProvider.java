package ru.geekbrains.june.chat.server;

import java.sql.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BDAuthenticationProvider implements AuthenticationProvider {
    private Connection connection;
    private PreparedStatement ps;
    private ResultSet rs;
    private static final Logger LOGGER = LogManager.getLogger(BDAuthenticationProvider.class);


    public BDAuthenticationProvider() {
    }

    public synchronized boolean isChangeNik(String nik, String newNik) {
        try {
            ps = connection.prepareStatement("Update users set nik=? where nik=?;");
            ps.setString(1, newNik);
            ps.setString(2, nik);
            ps.executeUpdate();
            return true;
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
        }
    }


    @Override
    public String getUsernameByLoginAndPassword(String login, String password) {
        try {
            ps = connection.prepareStatement("Select * from users where login=? and password=?;");
            ps.setString(1, login);
            ps.setString(2,password);
            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString(4);
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return null;
        }
        return null;
    }
    public void connect() {
        try {
            if (connection != null && !connection.isClosed()) {
                return;
            }
            connection = DriverManager.getConnection("jdbc:sqlite:userdb.db");
            System.out.println("Установлено соединение с БД");
            LOGGER.info("Установлено соединение с БД");
        } catch (SQLException e) {
            e.printStackTrace();
            LOGGER.error("Ошибка соединения с БД");
        }
    }

    public void disconnect() {
        System.out.println("Соединение с БД разорвано");
        LOGGER.info("Соединение с БД разорвано");
        try {
            if (rs != null) {
                rs.close();
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        try {
            if (ps != null) {
                ps.close();
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    public synchronized boolean isNikUsed(String nik) {
        //проверка: есть ли в БД пользователь с указанным nik
        try {
            ps = connection.prepareStatement("Select * from users where nik=?;");
            ps.setString(1, nik);
            rs = ps.executeQuery();
            if (rs.next()) {
                return true;
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return false;
        }
        return false;
    }
}
