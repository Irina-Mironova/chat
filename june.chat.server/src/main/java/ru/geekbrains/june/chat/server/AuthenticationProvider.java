package ru.geekbrains.june.chat.server;

public interface AuthenticationProvider {
    String getUsernameByLoginAndPassword(String login, String password);
    void connect();
    void disconnect();
    boolean isChangeNik(String oldNik, String newNik);
    boolean isNikUsed(String nik);
}
