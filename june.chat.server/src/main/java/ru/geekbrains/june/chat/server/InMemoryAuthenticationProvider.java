package ru.geekbrains.june.chat.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InMemoryAuthenticationProvider implements AuthenticationProvider {
    private class UserInfo {
        private String login;
        private String password;
        private String username;

        public UserInfo(String login, String password, String username) {
            this.login = login;
            this.password = password;
            this.username = username;
        }
    }

    @Override
    public void connect() {
        System.out.println("Сервис авторизации запущен");
    }

    @Override
    public void disconnect() {
        System.out.println("Сервис авторизации отключен");
    }

    private List<UserInfo> users;

    public InMemoryAuthenticationProvider() {
        this.users = new ArrayList<>(Arrays.asList(
                new UserInfo("user@gmail.com", "1234", "user"),
                new UserInfo("bob@gmail.com", "1234", "bob")
        ));
    }

    @Override
    public String getUsernameByLoginAndPassword(String login, String password) {
        for (UserInfo u : users) {
            if (u.login.equals(login) && u.password.equals(password)) {
                return u.username;
            }
        }
        return null;
    }

    @Override
    public boolean isChangeNik(String oldNik, String newNik) {
        return false;
    }

    @Override
    public synchronized boolean isNikUsed(String nik) {
        return false;
    }
}
